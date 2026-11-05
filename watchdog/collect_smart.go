package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

type smartAttr struct {
	ID         int    `json:"id"`
	Name       string `json:"name"`
	Value      int    `json:"value"`
	Worst      int    `json:"worst"`
	Thresh     int    `json:"thresh"`
	WhenFailed string `json:"when_failed"`
	Raw        struct {
		Value  int64  `json:"value"`
		String string `json:"string"`
	} `json:"raw"`
}

type smartJSON struct {
	Device struct {
		Name string `json:"name"`
		Type string `json:"type"`
	} `json:"device"`
	ModelName    string `json:"model_name"`
	SerialNumber string `json:"serial_number"`
	SmartStatus  struct {
		Passed *bool `json:"passed"`
	} `json:"smart_status"`
	Smartctl struct {
		ExitStatus int `json:"exit_status"`
		Messages   []struct {
			String   string `json:"string"`
			Severity string `json:"severity"`
		} `json:"messages"`
	} `json:"smartctl"`
	Temperature struct {
		Current int `json:"current"`
	} `json:"temperature"`
	PowerOnTime struct {
		Hours int64 `json:"hours"`
	} `json:"power_on_time"`
	ATASmartAttributes struct {
		Table []smartAttr `json:"table"`
	} `json:"ata_smart_attributes"`
	NVMESmart struct {
		Temperature      int    `json:"temperature"`
		AvailableSpare   int64  `json:"available_spare"`
		PercentageUsed   int64  `json:"percentage_used"`
		DataUnitsRead    uint64 `json:"data_units_read"`
		DataUnitsWritten uint64 `json:"data_units_written"`
		MediaErrors      int64  `json:"media_errors"`
		UnsafeShutdowns  int64  `json:"unsafe_shutdowns"`
	} `json:"nvme_smart_health_information_log"`
}

func (c *Collector) smartCandidates() []string {
	entries, err := readDirNames(c.hostPath("sys/block"))
	if err != nil {
		return nil
	}
	var out []string
	for _, name := range entries {
		switch {
		case strings.HasPrefix(name, "sd"), strings.HasPrefix(name, "nvme"):
			out = append(out, name)
		}
	}
	sort.Strings(out)
	return out
}

func (c *Collector) collectSMART() []SMARTInfo {
	c.smartMu.Lock()
	defer c.smartMu.Unlock()

	if time.Since(c.smartAt) < c.cfg.SmartInterval && c.smartCache != nil {
		out := make([]SMARTInfo, len(c.smartCache))
		copy(out, c.smartCache)
		return out
	}

	var out []SMARTInfo
	for _, name := range c.smartCandidates() {
		out = append(out, c.readSMART(name))
	}
	c.smartCache = out
	c.smartAt = time.Now()
	return out
}

