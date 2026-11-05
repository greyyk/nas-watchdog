package main

import (
	"bufio"
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"syscall"
	"time"
)

func (c *Collector) statfsPath(hostTarget string) (total, used, free uint64, pct float64) {
	var st syscall.Statfs_t
	if err := syscall.Statfs(hostTarget, &st); err != nil {
		return 0, 0, 0, 0
	}
	bs := uint64(st.Bsize)
	total = st.Blocks * bs
	free = st.Bavail * bs
	used = (st.Blocks - st.Bfree) * bs
	if total > 0 {
		pct = float64(used) / float64(total) * 100
	}
	return
}

var pseudoFS = map[string]bool{
	"proc": true, "sysfs": true, "devtmpfs": true, "devpts": true, "tmpfs": true,
	"cgroup": true, "cgroup2": true, "pstore": true, "bpf": true, "securityfs": true,
	"debugfs": true, "tracefs": true, "configfs": true, "fusectl": true, "mqueue": true,
	"hugetlbfs": true, "autofs": true, "efivarfs": true, "ramfs": true, "binfmt_misc": true,
	"overlay": true, "squashfs": true, "nsfs": true, "rpc_pipefs": true,
}

// mountSource picks the mounts file that reflects the host namespace.
// /proc/mounts is a symlink to self/mounts, so inside a container it reports
// the container's own namespace. With pid: host, /proc/1/mounts is host init.
func (c *Collector) mountSource() string {
	hostInit := c.hostPath("proc/1/mounts")
	if b, err := os.ReadFile(hostInit); err == nil {
		if strings.Contains(string(b), " "+c.cfg.PoolHostPath+" btrfs ") {
			return hostInit
		}
	}
	return c.hostPath("proc/mounts")
}

func (c *Collector) collectMounts() []MountInfo {
	f, err := os.Open(c.mountSource())
	if err != nil {
		return nil
	}
	defer f.Close()

	var out []MountInfo
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		fields := strings.Fields(sc.Text())
		if len(fields) < 4 {
			continue
		}
		fstype := fields[2]
		if pseudoFS[fstype] {
			continue
		}
		target := unescapeMount(fields[1])
		ro := false
		for _, o := range strings.Split(fields[3], ",") {
			if o == "ro" {
				ro = true
			}
		}
		m := MountInfo{
			Device:  unescapeMount(fields[0]),
			Target:  target,
			FSType:  fstype,
			RO:      ro,
			Options: fields[3],
		}
		m.Total, m.Used, m.Free, m.UsedPct = c.statfsPath(c.hostPath(target))
		out = append(out, m)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Target < out[j].Target })
	return out
}

func (c *Collector) collectPool(mounts []MountInfo, stats map[string]int64) PoolInfo {
	p := PoolInfo{
		Path:        c.cfg.PoolPath,
		HostPath:    c.cfg.PoolHostPath,
		ExpectedRW:  true,
		DeviceStats: stats,
		Status:      "unknown",
	}
	if p.DeviceStats == nil {
		p.DeviceStats = map[string]int64{}
	}
	for _, m := range mounts {
		if m.Target == c.cfg.PoolHostPath {
			p.Device = m.Device
			p.ReadOnly = m.RO
			break
		}
	}
	p.TotalBytes, p.UsedBytes, p.FreeBytes, p.UsedPct = c.statfsPath(c.cfg.PoolPath)

	if v, ok := stats["corruption_errs"]; ok {
		p.CorruptionTotal = v
	}
	switch {
	case p.ReadOnly && p.ExpectedRW:
		p.Status = "read_only"
	case p.CorruptionTotal > 0:
		p.Status = "ok_with_corruption"
	default:
		p.Status = "ok"
	}
	return p
}

var (
	btrfsStatsRe = regexp.MustCompile(`^\[([^\]]+)\]\.([a-z_]+)\s+(\d+)`)
	btrfsDFRe    = regexp.MustCompile(`^([A-Za-z]+), ([A-Za-z]+): total=(\d+), used=(\d+)`)
)

func runBtrfs(args ...string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	out, err := exec.CommandContext(ctx, "btrfs", args...).CombinedOutput()
	if err != nil {
		return string(out), fmt.Errorf("%v: %s", err, strings.TrimSpace(string(out)))
	}
	return string(out), nil
}

func (c *Collector) collectBtrfsDetails() BtrfsInfo {
	info := BtrfsInfo{
		DeviceStats: map[string]int64{},
		DF:          map[string]ChunkUsage{},
	}

	out, err := runBtrfs("device", "stats", c.cfg.PoolPath)
	if err != nil {
		info.Error = err.Error()
		return info
	}
	for _, line := range strings.Split(out, "\n") {
		m := btrfsStatsRe.FindStringSubmatch(strings.TrimSpace(line))
		if m == nil {
			continue
		}
		n, _ := strconv.ParseInt(m[3], 10, 64)
		info.DeviceStats[m[2]] += n
	}

	if dfOut, err := runBtrfs("filesystem", "df", "-b", c.cfg.PoolPath); err == nil {
		for _, line := range strings.Split(dfOut, "\n") {
			m := btrfsDFRe.FindStringSubmatch(strings.TrimSpace(line))
			if m == nil {
				continue
			}
			total, _ := strconv.ParseUint(m[3], 10, 64)
			used, _ := strconv.ParseUint(m[4], 10, 64)
			info.DF[m[1]] = ChunkUsage{Profile: m[2], Total: total, Used: used}
		}
	}

	info.Scrub = c.collectScrub()
	return info
}

