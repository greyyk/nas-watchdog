package main

import (
	"fmt"
	"sort"
	"strings"
	"time"
)

var severityRank = map[string]int{"critical": 0, "warning": 1, "info": 2}

func findContainer(list []ContainerInfo, name string) (ContainerInfo, bool) {
	for _, ci := range list {
		if ci.Name == name {
			return ci, true
		}
	}
	return ContainerInfo{}, false
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n]
}

var logPatternMeta = map[string]struct {
	Title    string
	Severity string
}{
	"btrfs":    {"btrfs filesystem errors in logs", "critical"},
	"readonly": {"Read-only filesystem errors in logs", "critical"},
	"io_error": {"I/O errors in logs", "critical"},
	"memory":   {"Memory / EDAC hardware errors in logs", "critical"},
	"oom":      {"OOM kills in logs", "warning"},
	"crash":    {"Kernel faults or segfaults in logs", "warning"},
	"disk":     {"Disk errors in logs", "warning"},
	"service":  {"Service failures in logs", "warning"},
}

type temperatureAlertGroup struct {
	ID       string
	Readings []TempReading
	Hottest  TempReading
}

func temperatureAlertGroups(temps []TempReading) []temperatureAlertGroup {
	groups := map[string][]TempReading{}
	for _, reading := range temps {
		id := reading.Key
		switch reading.Category {
		case "cpu_package", "cpu_core":
			id = "cpu"
		case "nvme":
			id = strings.TrimPrefix(reading.Device, "/dev/")
			if id == "" {
				id = strings.SplitN(reading.Key, "_sensor_", 2)[0]
			}
		}
		groups[id] = append(groups[id], reading)
	}
	keys := make([]string, 0, len(groups))
	for key := range groups {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	out := make([]temperatureAlertGroup, 0, len(keys))
	for _, key := range keys {
		group := temperatureAlertGroup{ID: key, Readings: groups[key]}
		for i, reading := range group.Readings {
			if i == 0 || reading.C > group.Hottest.C {
				group.Hottest = reading
			}
		}
		out = append(out, group)
	}
	return out
}

func evaluateAlerts(cfg *Config, st *StateStore, snap *Snapshot) []Alert {
	var active []Alert
	add := func(id, sev, title, detail string) {
		active = append(active, Alert{
			ID: id, Severity: sev, Title: title, Detail: detail, Active: true,
		})
	}

	if snap.Pool.ReadOnly && snap.Pool.ExpectedRW {
		add("pool_readonly", "critical", "Pool filesystem is READ-ONLY",
			fmt.Sprintf("%s on %s is mounted read-only. Any app writing to it is failing. "+
				"btrfs forces this after a write-time metadata corruption; a clean reboot is the safe recovery. "+
				"Do NOT remount read-write without rebooting.",
				snap.Pool.HostPath, snap.Pool.Device))
	}

	lastCorruption := st.ObserveCorruption(snap.Pool.CorruptionTotal)
	if !lastCorruption.IsZero() && time.Since(lastCorruption) < 24*time.Hour {
		add("pool_corruption", "warning", "Filesystem checksum corruption detected",
			fmt.Sprintf("%d total checksum errors, last new event %s. "+
				"Corruption is caught in memory before hitting disk, which points at RAM instability "+
				"(heat or a failing DIMM). Verify cooling and backups.",
				snap.Pool.CorruptionTotal, lastCorruption.Format(time.RFC3339)))
	}

	counts := map[string]int{}
	lastFor := map[string]string{}
	for _, ls := range snap.Logs {
		for name, c := range ls.Matches {
			if c == 0 {
				continue
			}
			counts[name] += c
			if ls.LastMatch != "" {
				lastFor[name] = fmt.Sprintf("%s: %s", ls.Source, truncate(ls.LastMatch, 240))
			}
		}
	}
	seen := st.ObserveLogs(counts)
	for name, c := range counts {
		if c == 0 {
			continue
		}
		t := seen[name]
		if t.IsZero() || time.Since(t) > 2*time.Hour {
			continue
		}
		meta, ok := logPatternMeta[name]
		if !ok {
			meta.Title, meta.Severity = "Log pattern "+name, "warning"
		}
		add("log_"+name, meta.Severity, meta.Title,
			fmt.Sprintf("%d matching lines in scanned logs. Latest %s — %s",
				c, t.Format(time.RFC3339), lastFor[name]))
	}

	for _, group := range temperatureAlertGroups(snap.Temps) {
		var selected TempReading
		severity, kind, alarm := "", "", ""
		bestRank := len(severityRank)
		consider := func(reading TempReading, candidateSeverity, candidateKind, candidateAlarm string) {
			if rank := severityRank[candidateSeverity]; rank < bestRank {
				selected, severity, kind, alarm = reading, candidateSeverity, candidateKind, candidateAlarm
				bestRank = rank
			}
		}
		for _, reading := range group.Readings {
			if group.Hottest.Key == reading.Key {
				switch {
				case reading.Crit > 0 && reading.C >= reading.Crit:
					consider(reading, "critical", "critical", "")
				case reading.Warn > 0 && reading.C >= reading.Warn:
					consider(reading, "warning", "warning", "")
				}
			}
			for _, name := range reading.Alarms {
				severity := "warning"
				if name == "alarm" || strings.Contains(name, "crit") || strings.Contains(name, "emergency") {
					severity = "critical"
				}
				consider(reading, severity, "hardware", name)
			}
		}
		if severity == "" {
			continue
		}
		id := "temp_" + group.ID
		if kind == "hardware" {
			add(id, severity, fmt.Sprintf("%s hardware temperature alarm", selected.Label),
				fmt.Sprintf("%s reports a %s temperature alarm at %.1f°C. Inspect cooling and airflow.",
					selected.Label, alarm, selected.C))
			continue
		}
		if kind == "critical" {
			add(id, "critical", fmt.Sprintf("%s is %.1f°C", selected.Label, selected.C),
				fmt.Sprintf("%s reached %.1f°C (critical threshold %.0f°C). Thermal instability can corrupt data in flight.",
					selected.Label, selected.C, selected.Crit))
		} else {
			add(id, "warning", fmt.Sprintf("%s is %.1f°C", selected.Label, selected.C),
				fmt.Sprintf("%s reached %.1f°C (warning threshold %.0f°C). Improve airflow before it gets worse.",
					selected.Label, selected.C, selected.Warn))
		}
	}

	if snap.Pool.TotalBytes > 0 {
		switch {
		case snap.Pool.UsedPct >= cfg.DiskCrit:
			add("disk_usage", "critical", fmt.Sprintf("Pool is %.1f%% full", snap.Pool.UsedPct),
				fmt.Sprintf("%.1f%% of %s used.", snap.Pool.UsedPct, humanBytes(snap.Pool.TotalBytes)))
		case snap.Pool.UsedPct >= cfg.DiskWarn:
			add("disk_usage", "warning", fmt.Sprintf("Pool is %.1f%% full", snap.Pool.UsedPct),
				fmt.Sprintf("%.1f%% of %s used.", snap.Pool.UsedPct, humanBytes(snap.Pool.TotalBytes)))
		}
	}

	for _, arr := range snap.RAID {
		if arr.Degraded {
			add("raid_"+arr.Name, "critical", fmt.Sprintf("%s is degraded", arr.Name),
				fmt.Sprintf("%s (%s) members=%s state=%s", arr.Name, arr.Level, arr.Members, arr.State))
		}
	}

	for _, sm := range snap.SMART {
		if sm.Error != "" || sm.Health == "" || sm.Health == "UNKNOWN" {
			continue
		}
		if !strings.EqualFold(sm.Health, "PASSED") {
			add("smart_health_"+sm.Device, "critical", fmt.Sprintf("%s SMART health failed", sm.Device),
				fmt.Sprintf("%s (%s) reports %s.", sm.Device, sm.Model, sm.Health))
		}
		if sm.Pending > 0 || sm.Uncorrectable > 0 || sm.MediaErrors > 0 {
			add("smart_bad_"+sm.Device, "warning", fmt.Sprintf("%s has bad sectors", sm.Device),
				fmt.Sprintf("%s: pending=%d uncorrectable=%d media_errors=%d",
					sm.Device, sm.Pending, sm.Uncorrectable, sm.MediaErrors))
		}
	}

	for _, name := range cfg.ExpectedContainers {
		ci, ok := findContainer(snap.Containers, name)
		switch {
		case !ok:
			add("container_"+name, "warning", fmt.Sprintf("Container %s is missing", name),
				fmt.Sprintf("Expected container %q is not present.", name))
		case ci.State != "running":
			add("container_"+name, "warning", fmt.Sprintf("Container %s is not running", name),
				fmt.Sprintf("%s is %s (%s).", name, ci.State, ci.Status))
		}
	}

	if n := st.RebootCountSince(cfg.RebootWindow); n >= 2 {
		add("reboot_loop", "critical", "Reboot loop detected",
			fmt.Sprintf("%d reboots within %s. Automatic recovery is suppressed until this stops; "+
				"the underlying fault is not fixed by reboots.", n, cfg.RebootWindow))
	}

	sort.SliceStable(active, func(i, j int) bool {
		ri, rj := severityRank[active[i].Severity], severityRank[active[j].Severity]
		if ri != rj {
			return ri < rj
		}
		return active[i].ID < active[j].ID
	})
	return active
}