func (c *Collector) readSMART(name string) SMARTInfo {
	info := SMARTInfo{Device: "/dev/" + name}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	devPath := c.hostPath("dev/" + name)
	args := []string{"-j"}
	switch {
	case strings.HasPrefix(name, "nvme"):
		args = append(args, "-d", "nvme")
	case strings.HasPrefix(name, "sd"):
		args = append(args, "-d", "ata")
	}
	args = append(args, "-a", devPath)
	out, err := exec.CommandContext(ctx, "smartctl", args...).Output()
	if err != nil && len(out) == 0 {
		info.Error = err.Error()
		return info
	}
	var raw smartJSON
	if err := json.Unmarshal(out, &raw); err != nil {
		info.Error = "parse: " + err.Error()
		return info
	}
	info.Type = raw.Device.Type
	info.Model = raw.ModelName
	info.Serial = raw.SerialNumber

	switch {
	case raw.SmartStatus.Passed == nil:
		info.Health = "UNKNOWN"
	case *raw.SmartStatus.Passed:
		info.Health = "PASSED"
	default:
		info.Health = "FAILED"
	}
	if raw.Smartctl.ExitStatus&8 != 0 {
		info.Health = "FAILED"
	}
	if raw.ModelName == "" && info.Health == "UNKNOWN" {
		info.Error = "smartctl could not identify device"
	}
	if raw.Temperature.Current > 0 {
		info.Temperature = float64(raw.Temperature.Current)
	} else if raw.NVMESmart.Temperature > 0 {
		info.Temperature = float64(raw.NVMESmart.Temperature)
	}
	info.PowerOnHours = raw.PowerOnTime.Hours

	if n := raw.NVMESmart; n.PercentageUsed > 0 || n.DataUnitsRead > 0 || n.UnsafeShutdowns > 0 {
		info.PercentageUsed = n.PercentageUsed
		info.MediaErrors = n.MediaErrors
		info.DataUnitsRead = n.DataUnitsRead
		info.DataUnitsWritten = n.DataUnitsWritten
		info.UnsafeShutdowns = n.UnsafeShutdowns
		info.AvailableSpare = n.AvailableSpare
	}

	if len(raw.ATASmartAttributes.Table) > 0 {
		attrs := map[string]int64{}
		for _, a := range raw.ATASmartAttributes.Table {
			attrs[a.Name] = a.Raw.Value
			info.Table = append(info.Table, SMARTAttribute{
				ID: a.ID, Name: a.Name, Value: a.Value, Worst: a.Worst,
				Thresh: a.Thresh, RawValue: a.Raw.Value, RawText: a.Raw.String,
				Failed: a.WhenFailed,
			})
			if info.TemperatureMin == nil && (a.ID == 190 || a.ID == 194) {
				if min, max, ok := parseDriveTemperatureRange(a.Raw.String); ok {
					info.TemperatureMin, info.TemperatureMax = &min, &max
				}
			}
			switch a.ID {
			case 5:
				info.Reallocated = a.Raw.Value
			case 197:
				info.Pending = a.Raw.Value
			case 198:
				info.Uncorrectable = a.Raw.Value
			case 199:
				info.CRCErrors = a.Raw.Value
			}
			if a.ID == 5 || a.ID == 197 || a.ID == 198 || a.ID == 199 || a.ID == 194 || a.ID == 9 {
				attrs[fmt.Sprintf("%d_%s", a.ID, a.Name)] = a.Raw.Value
			}
		}
		info.Attributes = attrs
	}
	return info
}

var driveTemperatureRangeRe = regexp.MustCompile(`^\s*-?[0-9]+(?:\.[0-9]+)?\s*\(Min/Max\s+(-?[0-9]+(?:\.[0-9]+)?)/(-?[0-9]+(?:\.[0-9]+)?)\)\s*$`)

func parseDriveTemperatureRange(raw string) (float64, float64, bool) {
	match := driveTemperatureRangeRe.FindStringSubmatch(raw)
	if match == nil {
		return 0, 0, false
	}
	min, minErr := strconv.ParseFloat(match[1], 64)
	max, maxErr := strconv.ParseFloat(match[2], 64)
	if minErr != nil || maxErr != nil || min < -100 || max > 200 || min > max {
		return 0, 0, false
	}
	return min, max, true
}

func smartTemperatureReadings(smart []SMARTInfo, cfg *Config) []TempReading {
	var out []TempReading
	for _, sm := range smart {
		if !strings.HasPrefix(sm.Device, "/dev/sd") || sm.Temperature <= 0 {
			continue
		}
		label := sm.Model
		if label == "" {
			label = filepath.Base(sm.Device)
		}
		out = append(out, TempReading{
			Key: "hdd_" + filepath.Base(sm.Device), Label: label, C: sm.Temperature,
			Warn: cfg.TempHDDWarn, Crit: cfg.TempHDDCrit, Category: "hdd", Source: "smart",
			Chip: sm.Type, Sensor: "temperature", Device: sm.Device,
		})
	}
	return out
}

func readDirNames(dir string) ([]string, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}
	out := make([]string, 0, len(entries))
	for _, e := range entries {
		out = append(out, e.Name())
	}
	return out, nil
}
