package main

import (
	"bufio"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

type cpuTimes struct {
	user, nice, system, idle, iowait, irq, softirq, steal float64
}

func (t cpuTimes) total() float64 {
	return t.user + t.nice + t.system + t.idle + t.iowait + t.irq + t.softirq + t.steal
}

func (t cpuTimes) idleTotal() float64 { return t.idle + t.iowait }

type netCounters struct {
	rxBytes, txBytes     uint64
	rxPackets, txPackets uint64
	rxErrs, txErrs       uint64
	rxDrops, txDrops     uint64
}

type diskCounters struct {
	readSectors, writeSectors uint64
	reads, writes             uint64
	ioMs                      uint64
}

type Collector struct {
	cfg *Config

	mu       sync.Mutex
	prevCPU  cpuTimes
	prevCore map[string]cpuTimes
	prevNet  map[string]netCounters
	prevDisk map[string]diskCounters
	prevProc map[int]uint64
	prevTime time.Time

	prevProcTotal float64

	prevRAPL    map[string]uint64
	subvolAt    time.Time
	subvolCache int

	smartMu    sync.Mutex
	smartCache []SMARTInfo
	smartAt    time.Time
	smartErr   string

	logMu    sync.Mutex
	logCache []LogSummary
	logAt    time.Time

	dockerMu    sync.Mutex
	dockerCache map[string]containerStats
	dockerAt    time.Time
}

func newCollector(cfg *Config) *Collector {
	return &Collector{
		cfg:      cfg,
		prevCore: map[string]cpuTimes{},
		prevNet:  map[string]netCounters{},
		prevDisk: map[string]diskCounters{},
		prevProc: map[int]uint64{},
		prevRAPL: map[string]uint64{},
	}
}

func (c *Collector) Collect() (*Snapshot, error) {
	now := time.Now()

	c.mu.Lock()
	elapsed := now.Sub(c.prevTime).Seconds()
	c.mu.Unlock()
	if elapsed <= 0 || elapsed > 3600 {
		elapsed = float64(c.cfg.Interval.Seconds())
	}
	if elapsed <= 0 {
		elapsed = 30
	}

	s := &Snapshot{TS: now}
	s.Host = c.collectHost()
	s.CPU = c.collectCPU(elapsed)
	c.collectCPUDetails(&s.CPU)
	s.Memory = c.collectMemory()
	s.Pressure = c.collectPressure()
	s.Mounts = c.collectMounts()
	s.Btrfs = c.collectBtrfsDetails()
	s.Pool = c.collectPool(s.Mounts, s.Btrfs.DeviceStats)
	s.Temps, s.Fans = c.collectHwmon()
	s.ThermalZones = c.collectThermalZones()
	s.RAID = c.collectRAID()
	s.Disks = c.collectDisks(elapsed)
	s.SMART = c.collectSMART()
	s.Temps = append(s.Temps, smartTemperatureReadings(s.SMART, c.cfg)...)
	s.Temps = uniqueTemperatureKeys(s.Temps)
	sortTempReadings(s.Temps)
	s.Net = c.collectNet(elapsed)
	s.Containers, s.Docker = c.collectDocker()
	s.Processes = c.collectProcesses(elapsed)
	s.Logs = c.collectLogs()
	s.Power = c.collectPower(elapsed)
	s.GPU = c.collectGPU()
	s.Bcache = c.collectBcache()
	s.NetStats = c.collectNetStats()
	s.Supplies = c.collectSupplies()
	s.Kernel = c.collectKernel()
	s.Subvols = c.collectSubvols()
	s.System = c.collectSystemInfo()
	s.EDAC = c.collectEDAC()
	s.Journal = c.collectJournal()
	s.Dmesg = c.collectDmesg()
	s.Watchdog = WatchdogInfo{
		Version:        version,
		IntervalSecs:   int(c.cfg.Interval.Seconds()),
		RebootEnabled:  c.cfg.AllowReboot,
		ActionsEnabled: c.cfg.AllowActions,
		RebootDelaySec: int(c.cfg.RebootDelay.Seconds()),
	}

	c.mu.Lock()
	c.prevTime = now
	c.mu.Unlock()
	return s, nil
}

func (c *Collector) collectHost() HostInfo {
	h := HostInfo{}
	h.Hostname = readTrim(c.hostPath("etc/hostname"))
	if h.Hostname == "" {
		h.Hostname, _ = os.Hostname()
	}
	h.Kernel = readTrim(c.hostPath("proc/sys/kernel/osrelease"))
	h.BootID = readTrim(c.hostPath("proc/sys/kernel/random/boot_id"))

	if b, err := os.ReadFile(c.hostPath("etc/os-release")); err == nil {
		for _, line := range strings.Split(string(b), "\n") {
			if strings.HasPrefix(line, "PRETTY_NAME=") {
				h.OS = strings.Trim(strings.TrimPrefix(line, "PRETTY_NAME="), `"`)
			}
		}
	}

	if f := strings.Fields(readTrim(c.hostPath("proc/uptime"))); len(f) >= 1 {
		h.UptimeSec, _ = strconv.ParseFloat(f[0], 64)
	}
	if f := strings.Fields(readTrim(c.hostPath("proc/loadavg"))); len(f) >= 3 {
		h.Load1, _ = strconv.ParseFloat(f[0], 64)
		h.Load5, _ = strconv.ParseFloat(f[1], 64)
		h.Load15, _ = strconv.ParseFloat(f[2], 64)
		if len(f) >= 4 {
			parts := strings.Split(f[3], "/")
			if len(parts) == 2 {
				h.Procs, _ = strconv.Atoi(parts[0])
				h.Threads, _ = strconv.Atoi(parts[1])
			}
		}
	}

	if b, err := os.ReadFile(c.hostPath("proc/cpuinfo")); err == nil {
		for _, line := range strings.Split(string(b), "\n") {
			if strings.HasPrefix(line, "processor") {
				h.CPUCores++
			}
			if strings.HasPrefix(line, "model name") && h.CPUModel == "" {
				if i := strings.Index(line, ":"); i >= 0 {
					h.CPUModel = strings.TrimSpace(line[i+1:])
				}
			}
		}
	}
	return h
}

func parseCPULine(line string) (string, cpuTimes) {
	f := strings.Fields(line)
	if len(f) < 5 {
		return "", cpuTimes{}
	}
	vals := make([]float64, 8)
	for i := 0; i < 8 && i+1 < len(f); i++ {
		vals[i], _ = strconv.ParseFloat(f[i+1], 64)
	}
	return f[0], cpuTimes{
		user: vals[0], nice: vals[1], system: vals[2], idle: vals[3],
		iowait: vals[4], irq: vals[5], softirq: vals[6], steal: vals[7],
	}
}

func pct(delta, total float64) float64 {
	if total <= 0 {
		return 0
	}
	v := delta / total * 100
	if v < 0 {
		v = 0
	}
	if v > 100 {
		v = 100
	}
	return v
}

func (c *Collector) collectCPU(elapsed float64) CPUInfo {
	info := CPUInfo{}
	f, err := os.Open(c.hostPath("proc/stat"))
	if err != nil {
		return info
	}
	defer f.Close()

	var aggregate cpuTimes
	perCore := map[string]cpuTimes{}
	var order []string
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := sc.Text()
		if !strings.HasPrefix(line, "cpu") {
			continue
		}
		name, t := parseCPULine(line)
		if name == "cpu" {
			aggregate = t
			continue
		}
		perCore[name] = t
		order = append(order, name)
	}

	c.mu.Lock()
	prev := c.prevCPU
	prevCores := c.prevCore
	c.prevCPU = aggregate
	c.prevCore = perCore
	c.mu.Unlock()

	dTotal := aggregate.total() - prev.total()
	if dTotal > 0 {
		info.UsagePct = pct(dTotal-(aggregate.idleTotal()-prev.idleTotal()), dTotal)
		info.UserPct = pct((aggregate.user-prev.user)+(aggregate.nice-prev.nice), dTotal)
		info.SysPct = pct(aggregate.system-prev.system, dTotal)
		info.IOWaitPct = pct(aggregate.iowait-prev.iowait, dTotal)
		info.IrqPct = pct((aggregate.irq-prev.irq)+(aggregate.softirq-prev.softirq), dTotal)
		info.StealPct = pct(aggregate.steal-prev.steal, dTotal)
		info.IdlePct = pct(aggregate.idleTotal()-prev.idleTotal(), dTotal)
	}

	for _, name := range order {
		cur := perCore[name]
		p := prevCores[name]
		dt := cur.total() - p.total()
		if dt <= 0 {
			info.PerCore = append(info.PerCore, 0)
			continue
		}
		info.PerCore = append(info.PerCore, pct(dt-(cur.idleTotal()-p.idleTotal()), dt))
	}
	info.Cores = len(order)

	if b, err := os.ReadFile(c.hostPath("proc/cpuinfo")); err == nil {
		var sum float64
		var n int
		for _, line := range strings.Split(string(b), "\n") {
			if strings.HasPrefix(line, "cpu MHz") {
				if i := strings.Index(line, ":"); i >= 0 {
					if v, err := strconv.ParseFloat(strings.TrimSpace(line[i+1:]), 64); err == nil {
						sum += v
						n++
					}
				}
			}
		}
		if n > 0 {
			info.FreqMHz = sum / float64(n)
		}
	}
	return info
}

