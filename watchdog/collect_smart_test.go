package main

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"
)

func TestParseDriveTemperatureRange(t *testing.T) {
	min, max, ok := parseDriveTemperatureRange("44 (Min/Max 44/45)")
	if !ok || min != 44 || max != 45 {
		t.Fatalf("got %.1f/%.1f, %v", min, max, ok)
	}
	min, max, ok = parseDriveTemperatureRange("-5.5 (Min/Max -10.0/45.5)")
	if !ok || min != -10 || max != 45.5 {
		t.Fatalf("decimal/signed range = %.1f/%.1f, %v", min, max, ok)
	}
	for _, raw := range []string{"44 (0 18 0 0 0)", "", "44 (Min/Max 60/20)", "44 (Min/Max -274/45)"} {
		if _, _, ok := parseDriveTemperatureRange(raw); ok {
			t.Errorf("malformed/invalid range %q was accepted", raw)
		}
	}
}

func TestSmartTemperatureReadingsIncludeOnlyRealSATADrives(t *testing.T) {
	cfg := &Config{TempHDDWarn: 50, TempHDDCrit: 60}
	readings := smartTemperatureReadings([]SMARTInfo{
		{Device: "/dev/sda", Type: "ata", Model: "ST14000NM000J-2TX103", Temperature: 44},
		{Device: "/dev/nvme0n1", Type: "nvme", Model: "RS256GSSD510", Temperature: 36},
		{Device: "/dev/sdb", Type: "ata", Model: "Unavailable", Temperature: 0},
	}, cfg)
	if len(readings) != 1 {
		t.Fatalf("got %d readings, want one SATA temperature: %+v", len(readings), readings)
	}
	got := readings[0]
	if got.Key != "hdd_sda" || got.C != 44 || got.Category != "hdd" || got.Source != "smart" || got.Device != "/dev/sda" || got.Warn != 50 || got.Crit != 60 {
		t.Fatalf("unexpected SATA temperature: %+v", got)
	}
}

func TestSmartTemperatureReadingsScaleToEveryPopulatedSATASlot(t *testing.T) {
	inventory := make([]SMARTInfo, 0, 8)
	for slot := 0; slot < 8; slot++ {
		inventory = append(inventory, SMARTInfo{
			Device: fmt.Sprintf("/dev/sd%c", 'a'+slot), Type: "ata",
			Model: fmt.Sprintf("SATA slot %d", slot+1), Temperature: float64(35 + slot),
		})
	}
	// NVMe temperatures come from hwmon so they are not duplicated here.
	inventory = append(inventory,
		SMARTInfo{Device: "/dev/nvme0n1", Type: "nvme", Temperature: 36},
		SMARTInfo{Device: "/dev/nvme1n1", Type: "nvme", Temperature: 37},
	)

	readings := smartTemperatureReadings(inventory, &Config{TempHDDWarn: 50, TempHDDCrit: 60})
	if len(readings) != 8 {
		t.Fatalf("got %d SATA readings, want all 8 populated slots: %+v", len(readings), readings)
	}
	seen := map[string]bool{}
	for i, reading := range readings {
		wantDevice := fmt.Sprintf("/dev/sd%c", 'a'+i)
		if reading.Device != wantDevice || seen[reading.Key] {
			t.Fatalf("missing or duplicate SATA reading at %d: %+v", i, reading)
		}
		seen[reading.Key] = true
	}
}

func TestSmartCandidatesUseTheActualBlockInventory(t *testing.T) {
	root := t.TempDir()
	block := filepath.Join(root, "sys/block")
	if err := os.MkdirAll(block, 0o755); err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"sda", "sdb", "sdc", "sdd", "sde", "sdf", "sdg", "sdh", "nvme0n1", "nvme1n1", "loop0", "sr0", "md0"} {
		if err := os.Mkdir(filepath.Join(block, name), 0o755); err != nil {
			t.Fatal(err)
		}
	}
	// Kernel partitions live below their parent disk, not in /sys/block itself.
	if err := os.Mkdir(filepath.Join(block, "sda", "sda1"), 0o755); err != nil {
		t.Fatal(err)
	}

	got := newCollector(&Config{HostRoot: root}).smartCandidates()
	if len(got) != 10 {
		t.Fatalf("got %d candidates %v, want 8 SATA + 2 NVMe namespaces", len(got), got)
	}
	wantFirst, wantLast := "nvme0n1", "sdh"
	if got[0] != wantFirst || got[len(got)-1] != wantLast {
		t.Fatalf("unexpected sorted block inventory: %v", got)
	}
	for _, name := range got {
		if name == "sda1" || name == "loop0" || name == "sr0" || name == "md0" {
			t.Fatalf("partition or unrelated block device %q was included", name)
		}
	}
}
