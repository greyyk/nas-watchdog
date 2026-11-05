package main

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

func (c *Collector) collectHwmon() ([]TempReading, []FanReading) {
	base := c.hostPath("sys/class/hwmon")
	dirs, err := os.ReadDir(base)
	if err != nil {
		return nil, nil
	}

	var temps []TempReading
	var fans []FanReading
	var dimms []TempReading
	fanKeys := map[string]int{}
	topology := c.cpuTopologyByCore()

	for _, d := range dirs {
		dir := filepath.Join(base, d.Name())
		name := readTrim(filepath.Join(dir, "name"))
		if name == "" {
			continue
		}
		devicePath := collectorDevicePath(c.cfg.HostRoot, dir)

		inputs, _ := filepath.Glob(filepath.Join(dir, "temp*_input"))
		sort.Strings(inputs)
		for _, in := range inputs {
			milli, ok := readInt64OK(in)
			if !ok || float64(milli)/1000 <= -200 {
				continue
			}
			stem := strings.TrimSuffix(filepath.Base(in), "_input")
			sensorLabel := readTrim(filepath.Join(dir, stem+"_label"))
			reading := TempReading{
				Label: sensorLabel, C: float64(milli) / 1000, Category: "other", Source: "hwmon",
				Chip: name, Sensor: stem, Device: devicePath,
				Limits: readTemperatureLimits(dir, stem), Alarms: readTemperatureAlarms(dir, stem),
			}

			switch name {
			case "spd5118", "jc42", "ee1004":
				reading.Category = "dimm"
				reading.Warn, reading.Crit = c.cfg.TempDimmWarn, c.cfg.TempDimmCrit
				dimms = append(dimms, reading)
				continue
			case "coretemp":
				reading.Warn, reading.Crit = c.cfg.TempCPUWarn, c.cfg.TempCPUCrit
				reading.Device = "coretemp"
				if strings.Contains(strings.ToLower(sensorLabel), "package") {
					reading.Key, reading.Label, reading.Category = "cpu", "CPU", "cpu_package"
				} else if match := coreTempLabelRe.FindStringSubmatch(sensorLabel); match != nil {
					coreID, _ := strconv.Atoi(match[1])
					reading.Key = fmt.Sprintf("cpu_core_%d", coreID)
					reading.Category = "cpu_core"
					if top, ok := topology[coreID]; ok {
						reading.CPUs, reading.CoreType = top.CPUs, top.CoreType
					}
				}
			case "nvme":
				controller := nvmeController(devicePath)
				reading.Category = "nvme"
				reading.Device = "/dev/" + controller
				reading.Warn, reading.Crit = c.cfg.TempNVMeWarn, c.cfg.TempNVMeCrit
				label := sensorLabel
				if label == "" {
					label = stem
				}
				reading.Key = nvmeTemperatureKey(controller, stem, sensorLabel)
				reading.Label = strings.Replace(controller, "nvme", "NVMe ", 1) + " " + label
			case "acpitz":
				reading.Key, reading.Label, reading.Category = "board", "Board", "board"
				if sensorLabel != "" {
					reading.Label += " " + sensorLabel
				} else if stem != "temp1" {
					reading.Label += " " + stem
				}
				reading.Device = "acpitz"
				reading.Warn, reading.Crit = c.cfg.TempBoardWarn, c.cfg.TempBoardCrit
			}
			if reading.Key == "" {
				reading.Key = sanitizeKey(name + "_" + deviceLeaf(devicePath) + "_" + stem)
			}
			if reading.Label == "" {
				reading.Label = strings.TrimSpace(name + " " + stem)
			}
			temps = append(temps, reading)
		}

		fanInputs, _ := filepath.Glob(filepath.Join(dir, "fan*_input"))
		sort.Strings(fanInputs)
		for _, in := range fanInputs {
			rpm, ok := readInt64OK(in)
			if !ok || rpm < 0 {
				continue
			}
			stem := strings.TrimSuffix(filepath.Base(in), "_input")
			num := strings.TrimPrefix(stem, "fan")
			label := readTrim(filepath.Join(dir, stem+"_label"))
			if label == "" {
				label = "Fan " + num
				if name != "zettos_pwm_fan" && name != "acpi_fan" && name != "it87" {
					label = strings.TrimSpace(name + " fan " + num)
				}
			}
			pwm := readInt64(filepath.Join(dir, "fan"+num+"_pwm"))
			if pwm == 0 {
				pwm = readInt64(filepath.Join(dir, "pwm"+num))
			}
			fans = append(fans, FanReading{
				Key: claimUniqueKey(sanitizeKey(name+"_"+stem), fanKeys), Label: label, RPM: clampInt(rpm), PWM: clampInt(pwm),
			})
		}
	}

	sort.Slice(dimms, func(i, j int) bool {
		if dimms[i].Device != dimms[j].Device {
			return dimms[i].Device < dimms[j].Device
		}
		return dimms[i].Sensor < dimms[j].Sensor
	})
	for i, dimm := range dimms {
		dimm.Key = fmt.Sprintf("dimm%d", i)
		dimm.Label = fmt.Sprintf("DIMM %d", i)
		temps = append(temps, dimm)
	}
	temps = uniqueTemperatureKeys(temps)
	temps = addLegacyCPUFallback(temps)
	sortTempReadings(temps)
	sort.Slice(fans, func(i, j int) bool { return fans[i].Key < fans[j].Key })
	return temps, fans
}