func (c *Collector) collectMemory() MemoryInfo {
	m := parseMeminfo(c.hostPath("proc/meminfo"))
	mi := MemoryInfo{
		Total:        m["MemTotal"],
		Free:         m["MemFree"],
		Available:    m["MemAvailable"],
		Buffers:      m["Buffers"],
		Cached:       m["Cached"],
		Dirty:        m["Dirty"],
		Writeback:    m["Writeback"],
		Slab:         m["Slab"],
		SReclaimable: m["SReclaimable"],
		SwapTotal:    m["SwapTotal"],
		SwapFree:     m["SwapFree"],
	}
	if mi.Available > mi.Total {
		mi.Available = mi.Total
	}
	mi.Used = mi.Total - mi.Available
	if mi.Total > 0 {
		mi.UsedPct = float64(mi.Used) / float64(mi.Total) * 100
	}
	mi.SwapUsed = mi.SwapTotal - mi.SwapFree
	if mi.SwapTotal > 0 {
		mi.SwapPct = float64(mi.SwapUsed) / float64(mi.SwapTotal) * 100
	}

	if b, err := os.ReadFile(c.hostPath("proc/vmstat")); err == nil {
		for _, line := range strings.Split(string(b), "\n") {
			f := strings.Fields(line)
			if len(f) != 2 {
				continue
			}
			v, _ := strconv.ParseInt(f[1], 10, 64)
			switch f[0] {
			case "oom_kill":
				mi.OOMKills = v
			case "pgfault":
				mi.PageFaults = v
			case "pgmajfault":
				mi.MajorFaults = v
			}
		}
	}
	return mi
}

func parsePressure(path string) PressureLine {
	var p PressureLine
	b, err := os.ReadFile(path)
	if err != nil {
		return p
	}
	for _, line := range strings.Split(string(b), "\n") {
		f := strings.Fields(line)
		if len(f) < 2 {
			continue
		}
		vals := map[string]float64{}
		for _, kv := range f[1:] {
			parts := strings.SplitN(kv, "=", 2)
			if len(parts) != 2 {
				continue
			}
			v, _ := strconv.ParseFloat(parts[1], 64)
			vals[parts[0]] = v
		}
		if f[0] == "some" {
			p.Some10, p.Some60, p.Some300 = vals["avg10"], vals["avg60"], vals["avg300"]
		} else if f[0] == "full" {
			p.Full10, p.Full60, p.Full300 = vals["avg10"], vals["avg60"], vals["avg300"]
		}
	}
	return p
}

func (c *Collector) collectPressure() PressureInfo {
	base := c.hostPath("proc/pressure")
	return PressureInfo{
		CPU: parsePressure(filepath.Join(base, "cpu")),
		Mem: parsePressure(filepath.Join(base, "memory")),
		IO:  parsePressure(filepath.Join(base, "io")),
	}
}
