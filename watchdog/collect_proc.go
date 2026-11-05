package main

import (
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

func max0(v int64) int64 {
	if v < 0 {
		return 0
	}
	return v
}

func (c *Collector) collectProcesses(elapsed float64) []ProcInfo {
	base := c.hostPath("proc")
	entries, err := os.ReadDir(base)
	if err != nil {
		return nil
	}

	var total float64
	if b, err := os.ReadFile(c.hostPath("proc/stat")); err == nil {
		for _, line := range strings.Split(string(b), "\n") {
			if strings.HasPrefix(line, "cpu ") {
				_, t := parseCPULine(line)
				total = t.total()
				break
			}
		}
	}

	c.mu.Lock()
	prevTotal := c.prevProcTotal
	c.prevProcTotal = total
	prevProcs := c.prevProc
	c.mu.Unlock()

	dTotal := total - prevTotal
	pageSize := uint64(os.Getpagesize())
	memTotal := parseMeminfo(c.hostPath("proc/meminfo"))["MemTotal"]

	var procs []ProcInfo
	nextProcs := map[int]uint64{}

	for _, e := range entries {
		pid, err := strconv.Atoi(e.Name())
		if err != nil {
			continue
		}
		dir := filepath.Join(base, e.Name())
		statBytes, err := os.ReadFile(filepath.Join(dir, "stat"))
		if err != nil {
			continue
		}
		line := string(statBytes)
		open := strings.Index(line, "(")
		closeIdx := strings.LastIndex(line, ")")
		if open < 0 || closeIdx <= open {
			continue
		}
		name := line[open+1 : closeIdx]
		rest := strings.Fields(line[closeIdx+1:])
		if len(rest) < 22 {
			continue
		}
		utime, _ := strconv.ParseUint(rest[11], 10, 64)
		stime, _ := strconv.ParseUint(rest[12], 10, 64)
		threads, _ := strconv.Atoi(rest[17])
		rssPages, _ := strconv.ParseInt(rest[21], 10, 64)
		state := rest[0]
		ticks := utime + stime
		nextProcs[pid] = ticks

		p := ProcInfo{
			PID: pid, Name: name, State: state, Threads: threads,
			RSS: uint64(max0(rssPages)) * pageSize,
		}
		if prev, ok := prevProcs[pid]; ok && dTotal > 0 && ticks >= prev {
			p.CPUPct = float64(ticks-prev) / dTotal * 100
		}
		if memTotal > 0 {
			p.MemPct = float64(p.RSS) / float64(memTotal) * 100
		}
		if cmd, err := os.ReadFile(filepath.Join(dir, "cmdline")); err == nil {
			s := strings.TrimSpace(strings.ReplaceAll(string(cmd), "\x00", " "))
			if len(s) > 180 {
				s = s[:180]
			}
			p.Cmdline = s
		}
		procs = append(procs, p)
	}

	c.mu.Lock()
	c.prevProc = nextProcs
	c.mu.Unlock()

	sort.Slice(procs, func(i, j int) bool {
		if procs[i].CPUPct == procs[j].CPUPct {
			return procs[i].RSS > procs[j].RSS
		}
		return procs[i].CPUPct > procs[j].CPUPct
	})
	if len(procs) > 25 {
		procs = procs[:25]
	}
	return procs
}