var (
	coreTempLabelRe = regexp.MustCompile(`^Core ([0-9]+)$`)
	nvmePathRe      = regexp.MustCompile(`(?:^|/)nvme([0-9]+)(?:/|$)`)
)

type cpuThermalTopology struct {
	CPUs     []int
	CoreType string
}

func (c *Collector) cpuTopologyByCore() map[int]cpuThermalTopology {
	out := map[int]cpuThermalTopology{}
	coreType := map[int]string{}
	for _, logical := range expandCPUList(readTrim(c.hostPath("sys/devices/cpu_core/cpus"))) {
		coreType[logical] = "performance"
	}
	for _, logical := range expandCPUList(readTrim(c.hostPath("sys/devices/cpu_atom/cpus"))) {
		coreType[logical] = "efficiency"
	}

	dirs, _ := filepath.Glob(c.hostPath("sys/devices/system/cpu/cpu[0-9]*"))
	for _, dir := range dirs {
		logical, err := strconv.Atoi(strings.TrimPrefix(filepath.Base(dir), "cpu"))
		if err != nil {
			continue
		}
		coreID, ok := readInt64OK(filepath.Join(dir, "topology/core_id"))
		if !ok {
			continue
		}
		top := out[int(coreID)]
		top.CPUs = append(top.CPUs, logical)
		if top.CoreType == "" {
			top.CoreType = coreType[logical]
		}
		out[int(coreID)] = top
	}
	for id, top := range out {
		sort.Ints(top.CPUs)
		out[id] = top
	}
	return out
}

func expandCPUList(spec string) []int {
	var out []int
	for _, part := range strings.Split(spec, ",") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		bounds := strings.SplitN(part, "-", 2)
		start, err := strconv.Atoi(bounds[0])
		if err != nil {
			continue
		}
		end := start
		if len(bounds) == 2 {
			end, err = strconv.Atoi(bounds[1])
			if err != nil || end < start {
				continue
			}
		}
		for n := start; n <= end; n++ {
			out = append(out, n)
		}
	}
	return out
}

func collectorDevicePath(hostRoot, dir string) string {
	resolved, err := filepath.EvalSymlinks(dir)
	if err != nil {
		resolved = dir
	}
	if hostRoot != "" {
		resolved = strings.TrimPrefix(resolved, strings.TrimSuffix(hostRoot, "/"))
	}
	return resolved
}

func deviceLeaf(devicePath string) string {
	parts := strings.Split(strings.Trim(devicePath, "/"), "/")
	for len(parts) > 0 && (strings.HasPrefix(parts[len(parts)-1], "hwmon") || parts[len(parts)-1] == "hwmon") {
		parts = parts[:len(parts)-1]
	}
	if len(parts) == 0 {
		return "sensor"
	}
	return parts[len(parts)-1]
}

func nvmeController(devicePath string) string {
	matches := nvmePathRe.FindAllStringSubmatch(devicePath, -1)
	if len(matches) == 0 {
		return "nvme"
	}
	return "nvme" + matches[len(matches)-1][1]
}

func nvmeTemperatureKey(controller, stem, label string) string {
	if strings.EqualFold(label, "Composite") {
		return controller
	}
	fields := strings.Fields(strings.ToLower(label))
	if len(fields) == 2 && fields[0] == "sensor" {
		if n, err := strconv.Atoi(fields[1]); err == nil {
			return fmt.Sprintf("%s_sensor_%d", controller, n)
		}
	}
	return controller + "_" + sanitizeKey(stem)
}

func readTemperatureLimits(dir, stem string) *TemperatureLimits {
	limits := &TemperatureLimits{}
	set := func(suffix string, target **float64) {
		if milli, ok := readInt64OK(filepath.Join(dir, stem+suffix)); ok {
			value := float64(milli) / 1000
			if value >= -100 && value <= 200 {
				*target = &value
			}
		}
	}
	set("_min", &limits.Min)
	set("_max", &limits.Max)
	set("_lcrit", &limits.LCrit)
	set("_crit", &limits.Crit)
	set("_emergency", &limits.Emergency)
	if limits.Min == nil && limits.Max == nil && limits.LCrit == nil && limits.Crit == nil && limits.Emergency == nil {
		return nil
	}
	return limits
}

func readTemperatureAlarms(dir, stem string) []string {
	var alarms []string
	for _, name := range []string{"alarm", "min_alarm", "max_alarm", "lcrit_alarm", "crit_alarm", "emergency_alarm"} {
		if value, ok := readInt64OK(filepath.Join(dir, stem+"_"+name)); ok && value != 0 {
			alarms = append(alarms, strings.TrimSuffix(name, "_alarm"))
		}
	}
	return alarms
}