func (c *Collector) collectScrub() ScrubInfo {
	var s ScrubInfo
	out, err := runBtrfs("scrub", "status", "-R", c.cfg.PoolPath)
	if err != nil {
		return s
	}
	var lastPhysical uint64
	for _, line := range strings.Split(out, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		parts := strings.SplitN(line, ":", 2)
		if len(parts) != 2 {
			continue
		}
		key := strings.ToLower(strings.ReplaceAll(strings.TrimSpace(parts[0]), " ", "_"))
		val := strings.TrimSpace(parts[1])
		switch key {
		case "status":
			s.Status = val
		case "scrub_started":
			s.Started = val
		case "duration":
			s.DurationSec = parseDuration(val)
		case "data_bytes_scrubbed":
			s.DataBytesScrubbed, _ = strconv.ParseUint(val, 10, 64)
		case "tree_bytes_scrubbed":
			s.TreeBytesScrubbed, _ = strconv.ParseUint(val, 10, 64)
		case "read_errors":
			s.ReadErrors, _ = strconv.ParseInt(val, 10, 64)
		case "csum_errors":
			s.CsumErrors, _ = strconv.ParseInt(val, 10, 64)
		case "verify_errors":
			s.VerifyErrors, _ = strconv.ParseInt(val, 10, 64)
		case "uncorrectable_errors":
			s.UncorrectableErrors, _ = strconv.ParseInt(val, 10, 64)
		case "corrected_errors":
			s.CorrectedErrors, _ = strconv.ParseInt(val, 10, 64)
		case "last_physical":
			lastPhysical, _ = strconv.ParseUint(val, 10, 64)
		}
	}
	if total, _, _, _ := c.statfsPath(c.cfg.PoolPath); total > 0 && lastPhysical > 0 {
		p := float64(lastPhysical) / float64(total) * 100
		if p > 100 {
			p = 100
		}
		s.ProgressPct = p
	}
	if s.Status == "finished" {
		s.ProgressPct = 100
	}
	return s
}

func parseDuration(v string) int64 {
	parts := strings.Split(v, ":")
	if len(parts) != 3 {
		return 0
	}
	h, _ := strconv.ParseInt(parts[0], 10, 64)
	m, _ := strconv.ParseInt(parts[1], 10, 64)
	sec, _ := strconv.ParseInt(parts[2], 10, 64)
	return h*3600 + m*60 + sec
}

func (c *Collector) collectDisks(elapsed float64) []DiskInfo {
	f, err := os.Open(c.hostPath("proc/diskstats"))
	if err != nil {
		return nil
	}
	defer f.Close()

	prev := map[string]diskCounters{}
	c.mu.Lock()
	for k, v := range c.prevDisk {
		prev[k] = v
	}
	c.mu.Unlock()

	var out []DiskInfo
	next := map[string]diskCounters{}
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		fields := strings.Fields(sc.Text())
		if len(fields) < 14 {
			continue
		}
		name := fields[2]
		if strings.HasPrefix(name, "loop") || strings.HasPrefix(name, "ram") ||
			strings.HasPrefix(name, "zram") || strings.HasPrefix(name, "nbd") ||
			strings.HasPrefix(name, "sr") {
			continue
		}
		allowed := strings.HasPrefix(name, "sd") || strings.HasPrefix(name, "nvme") ||
			strings.HasPrefix(name, "md") || strings.HasPrefix(name, "dm-") ||
			strings.HasPrefix(name, "bcache")
		if !allowed {
			continue
		}
		sysBase := c.hostPath(filepath.Join("sys/block", name))
		if _, err := os.Stat(c.hostPath(filepath.Join("sys/class/block", name, "partition"))); err == nil {
			continue
		}
		rs, _ := strconv.ParseUint(fields[5], 10, 64)
		rd, _ := strconv.ParseUint(fields[3], 10, 64)
		ws, _ := strconv.ParseUint(fields[9], 10, 64)
		wr, _ := strconv.ParseUint(fields[7], 10, 64)
		ioMs, _ := strconv.ParseUint(fields[12], 10, 64)

		cur := diskCounters{readSectors: rs, writeSectors: ws, reads: rd, writes: wr, ioMs: ioMs}
		next[name] = cur

		d := DiskInfo{
			Name:       name,
			ReadBytes:  rs * 512,
			WriteBytes: ws * 512,
		}
		if p, ok := prev[name]; ok && elapsed > 0 {
			d.ReadRate = float64(rs-p.readSectors) * 512 / elapsed
			d.WriteRate = float64(ws-p.writeSectors) * 512 / elapsed
			d.ReadIOPS = float64(rd-p.reads) / elapsed
			d.WriteIOPS = float64(wr-p.writes) / elapsed
			d.UtilPct = float64(ioMs-p.ioMs) / (elapsed * 1000) * 100
			if d.UtilPct > 100 {
				d.UtilPct = 100
			}
			d.IOMs = float64(ioMs - p.ioMs)
		}

		d.Model = readTrim(filepath.Join(sysBase, "device/model"))
		d.Serial = readTrim(filepath.Join(sysBase, "device/serial"))
		if sz := readInt64(filepath.Join(sysBase, "size")); sz > 0 {
			d.SizeBytes = uint64(sz) * 512
		}
		d.Rotational = readTrim(filepath.Join(sysBase, "queue/rotational")) == "1"
		out = append(out, d)
	}

	c.mu.Lock()
	c.prevDisk = next
	c.mu.Unlock()

	sort.Slice(out, func(i, j int) bool { return out[i].Name < out[j].Name })
	return out
}
