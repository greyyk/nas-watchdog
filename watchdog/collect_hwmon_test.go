package main

import (
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strconv"
	"strings"
	"testing"
)

func writeSensorFile(t *testing.T, path, value string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(value+"\n"), 0o644); err != nil {
		t.Fatal(err)
	}
}

func makeHwmon(t *testing.T, root, instance, target, chip string, files map[string]string) {
	t.Helper()
	targetDir := filepath.Join(root, "sys/devices", target)
	for name, value := range files {
		writeSensorFile(t, filepath.Join(targetDir, name), value)
	}
	writeSensorFile(t, filepath.Join(targetDir, "name"), chip)
	classDir := filepath.Join(root, "sys/class/hwmon")
	if err := os.MkdirAll(classDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(targetDir, filepath.Join(classDir, instance)); err != nil {
		t.Fatal(err)
	}
}

func milliFiles(index, value int, label string) map[string]string {
	stem := "temp" + strconv.Itoa(index)
	return map[string]string{
		stem + "_input": strconv.Itoa(value),
		stem + "_label": label,
	}
}

func makeZettyThermalFixture(t *testing.T) (*Collector, string) {
	t.Helper()
	root := t.TempDir()
	tempIndexByCore := map[int]int{0: 2, 1: 3, 2: 4, 3: 5, 4: 6, 5: 7, 6: 8, 7: 9, 8: 10, 12: 14, 16: 18, 20: 22, 32: 34, 33: 35}
	coreFiles := milliFiles(1, 56000, "Package id 0")
	coreFiles["temp1_max"] = "110000"
	coreFiles["temp1_crit"] = "110000"
	for core, index := range tempIndexByCore {
		stem := "temp" + strconv.Itoa(index)
		coreFiles[stem+"_input"] = strconv.Itoa(45000 + core*100)
		coreFiles[stem+"_label"] = fmt.Sprintf("Core %d", core)
		coreFiles[stem+"_max"] = "110000"
		coreFiles[stem+"_crit"] = "110000"
		coreFiles[stem+"_crit_alarm"] = "0"
	}
	makeHwmon(t, root, "hwmon8", "platform/coretemp.0/hwmon/hwmon8", "coretemp", coreFiles)

	for i, address := range []string{"0050", "0052"} {
		makeHwmon(t, root, fmt.Sprintf("hwmon%d", i+6), "pci0000:00/0000:00:1f.4/i2c-12/12-"+address+"/hwmon/hwmon"+strconv.Itoa(i+6), "spd5118", map[string]string{
			"temp1_input":      strconv.Itoa(35000 + i*750),
			"temp1_min":        "0",
			"temp1_max":        "55000",
			"temp1_lcrit":      "0",
			"temp1_crit":       "85000",
			"temp1_crit_alarm": "0",
		})
	}
	makeHwmon(t, root, "hwmon2", "pci0000:00/0000:00:06.2/0000:02:00.0/nvme/nvme1/hwmon2", "nvme", map[string]string{
		"temp1_input": "26850", "temp1_label": "Composite", "temp1_min": "-20150",
		"temp1_max": "74850", "temp1_crit": "79850", "temp1_alarm": "0",
	})
	makeHwmon(t, root, "hwmon3", "pci0000:00/0000:00:06.0/0000:01:00.0/nvme/nvme0/hwmon3", "nvme", map[string]string{
		"temp1_input": "37850", "temp1_label": "Composite", "temp1_min": "-273150",
		"temp1_max": "89850", "temp1_crit": "94850",
		"temp2_input": "37850", "temp2_label": "Sensor 1", "temp2_max": "65261850", "temp2_min": "-273150",
		"temp3_input": "31850", "temp3_label": "Sensor 2", "temp3_max": "65261850", "temp3_min": "-273150",
	})
	makeHwmon(t, root, "hwmon0", "virtual/thermal/thermal_zone0/hwmon0", "acpitz", milliFiles(1, 27800, ""))
	makeHwmon(t, root, "hwmon1", "platform/zettos_pwm_fan/hwmon/hwmon1", "zettos_pwm_fan", map[string]string{
		"fan1_input": "1450", "fan1_pwm": "144", "fan2_input": "0", "fan2_pwm": "0", "fan3_input": "3213", "fan3_pwm": "139",
	})

	cpuCores := map[int][]int{16: {0, 5}, 8: {1, 2}, 2: {10}, 3: {11}, 4: {12}, 5: {13}, 6: {14}, 7: {15}, 32: {16}, 33: {17}, 12: {3, 4}, 20: {6, 7}, 0: {8}, 1: {9}}
	for core, cpus := range cpuCores {
		for _, cpu := range cpus {
			writeSensorFile(t, filepath.Join(root, fmt.Sprintf("sys/devices/system/cpu/cpu%d/topology/core_id", cpu)), strconv.Itoa(core))
		}
	}
	writeSensorFile(t, filepath.Join(root, "sys/devices/cpu_core/cpus"), "0-7")
	writeSensorFile(t, filepath.Join(root, "sys/devices/cpu_atom/cpus"), "8-17")
	return newCollector(&Config{HostRoot: root, TempCPUWarn: 90, TempCPUCrit: 100, TempDimmWarn: 60, TempDimmCrit: 70, TempNVMeWarn: 70, TempNVMeCrit: 85, TempBoardWarn: 60, TempBoardCrit: 75}), root
}

func readingByKey(t *testing.T, readings []TempReading, key string) TempReading {
	t.Helper()
	for _, reading := range readings {
		if reading.Key == key {
			return reading
		}
	}
	t.Fatalf("missing temperature %q", key)
	return TempReading{}
}

func TestCollectHwmonPreservesEveryZettySensor(t *testing.T) {
	collector, _ := makeZettyThermalFixture(t)
	temps, fans := collector.collectHwmon()
	if len(temps) != 22 {
		t.Fatalf("got %d temperature readings, want 22", len(temps))
	}
	var coreKeys []string
	for _, temp := range temps {
		if temp.Category == "cpu_core" {
			coreKeys = append(coreKeys, temp.Key)
		}
	}
	var wantCoreKeys []string
	for _, core := range []int{0, 1, 2, 3, 4, 5, 6, 7, 8, 12, 16, 20, 32, 33} {
		wantCoreKeys = append(wantCoreKeys, fmt.Sprintf("cpu_core_%d", core))
	}
	// API ordering is lexical, so compare as sets.
	coreSet := map[string]bool{}
	for _, key := range coreKeys {
		coreSet[key] = true
	}
	for _, key := range wantCoreKeys {
		if !coreSet[key] {
			t.Errorf("missing %s", key)
		}
	}
	if len(coreKeys) != 14 {
		t.Fatalf("got %d core readings, want 14", len(coreKeys))
	}

	cpu := readingByKey(t, temps, "cpu")
	if cpu.C != 56 || cpu.Category != "cpu_package" || cpu.Limits == nil || cpu.Limits.Crit == nil || *cpu.Limits.Crit != 110 {
		t.Fatalf("unexpected package reading: %+v", cpu)
	}
	core16 := readingByKey(t, temps, "cpu_core_16")
	if !reflect.DeepEqual(core16.CPUs, []int{0, 5}) || core16.CoreType != "performance" {
		t.Fatalf("core topology was not mapped: %+v", core16)
	}
	core0 := readingByKey(t, temps, "cpu_core_0")
	if !reflect.DeepEqual(core0.CPUs, []int{8}) || core0.CoreType != "efficiency" {
		t.Fatalf("efficiency core topology was not mapped: %+v", core0)
	}

	for i := 0; i < 2; i++ {
		dimm := readingByKey(t, temps, fmt.Sprintf("dimm%d", i))
		if dimm.Limits == nil || dimm.Limits.Min == nil || *dimm.Limits.Min != 0 || dimm.Limits.Max == nil || *dimm.Limits.Max != 55 || dimm.Limits.Crit == nil || *dimm.Limits.Crit != 85 {
			t.Fatalf("DIMM %d limits missing: %+v", i, dimm)
		}
	}

	if key := readingByKey(t, temps, "nvme0").Label; key != "NVMe 0 Composite" {
		t.Errorf("composite label = %q", key)
	}
	if got := readingByKey(t, temps, "nvme0_sensor_1").Device; got != "/dev/nvme0" {
		t.Errorf("sensor device = %q", got)
	}
	if got := readingByKey(t, temps, "nvme1").Device; got != "/dev/nvme1" {
		t.Errorf("second controller device = %q", got)
	}
	sensor := readingByKey(t, temps, "nvme0_sensor_2")
	if sensor.Limits != nil {
		t.Fatalf("placeholder NVMe limits should be omitted: %+v", sensor.Limits)
	}
	nvme0 := readingByKey(t, temps, "nvme0")
	if nvme0.Limits == nil || nvme0.Limits.Min != nil || nvme0.Limits.Max == nil || *nvme0.Limits.Max != 89.85 {
		t.Fatalf("valid NVMe limits should survive sentinel filtering: %+v", nvme0.Limits)
	}

	board := readingByKey(t, temps, "board")
	if board.Warn != 60 || board.Crit != 75 {
		t.Errorf("board thresholds = %.0f/%.0f", board.Warn, board.Crit)
	}
	if len(fans) != 3 || fans[1].RPM != 0 {
		t.Fatalf("stopped fans should remain visible: %+v", fans)
	}
}

func TestLegacyCPUFallbackUsesHottestCoreOnlyWithoutPackageSensor(t *testing.T) {
	temps := []TempReading{
		{Key: "cpu_core_0", Label: "Core 0", C: 45, Category: "cpu_core"},
		{Key: "cpu_core_2", Label: "Core 2", C: 55, Category: "cpu_core"},
	}
	withFallback := addLegacyCPUFallback(temps)
	fallback := readingByKey(t, withFallback, "cpu")
	if len(withFallback) != 3 || fallback.C != 55 || fallback.Category != "cpu_package" {
		t.Fatalf("unexpected fallback: %+v", withFallback)
	}
	if got := addLegacyCPUFallback(withFallback); len(got) != 3 {
		t.Fatalf("package fallback was duplicated: %+v", got)
	}
}

func TestCollectHwmonCollectsUnknownChipsAndRejectsOnlySentinels(t *testing.T) {
	root := t.TempDir()
	makeHwmon(t, root, "hwmon0", "pci0000:00/fake-adapter/hwmon/hwmon0", "futurechip", map[string]string{
		"temp1_input": "-10000", "temp1_label": "Ambient", "temp1_max": "125000",
		"temp2_input": "-274000", "temp2_label": "Disconnected",
	})
	collector := newCollector(&Config{HostRoot: root})
	temps, _ := collector.collectHwmon()
	if len(temps) != 1 {
		t.Fatalf("got %d readings, want one valid generic sensor: %+v", len(temps), temps)
	}
	if temps[0].C != -10 || temps[0].Category != "other" || temps[0].Label != "Ambient" || temps[0].Chip != "futurechip" {
		t.Fatalf("unexpected generic reading: %+v", temps[0])
	}
	if temps[0].Limits == nil || temps[0].Limits.Max == nil || *temps[0].Limits.Max != 125 {
		t.Fatalf("generic limit missing: %+v", temps[0].Limits)
	}
}

func TestCollectThermalZonesIncludesTripsAndDisabledState(t *testing.T) {
	root := t.TempDir()
	writeSensorFile(t, filepath.Join(root, "sys/class/thermal/thermal_zone0/type"), "acpitz")
	writeSensorFile(t, filepath.Join(root, "sys/class/thermal/thermal_zone0/temp"), "27800")
	writeSensorFile(t, filepath.Join(root, "sys/class/thermal/thermal_zone0/mode"), "enabled")
	writeSensorFile(t, filepath.Join(root, "sys/class/thermal/thermal_zone0/policy"), "step_wise")
	writeSensorFile(t, filepath.Join(root, "sys/class/thermal/thermal_zone0/trip_point_0_temp"), "115000")
	writeSensorFile(t, filepath.Join(root, "sys/class/thermal/thermal_zone0/trip_point_0_type"), "critical")
	writeSensorFile(t, filepath.Join(root, "sys/class/thermal/thermal_zone0/trip_point_0_hyst"), "2000")
	writeSensorFile(t, filepath.Join(root, "sys/class/thermal/thermal_zone0/trip_point_1_temp"), "-274000")
	writeSensorFile(t, filepath.Join(root, "sys/class/thermal/thermal_zone0/trip_point_1_type"), "passive")
	writeSensorFile(t, filepath.Join(root, "sys/class/thermal/thermal_zone1/type"), "INT3400 Thermal")
	writeSensorFile(t, filepath.Join(root, "sys/class/thermal/thermal_zone1/temp"), "20000")
	writeSensorFile(t, filepath.Join(root, "sys/class/thermal/thermal_zone1/mode"), "disabled")

	zones := newCollector(&Config{HostRoot: root}).collectThermalZones()
	if len(zones) != 2 {
		t.Fatalf("got %d zones, want 2", len(zones))
	}
	if zones[0].C != 27.8 || zones[0].Mode != "enabled" || len(zones[0].Trips) != 1 {
		t.Fatalf("unexpected first zone: %+v", zones[0])
	}
	trip := zones[0].Trips[0]
	if trip.Index != 0 || trip.Type != "critical" || trip.TempC != 115 || trip.HystC != 2 {
		t.Fatalf("unexpected trip: %+v", trip)
	}
	if zones[1].Mode != "disabled" {
		t.Fatalf("disabled state lost: %+v", zones[1])
	}
}

func TestCollectHwmonFindsGenericFansAndKeepsIDsUnique(t *testing.T) {
	root := t.TempDir()
	makeHwmon(t, root, "hwmon0", "platform/controller-a/hwmon/hwmon0", "futurefan", map[string]string{
		"fan1_input": "900", "fan1_pwm": "77",
	})
	makeHwmon(t, root, "hwmon1", "platform/controller-b/hwmon/hwmon1", "futurefan", map[string]string{
		"fan1_input": "0", "fan1_pwm": "0",
	})

	_, fans := newCollector(&Config{HostRoot: root}).collectHwmon()
	if len(fans) != 2 {
		t.Fatalf("got %d fans, want both generic fans: %+v", len(fans), fans)
	}
	if fans[0].Key == fans[1].Key {
		t.Fatalf("fan IDs collide across controllers: %+v", fans)
	}
	if fans[0].RPM != 0 && fans[1].RPM != 0 {
		t.Fatal("a stopped fan must remain visible instead of disappearing")
	}
	if fans[0].Key != "futurefan_fan1" && fans[1].Key != "futurefan_fan1" {
		t.Fatal("the first fan should retain the original key format")
	}
}

func TestCollectHwmonSupportsAdditionalBoardChannelsWithoutKeyCollisions(t *testing.T) {
	root := t.TempDir()
	files := milliFiles(1, 28000, "")
	for key, value := range milliFiles(2, 31000, "Ambient") {
		files[key] = value
	}
	makeHwmon(t, root, "hwmon0", "virtual/thermal/thermal_zone0/hwmon0", "acpitz", files)

	temps, _ := newCollector(&Config{HostRoot: root}).collectHwmon()
	if len(temps) != 2 {
		t.Fatalf("got %d board channels, want 2: %+v", len(temps), temps)
	}
	first := readingByKey(t, temps, "board")
	second := readingByKey(t, temps, "board_2")
	if first.C != 28 || second.C != 31 {
		t.Fatalf("unexpected board channels: %+v", temps)
	}
	if second.Label != "Board Ambient" {
		t.Fatalf("additional channel label = %q", second.Label)
	}
}

func TestCollectHwmonAllowsEmptyHardwareInventory(t *testing.T) {
	root := t.TempDir()
	temps, fans := newCollector(&Config{HostRoot: root}).collectHwmon()
	if len(temps) != 0 || len(fans) != 0 {
		t.Fatalf("empty hosts should return an empty inventory: temps=%+v fans=%+v", temps, fans)
	}
}

func TestExpandCPUList(t *testing.T) {
	got := expandCPUList("0-3,5,8-9")
	want := []int{0, 1, 2, 3, 5, 8, 9}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("expandCPUList = %v, want %v", got, want)
	}
	if bad := expandCPUList("nonsense,5-2"); len(bad) != 0 {
		t.Fatalf("invalid CPU ranges should be ignored: %v", bad)
	}
}

func TestNVMeControllerUsesControllerPathNotEnumerationOrder(t *testing.T) {
	path := "/host/sys/devices/pci0000:00/0000:00:06.0/0000:01:00.0/nvme/nvme0/hwmon3"
	if got := nvmeController(path); got != "nvme0" {
		t.Fatalf("controller = %q", got)
	}
	if got := nvmeTemperatureKey("nvme0", "temp3", "Sensor 2"); got != "nvme0_sensor_2" {
		t.Fatalf("sensor key = %q", got)
	}
	if got := nvmeTemperatureKey("nvme0", "temp1", ""); got != "nvme0_temp1" {
		t.Fatalf("unlabeled composite must not be invented: %q", got)
	}
}

func TestTemperatureAlarmNames(t *testing.T) {
	root := t.TempDir()
	writeSensorFile(t, filepath.Join(root, "temp1_alarm"), "0")
	writeSensorFile(t, filepath.Join(root, "temp1_max_alarm"), "1")
	writeSensorFile(t, filepath.Join(root, "temp1_crit_alarm"), "1")
	if got := strings.Join(readTemperatureAlarms(root, "temp1"), ","); got != "max,crit" {
		t.Fatalf("alarms = %q", got)
	}
}
