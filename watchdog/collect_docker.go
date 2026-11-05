package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"sort"
	"strings"
	"sync"
	"time"
)

type dockerContainer struct {
	ID      string   `json:"Id"`
	Names   []string `json:"Names"`
	Image   string   `json:"Image"`
	State   string   `json:"State"`
	Status  string   `json:"Status"`
	Created int64    `json:"Created"`
	Ports   []struct {
		IP          string `json:"IP"`
		PrivatePort int    `json:"PrivatePort"`
		PublicPort  int    `json:"PublicPort"`
		Type        string `json:"Type"`
	} `json:"Ports"`
}

type dockerStats struct {
	CPUStats struct {
		CPUUsage struct {
			TotalUsage uint64 `json:"total_usage"`
		} `json:"cpu_usage"`
		SystemCPUUsage uint64 `json:"system_cpu_usage"`
		OnlineCPUs     int    `json:"online_cpus"`
	} `json:"cpu_stats"`
	PreCPUStats struct {
		CPUUsage struct {
			TotalUsage uint64 `json:"total_usage"`
		} `json:"cpu_usage"`
		SystemCPUUsage uint64 `json:"system_cpu_usage"`
	} `json:"precpu_stats"`
	MemoryStats struct {
		Usage uint64            `json:"usage"`
		Limit uint64            `json:"limit"`
		Stats map[string]uint64 `json:"stats"`
	} `json:"memory_stats"`
	Networks map[string]struct {
		RxBytes uint64 `json:"rx_bytes"`
		TxBytes uint64 `json:"tx_bytes"`
	} `json:"networks"`
	BlkioStats struct {
		IoServiceBytesRecursive []struct {
			Op    string `json:"op"`
			Value uint64 `json:"value"`
		} `json:"io_service_bytes_recursive"`
	} `json:"blkio_stats"`
}

func dockerHTTPClient(socket string) *http.Client {
	return &http.Client{
		Timeout: 8 * time.Second,
		Transport: &http.Transport{
			DialContext: func(ctx context.Context, _, _ string) (net.Conn, error) {
				var d net.Dialer
				return d.DialContext(ctx, "unix", socket)
			},
		},
	}
}

func (c *Collector) collectDocker() ([]ContainerInfo, DockerInfo) {
	client := dockerHTTPClient(c.dockerSocket())
	info := DockerInfo{}

	if resp, err := client.Get("http://docker/info"); err == nil {
		var raw struct {
			ServerVersion     string `json:"ServerVersion"`
			APIVersion        string `json:"ApiVersion"`
			Driver            string `json:"Driver"`
			MemTotal          uint64 `json:"MemTotal"`
			NCPU              int    `json:"NCPU"`
			ContainersRunning int    `json:"ContainersRunning"`
			ContainersPaused  int    `json:"ContainersPaused"`
			ContainersStopped int    `json:"ContainersStopped"`
			Images            int    `json:"Images"`
			OperatingSystem   string `json:"OperatingSystem"`
		}
		if json.NewDecoder(resp.Body).Decode(&raw) == nil {
			info = DockerInfo{
				Version: raw.ServerVersion, APIVersion: raw.APIVersion,
				StorageDriver: raw.Driver, MemTotal: raw.MemTotal, NCPU: raw.NCPU,
				Running: raw.ContainersRunning, Paused: raw.ContainersPaused,
				Stopped: raw.ContainersStopped, Images: raw.Images,
				OperatingSystem: raw.OperatingSystem,
			}
		}
		resp.Body.Close()
	} else {
		info.Error = err.Error()
	}

	resp, err := client.Get("http://docker/containers/json?all=1")
	if err != nil {
		return nil, info
	}
	defer resp.Body.Close()
	var raw []dockerContainer
	if err := json.NewDecoder(resp.Body).Decode(&raw); err != nil {
		return nil, info
	}

	stats := c.dockerStatsCache(client, raw)

	out := make([]ContainerInfo, 0, len(raw))
	for _, r := range raw {
		name := ""
		if len(r.Names) > 0 {
			name = strings.TrimPrefix(r.Names[0], "/")
		}
		ci := ContainerInfo{
			Name: name, ID: r.ID, Image: r.Image, State: r.State, Status: r.Status,
			Created: time.Unix(r.Created, 0).UTC().Format(time.RFC3339),
		}
		switch {
		case strings.Contains(r.Status, "(healthy)"):
			ci.Health = "healthy"
		case strings.Contains(r.Status, "(unhealthy)"):
			ci.Health = "unhealthy"
		case strings.Contains(r.Status, "(health: starting)"):
			ci.Health = "starting"
		}
		for _, p := range r.Ports {
			if p.PublicPort > 0 {
				ci.Ports = append(ci.Ports, fmt.Sprintf("%d->%d/%s", p.PublicPort, p.PrivatePort, p.Type))
			} else {
				ci.Ports = append(ci.Ports, fmt.Sprintf("%d/%s", p.PrivatePort, p.Type))
			}
		}
		if st, ok := stats[name]; ok {
			ci.CPUPct = st.CPUPct
			ci.MemUsage = st.MemUsage
			ci.MemLimit = st.MemLimit
			ci.MemPct = st.MemPct
			ci.NetRx = st.NetRx
			ci.NetTx = st.NetTx
			ci.BlockRead = st.BlockRead
			ci.BlockWrite = st.BlockWrite
		}
		out = append(out, ci)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Name < out[j].Name })
	return out, info
}

