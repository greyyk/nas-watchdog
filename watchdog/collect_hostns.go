package main

import (
	"bufio"
	"context"
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

func filterLines(lines []string, pattern string, max int) ([]string, error) {
	var re *regexp.Regexp
	var err error
	if pattern != "" {
		re, err = regexp.Compile(pattern)
		if err != nil {
			return nil, err
		}
	}
	var out []string
	for _, line := range lines {
		if line == "" {
			continue
		}
		if re != nil && !re.MatchString(line) {
			continue
		}
		out = append(out, line)
	}
	if max > 0 && len(out) > max {
		out = out[len(out)-max:]
	}
	return out, nil
}

func (c *Collector) tailJournal(lines int, pattern string) ([]string, error) {
	if lines <= 0 || lines > 2000 {
		lines = 300
	}
	out, err := runHostNS("journalctl", "-o", "short-iso", "-n", strconv.Itoa(lines*2), "--no-pager")
	if err != nil {
		return nil, err
	}
	return filterLines(strings.Split(string(out), "\n"), pattern, lines)
}

func (c *Collector) tailDmesg(lines int, pattern string) ([]string, error) {
	if lines <= 0 || lines > 2000 {
		lines = 300
	}
	out, err := runHostNS("dmesg", "--ctime")
	if err != nil {
		return nil, err
	}
	return filterLines(strings.Split(string(out), "\n"), pattern, lines)
}

func runHostNS(args ...string) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	full := append([]string{"-t", "1", "-m", "-u", "-i", "-n", "-p", "--"}, args...)
	return exec.CommandContext(ctx, "nsenter", full...).Output()
}

func (c *Collector) collectJournal() []JournalEntry {
	out, err := runHostNS("journalctl", "-o", "json", "-n", "250", "--no-pager")
	if err != nil {
		return nil
	}
	var entries []JournalEntry
	for _, line := range strings.Split(string(out), "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		var raw struct {
			Message  string `json:"MESSAGE"`
			Unit     string `json:"_SYSTEMD_UNIT"`
			Priority string `json:"PRIORITY"`
			Realtime string `json:"__REALTIME_TIMESTAMP"`
		}
		if json.Unmarshal([]byte(line), &raw) != nil {
			continue
		}
		entry := JournalEntry{Unit: raw.Unit, Message: raw.Message}
		entry.Priority, _ = strconv.Atoi(raw.Priority)
		if us, err := strconv.ParseInt(raw.Realtime, 10, 64); err == nil {
			entry.TS = time.Unix(0, us*1000).Format(time.RFC3339)
		}
		if len(entry.Message) > 500 {
			entry.Message = entry.Message[:500]
		}
		entries = append(entries, entry)
	}
	return entries
}

func (c *Collector) collectDmesg() []string {
	out, err := runHostNS("dmesg", "--ctime")
	if err != nil {
		return nil
	}
	lines := strings.Split(strings.TrimRight(string(out), "\n"), "\n")
	if len(lines) > 250 {
		lines = lines[len(lines)-250:]
	}
	return lines
}

func (c *Collector) collectFailedUnits() []string {
	out, err := runHostNS("systemctl", "--failed", "--no-legend", "--plain")
	if err != nil {
		return nil
	}
	var units []string
	sc := bufio.NewScanner(strings.NewReader(string(out)))
	for sc.Scan() {
		fields := strings.Fields(sc.Text())
		if len(fields) > 0 && strings.Contains(fields[0], ".") {
			units = append(units, strings.TrimPrefix(fields[0], "\u25cf"))
		}
	}
	sort.Strings(units)
	return units
}

func (c *Collector) collectTimeState() (bool, string) {
	out, err := runHostNS("timedatectl", "show", "-p", "NTPSynchronized", "-p", "Timezone")
	if err != nil {
		return false, ""
	}
	ntp := false
	tz := ""
	for _, line := range strings.Split(string(out), "\n") {
		parts := strings.SplitN(strings.TrimSpace(line), "=", 2)
		if len(parts) != 2 {
			continue
		}
		switch parts[0] {
		case "NTPSynchronized":
			ntp = parts[1] == "yes"
		case "Timezone":
			tz = parts[1]
		}
	}
	return ntp, tz
}

