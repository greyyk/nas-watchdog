package main

import (
	"testing"
	"time"
)

func TestHistoryIncludesAllTemperaturesZonesAndFans(t *testing.T) {
	history := newHistory(2)
	history.Add(&Snapshot{
		TS: time.Now(), CPU: CPUInfo{UsagePct: 12.5}, Memory: MemoryInfo{UsedPct: 42},
		Temps: []TempReading{{Key: "cpu", C: 51}, {Key: "cpu_core_0", C: 49}, {Key: "hdd_sda", C: 44}},
		ThermalZones: []ThermalZoneReading{
			{Key: "zone_thermal_zone0", C: 27.8, Mode: "enabled"},
			{Key: "zone_thermal_zone1", C: 20, Mode: "disabled"},
		},
		Fans: []FanReading{{Key: "fan_1", RPM: 1450, PWM: 144}, {Key: "fan_2", RPM: 0, PWM: 0}},
	})
	samples := history.Snapshot()
	if len(samples) != 1 {
		t.Fatalf("got %d samples", len(samples))
	}
	sample := samples[0]
	for key, want := range map[string]float64{"cpu": 51, "cpu_core_0": 49, "hdd_sda": 44, "zone_thermal_zone0": 27.8} {
		if got, ok := sample.Temps[key]; !ok || got != want {
			t.Errorf("temperature %s = %.1f, present=%v; want %.1f", key, got, ok, want)
		}
	}
	if _, ok := sample.Temps["zone_thermal_zone1"]; ok {
		t.Error("disabled thermal zones must not create misleading history")
	}
	if got := sample.Fans["fan_1"]; got.RPM != 1450 || got.PWM != 144 {
		t.Errorf("fan history = %+v", got)
	}
	if got, ok := sample.Fans["fan_2"]; !ok || got.RPM != 0 {
		t.Errorf("stopped fan was not retained: %+v, %v", got, ok)
	}
}

func TestHistoryRetainsMostRecentSamples(t *testing.T) {
	history := newHistory(2)
	base := time.Now()
	for i := 0; i < 3; i++ {
		history.Add(&Snapshot{TS: base.Add(time.Duration(i) * 30 * time.Second), CPU: CPUInfo{UsagePct: float64(i)}})
	}
	samples := history.Snapshot()
	if len(samples) != 2 || samples[0].CPU != 1 || samples[1].CPU != 2 {
		t.Fatalf("unexpected retention behavior: %+v", samples)
	}
}
