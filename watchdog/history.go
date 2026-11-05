package main

import (
	"strings"
	"sync"
	"time"
)

type FanHistory struct {
	RPM int `json:"rpm"`
	PWM int `json:"pwm"`
}

type Sample struct {
	TS         time.Time             `json:"ts"`
	CPU        float64               `json:"cpu"`
	MemPct     float64               `json:"mem_pct"`
	Load1      float64               `json:"load1"`
	NetRx      float64               `json:"net_rx"`
	NetTx      float64               `json:"net_tx"`
	DiskRead   float64               `json:"disk_read"`
	DiskWrite  float64               `json:"disk_write"`
	Temps      map[string]float64    `json:"temps"`
	Fans       map[string]FanHistory `json:"fans"`
	UsedPct    float64               `json:"used_pct"`
	Corruption int64                 `json:"corruption"`
	ReadOnly   bool                  `json:"read_only"`
	MemAvail   uint64                `json:"mem_available"`
}

type History struct {
	mu      sync.Mutex
	max     int
	samples []Sample
}

func newHistory(max int) *History {
	return &History{max: max}
}

func (h *History) Add(s *Snapshot) {
	h.mu.Lock()
	defer h.mu.Unlock()

	sample := Sample{
		TS:         s.TS,
		CPU:        s.CPU.UsagePct,
		MemPct:     s.Memory.UsedPct,
		Load1:      s.Host.Load1,
		Temps:      map[string]float64{},
		Fans:       map[string]FanHistory{},
		UsedPct:    s.Pool.UsedPct,
		Corruption: s.Pool.CorruptionTotal,
		ReadOnly:   s.Pool.ReadOnly,
		MemAvail:   s.Memory.Available,
	}
	for _, t := range s.Temps {
		sample.Temps[t.Key] = t.C
	}
	for _, zone := range s.ThermalZones {
		if zone.Mode != "disabled" {
			sample.Temps[zone.Key] = zone.C
		}
	}
	for _, fan := range s.Fans {
		sample.Fans[fan.Key] = FanHistory{RPM: fan.RPM, PWM: fan.PWM}
	}
	for _, n := range s.Net {
		sample.NetRx += n.RxRate
		sample.NetTx += n.TxRate
	}
	for _, d := range s.Disks {
		if strings.HasPrefix(d.Name, "md") || strings.HasPrefix(d.Name, "dm-") ||
			strings.HasPrefix(d.Name, "bcache") {
			continue
		}
		sample.DiskRead += d.ReadRate
		sample.DiskWrite += d.WriteRate
	}

	h.samples = append(h.samples, sample)
	if len(h.samples) > h.max {
		h.samples = h.samples[len(h.samples)-h.max:]
	}
}

func (h *History) Snapshot() []Sample {
	h.mu.Lock()
	defer h.mu.Unlock()
	out := make([]Sample, len(h.samples))
	copy(out, h.samples)
	return out
}