func (c *Collector) collectSystemInfo() SystemInfo {
	si := SystemInfo{}
	if b, err := os.ReadFile(c.hostPath("proc/stat")); err == nil {
		for _, line := range strings.Split(string(b), "\n") {
			fields := strings.Fields(line)
			if len(fields) < 2 {
				continue
			}
			v, _ := strconv.ParseUint(fields[1], 10, 64)
			switch fields[0] {
			case "ctxt":
				si.Ctxt = v
			case "processes":
				si.Processes = v
			case "procs_running":
				si.ProcsRunning = clampUintToInt(v)
			case "procs_blocked":
				si.ProcsBlocked = clampUintToInt(v)
			}
		}
	}
	si.FailedUnits = c.collectFailedUnits()
	si.NTPSynced, si.Timezone = c.collectTimeState()
	return si
}

func (c *Collector) collectEDAC() EDACInfo {
	var e EDACInfo
	counts, _ := filepath.Glob(c.hostPath("sys/devices/system/edac/mc/mc*/ce_count"))
	for _, p := range counts {
		e.Corrected += readInt64(p)
	}
	uCounts, _ := filepath.Glob(c.hostPath("sys/devices/system/edac/mc/mc*/ue_count"))
	for _, p := range uCounts {
		e.Uncorrected += readInt64(p)
	}
	return e
}

func (c *Collector) collectCPUDetails(info *CPUInfo) {
	info.Governor = readTrim(c.hostPath("sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"))
	info.MaxFreqMHz = readInt64(c.hostPath("sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")) / 1000

	cores, _ := filepath.Glob(c.hostPath("sys/devices/system/cpu/cpu[0-9]*"))
	sort.Slice(cores, func(i, j int) bool {
		a, _ := strconv.Atoi(strings.TrimPrefix(filepath.Base(cores[i]), "cpu"))
		b, _ := strconv.Atoi(strings.TrimPrefix(filepath.Base(cores[j]), "cpu"))
		return a < b
	})
	for i, dir := range cores {
		usage := 0.0
		if i < len(info.PerCore) {
			usage = info.PerCore[i]
		}
		freq := readInt64(filepath.Join(dir, "cpufreq/scaling_cur_freq")) / 1000
		info.PerCoreDetail = append(info.PerCoreDetail, CPUCore{ID: i, Usage: usage, FreqMHz: freq})
	}
	info.Threads = len(cores)

	idleDir := c.hostPath("sys/devices/system/cpu/cpu0/cpuidle")
	states, _ := filepath.Glob(filepath.Join(idleDir, "state[0-9]*"))
	for _, st := range states {
		usage, _ := strconv.ParseUint(readTrim(filepath.Join(st, "usage")), 10, 64)
		timeMs, _ := strconv.ParseUint(readTrim(filepath.Join(st, "time")), 10, 64)
		latency, _ := strconv.ParseInt(readTrim(filepath.Join(st, "latency")), 10, 64)
		info.CStates = append(info.CStates, CState{
			Name:    readTrim(filepath.Join(st, "name")),
			Count:   usage,
			TimeMs:  timeMs / 1000,
			Latency: latency,
		})
	}

	th := c.hostPath("sys/devices/system/cpu/cpu0/thermal_throttle")
	info.Throttle = ThrottleInfo{
		PackageCount:   readInt64(filepath.Join(th, "package_throttle_count")),
		PackageTotalMs: readInt64(filepath.Join(th, "package_throttle_total_time_ms")),
		CoreCount:      readInt64(filepath.Join(th, "core_throttle_count")),
		CoreTotalMs:    readInt64(filepath.Join(th, "core_throttle_total_time_ms")),
	}
}
