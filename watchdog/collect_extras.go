package main

import (
	"bufio"
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"time"
)

func (c *Collector) collectBcache() BcacheInfo {
	b := BcacheInfo{}
	base := c.hostPath("sys/block/bcache0/bcache")
	if _, err := os.Stat(base); err != nil {
		return b
	}
	b.Present = true
	b.CacheMode = readTrim(filepath.Join(base, "cache_mode"))
	b.State = readTrim(filepath.Join(base, "state"))
	b.CacheHits = readInt64(filepath.Join(base, "stats_total/cache_hits"))
	b.CacheMisses = readInt64(filepath.Join(base, "stats_total/cache_misses"))
	b.BypassHits = readInt64(filepath.Join(base, "stats_total/bypass_hits"))
	b.BypassMisses = readInt64(filepath.Join(base, "stats_total/bypass_misses"))
	b.CacheBypassHits = readInt64(filepath.Join(base, "stats_total/cache_bypass_hits"))
	b.CacheBypassMisses = readInt64(filepath.Join(base, "stats_total/cache_bypass_misses"))

	total := b.CacheHits + b.CacheMisses
	if total > 0 {
		b.HitRatio = float64(b.CacheHits) / float64(total) * 100
	}

	if bsets, err := filepath.Glob(c.hostPath("sys/fs/bcache/*/cache_available_percent")); err == nil && len(bsets) > 0 {
		b.AvailablePercent = float64(readInt64(bsets[0]))
	}
	return b
}

func (c *Collector) collectPower(elapsed float64) PowerInfo {
	p := PowerInfo{Parts: map[string]float64{}}
	base := c.hostPath("sys/class/powercap")
	dirs, err := os.ReadDir(base)
	if err != nil {
		return p
	}

	next := map[string]uint64{}
	for _, d := range dirs {
		name := d.Name()
		if !strings.HasPrefix(name, "intel-rapl") {
			continue
		}
		dir := filepath.Join(base, name)
		label := readTrim(filepath.Join(dir, "name"))
		if label == "" {
			continue
		}
		energy := uint64(readInt64(filepath.Join(dir, "energy_uj")))
		maxRange := uint64(readInt64(filepath.Join(dir, "max_energy_range_uj")))
		if energy == 0 {
			continue
		}
		next[name] = energy

		c.mu.Lock()
		prev, ok := c.prevRAPL[name]
		c.mu.Unlock()
		if ok && energy >= prev && elapsed > 0 {
			delta := float64(energy-prev) / 1e6
			watts := delta / elapsed
			p.Parts[label] = watts
			if strings.HasPrefix(label, "package") {
				p.PackageWatts += watts
			}
			if p.EnergyUJ == 0 && label == "package-0" {
				p.EnergyUJ = energy
			}
		} else if !ok && maxRange > 0 {
			_ = maxRange
		}
	}

	c.mu.Lock()
	c.prevRAPL = next
	c.mu.Unlock()
	return p
}

func (c *Collector) collectGPU() []GPUInfo {
	var out []GPUInfo
	cardRe := regexp.MustCompile(`^card\d+$`)
	cards, _ := filepath.Glob(c.hostPath("sys/class/drm/card[0-9]*"))
	for _, card := range cards {
		name := filepath.Base(card)
		if !cardRe.MatchString(name) {
			continue
		}
		g := GPUInfo{Card: name}
		g.FreqMHz = readInt64(filepath.Join(card, "gt_cur_freq_mhz"))
		if g.FreqMHz == 0 {
			if conns, err := filepath.Glob(filepath.Join(card, "-*", "device", "gt_cur_freq_mhz")); err == nil {
				for _, p := range conns {
					if v := readInt64(p); v > 0 {
						g.FreqMHz = v
						break
					}
				}
			}
		}
		if g.FreqMHz == 0 {
			continue
		}
		g.MinMHz = readInt64(filepath.Join(card, "gt_min_freq_mhz"))
		g.MaxMHz = readInt64(filepath.Join(card, "gt_max_freq_mhz"))
		g.RP0MHz = readInt64(filepath.Join(card, "gt_RP0_freq_mhz"))
		g.RC6Ms = readInt64(filepath.Join(card, "gt", "gt0", "rc6_residency_ms"))
		if g.RC6Ms == 0 {
			g.RC6Ms = readInt64(filepath.Join(card, "power", "rc6_residency_ms"))
		}
		if engines, err := os.ReadDir(filepath.Join(card, "engine")); err == nil {
			for _, e := range engines {
				g.Engines = append(g.Engines, e.Name())
			}
		}
		out = append(out, g)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Card < out[j].Card })
	return out
}

func (c *Collector) collectSupplies() []PowerSupply {
	var out []PowerSupply
	dirs, err := os.ReadDir(c.hostPath("sys/class/power_supply"))
	if err != nil {
		return nil
	}
	for _, d := range dirs {
		base := c.hostPath(filepath.Join("sys/class/power_supply", d.Name()))
		out = append(out, PowerSupply{
			Name:   d.Name(),
			Type:   readTrim(filepath.Join(base, "type")),
			Status: readTrim(filepath.Join(base, "status")),
			Online: readTrim(filepath.Join(base, "online")),
		})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Name < out[j].Name })
	return out
}

func (c *Collector) collectKernel() KernelInfo {
	k := KernelInfo{
		Version: readTrim(c.hostPath("proc/version")),
		Cmdline: readTrim(c.hostPath("proc/cmdline")),
	}
	if f, err := os.Open(c.hostPath("proc/modules")); err == nil {
		sc := bufio.NewScanner(f)
		for sc.Scan() {
			if strings.TrimSpace(sc.Text()) != "" {
				k.ModuleCount++
			}
		}
		f.Close()
	}
	return k
}

func (c *Collector) collectSubvols() int {
	c.mu.Lock()
	cached := c.subvolCache
	at := c.subvolAt
	c.mu.Unlock()
	if time.Since(at) < 5*time.Minute {
		return cached
	}

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	out, err := exec.CommandContext(ctx, "btrfs", "subvolume", "list", c.cfg.PoolPath).Output()
	if err != nil {
		return cached
	}
	count := 0
	for _, line := range strings.Split(string(out), "\n") {
		if strings.TrimSpace(line) != "" {
			count++
		}
	}
	c.mu.Lock()
	c.subvolCache = count
	c.subvolAt = time.Now()
	c.mu.Unlock()
	return count
}