func (c *Collector) dockerSocket() string {
	return "/var/run/docker.sock"
}

type containerStats struct {
	CPUPct     float64
	MemUsage   uint64
	MemLimit   uint64
	MemPct     float64
	NetRx      uint64
	NetTx      uint64
	BlockRead  uint64
	BlockWrite uint64
}

func (c *Collector) dockerStatsCache(client *http.Client, list []dockerContainer) map[string]containerStats {
	c.dockerMu.Lock()
	defer c.dockerMu.Unlock()

	if time.Since(c.dockerAt) < 45*time.Second && c.dockerCache != nil {
		return c.dockerCache
	}

	result := map[string]containerStats{}
	var mu sync.Mutex
	var wg sync.WaitGroup

	for _, cont := range list {
		if cont.State != "running" {
			continue
		}
		name := ""
		if len(cont.Names) > 0 {
			name = strings.TrimPrefix(cont.Names[0], "/")
		}
		wg.Add(1)
		go func(id, name string) {
			defer wg.Done()
			ctx, cancel := context.WithTimeout(context.Background(), 6*time.Second)
			defer cancel()
			req, err := http.NewRequestWithContext(ctx, http.MethodGet,
				fmt.Sprintf("http://docker/containers/%s/stats?stream=false&one-shot=true", id), nil)
			if err != nil {
				return
			}
			resp, err := client.Do(req)
			if err != nil {
				return
			}
			defer resp.Body.Close()
			var st dockerStats
			if json.NewDecoder(resp.Body).Decode(&st) != nil {
				return
			}

			cs := containerStats{}
			cpuDelta := float64(st.CPUStats.CPUUsage.TotalUsage - st.PreCPUStats.CPUUsage.TotalUsage)
			sysDelta := float64(st.CPUStats.SystemCPUUsage - st.PreCPUStats.SystemCPUUsage)
			if sysDelta > 0 && cpuDelta > 0 {
				cpus := st.CPUStats.OnlineCPUs
				if cpus == 0 {
					cpus = 1
				}
				cs.CPUPct = cpuDelta / sysDelta * float64(cpus) * 100
			}
			usage := st.MemoryStats.Usage
			if cache, ok := st.MemoryStats.Stats["cache"]; ok && usage > cache {
				usage -= cache
			}
			cs.MemUsage = usage
			cs.MemLimit = st.MemoryStats.Limit
			if cs.MemLimit > 0 {
				cs.MemPct = float64(cs.MemUsage) / float64(cs.MemLimit) * 100
			}
			for _, n := range st.Networks {
				cs.NetRx += n.RxBytes
				cs.NetTx += n.TxBytes
			}
			for _, b := range st.BlkioStats.IoServiceBytesRecursive {
				switch strings.ToLower(b.Op) {
				case "read":
					cs.BlockRead += b.Value
				case "write":
					cs.BlockWrite += b.Value
				}
			}
			mu.Lock()
			result[name] = cs
			mu.Unlock()
		}(cont.ID, name)
	}
	wg.Wait()

	c.dockerCache = result
	c.dockerAt = time.Now()
	return result
}