func claimUniqueKey(base string, used map[string]int) string {
	if base == "" {
		base = "sensor"
	}
	if used[base] == 0 {
		used[base] = 1
		return base
	}
	for n := used[base] + 1; ; n++ {
		candidate := fmt.Sprintf("%s_%d", base, n)
		if used[candidate] == 0 {
			used[base] = n
			used[candidate] = 1
			return candidate
		}
	}
}

func uniqueTemperatureKeys(temps []TempReading) []TempReading {
	used := map[string]int{}
	for i := range temps {
		temps[i].Key = claimUniqueKey(temps[i].Key, used)
	}
	return temps
}

func addLegacyCPUFallback(temps []TempReading) []TempReading {
	var hottest *TempReading
	for i := range temps {
		if temps[i].Key == "cpu" {
			return temps
		}
		if temps[i].Category == "cpu_core" && (hottest == nil || temps[i].C > hottest.C) {
			hottest = &temps[i]
		}
	}
	if hottest == nil {
		return temps
	}
	fallback := *hottest
	fallback.Key, fallback.Label, fallback.Category = "cpu", "CPU", "cpu_package"
	fallback.CPUs, fallback.CoreType = nil, ""
	return append(temps, fallback)
}

func sortTempReadings(temps []TempReading) {
	sort.Slice(temps, func(i, j int) bool { return temps[i].Key < temps[j].Key })
}

func (c *Collector) collectThermalZones() []ThermalZoneReading {
	var out []ThermalZoneReading
	zones, _ := filepath.Glob(c.hostPath("sys/class/thermal/thermal_zone[0-9]*/temp"))
	for _, path := range zones {
		dir := filepath.Dir(path)
		milli, ok := readInt64OK(path)
		label := readTrim(filepath.Join(dir, "type"))
		if !ok || float64(milli)/1000 <= -200 || label == "" {
			continue
		}
		zone := ThermalZoneReading{
			Key: "zone_" + sanitizeKey(filepath.Base(dir)), Label: label, C: float64(milli) / 1000,
			Mode: readTrim(filepath.Join(dir, "mode")), Policy: readTrim(filepath.Join(dir, "policy")),
		}
		trips, _ := filepath.Glob(filepath.Join(dir, "trip_point_[0-9]*_temp"))
		for _, tripPath := range trips {
			tripMilli, ok := readInt64OK(tripPath)
			if !ok || float64(tripMilli)/1000 <= -200 {
				continue
			}
			stem := strings.TrimSuffix(filepath.Base(tripPath), "_temp")
			index, err := strconv.Atoi(strings.TrimPrefix(stem, "trip_point_"))
			if err != nil {
				continue
			}
			hyst := readInt64(filepath.Join(dir, stem+"_hyst"))
			zone.Trips = append(zone.Trips, ThermalTrip{
				Index: index, Type: readTrim(filepath.Join(dir, stem+"_type")),
				TempC: float64(tripMilli) / 1000, HystC: float64(hyst) / 1000,
			})
		}
		sort.Slice(zone.Trips, func(i, j int) bool { return zone.Trips[i].Index < zone.Trips[j].Index })
		out = append(out, zone)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Key < out[j].Key })
	return out
}

var (
	mdNameRe   = regexp.MustCompile(`^(md\d+)\s*:\s*(.*)$`)
	mdLevelRe  = regexp.MustCompile(`\b(raid\d+|linear|multipath)\b`)
	mdCountRe  = regexp.MustCompile(`\[(\d+)/(\d+)\]`)
	mdMemberRe = regexp.MustCompile(`\[([U_]+)\]`)
)

func (c *Collector) collectRAID() []RAIDArray {
	f, err := os.Open(c.hostPath("proc/mdstat"))
	if err != nil {
		return nil
	}
	defer f.Close()

	var out []RAIDArray
	var cur *RAIDArray
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := sc.Text()
		if m := mdNameRe.FindStringSubmatch(strings.TrimSpace(line)); m != nil {
			if cur != nil {
				out = append(out, *cur)
			}
			cur = &RAIDArray{Name: m[1], State: strings.TrimSpace(m[2]), Detail: line}
			continue
		}
		if cur == nil {
			continue
		}
		trimmed := strings.TrimSpace(line)
		if trimmed == "" {
			out = append(out, *cur)
			cur = nil
			continue
		}
		if strings.Contains(trimmed, "resync") || strings.Contains(trimmed, "recovery") ||
			strings.Contains(trimmed, "check") {
			cur.Sync = trimmed
		}
		if lv := mdLevelRe.FindString(trimmed); lv != "" && cur.Level == "" {
			cur.Level = lv
		}
		if cm := mdCountRe.FindStringSubmatch(trimmed); cm != nil && cm[1] != cm[2] {
			cur.Degraded = true
		}
		if mm := mdMemberRe.FindStringSubmatch(trimmed); mm != nil {
			cur.Members = mm[1]
			if strings.Contains(mm[1], "_") {
				cur.Degraded = true
			}
		}
		cur.Detail = trimmed
	}
	if cur != nil {
		out = append(out, *cur)
	}
	return out
}
