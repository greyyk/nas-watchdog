package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
	"time"
)

type persistedState struct {
	BootID          string               `json:"boot_id"`
	CorruptionTotal int64                `json:"corruption_total"`
	CorruptionAt    time.Time            `json:"corruption_at"`
	CorruptionInit  bool                 `json:"corruption_initialized"`
	Alerts          map[string]Alert     `json:"alerts"`
	Reboots         []time.Time          `json:"reboots"`
	LastReboot      time.Time            `json:"last_reboot"`
	PendingRebootAt time.Time            `json:"pending_reboot_at"`
	LogCounts       map[string]int       `json:"log_counts"`
	LogSeen         map[string]time.Time `json:"log_seen"`
}

type StateStore struct {
	mu   sync.Mutex
	path string
	data persistedState
}

func newStateStore(dir string) *StateStore {
	s := &StateStore{
		path: filepath.Join(dir, "state.json"),
		data: persistedState{Alerts: map[string]Alert{}},
	}
	if b, err := os.ReadFile(s.path); err == nil {
		var d persistedState
		if json.Unmarshal(b, &d) == nil {
			if d.Alerts == nil {
				d.Alerts = map[string]Alert{}
			}
			s.data = d
		}
	}
	return s
}

func (s *StateStore) saveLocked() {
	b, err := json.MarshalIndent(s.data, "", "  ")
	if err != nil {
		return
	}
	dir := filepath.Dir(s.path)
	_ = os.MkdirAll(dir, 0o755)
	tmp := s.path + ".tmp"
	if err := os.WriteFile(tmp, b, 0o644); err != nil {
		return
	}
	_ = os.Rename(tmp, s.path)
}

func (s *StateStore) BootCheck(bootID string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if bootID == "" {
		return false
	}
	if s.data.BootID == "" {
		s.data.BootID = bootID
		s.saveLocked()
		return false
	}
	if s.data.BootID == bootID {
		return false
	}
	now := time.Now()
	s.data.BootID = bootID
	s.data.Reboots = append(s.data.Reboots, now)
	s.data.LastReboot = now
	cutoff := now.Add(-24 * time.Hour)
	kept := s.data.Reboots[:0]
	for _, t := range s.data.Reboots {
		if t.After(cutoff) {
			kept = append(kept, t)
		}
	}
	s.data.Reboots = kept
	s.saveLocked()
	return true
}

func (s *StateStore) RebootCountSince(d time.Duration) int {
	s.mu.Lock()
	defer s.mu.Unlock()
	cutoff := time.Now().Add(-d)
	n := 0
	for _, t := range s.data.Reboots {
		if t.After(cutoff) {
			n++
		}
	}
	return n
}

func (s *StateStore) ObserveCorruption(total int64) time.Time {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !s.data.CorruptionInit {
		s.data.CorruptionTotal = total
		s.data.CorruptionInit = true
		s.saveLocked()
		return time.Time{}
	}
	if total != s.data.CorruptionTotal {
		if total > s.data.CorruptionTotal {
			s.data.CorruptionAt = time.Now()
		}
		s.data.CorruptionTotal = total
		s.saveLocked()
	}
	return s.data.CorruptionAt
}

func (s *StateStore) ObserveLogs(counts map[string]int) map[string]time.Time {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.data.LogCounts == nil {
		s.data.LogCounts = map[string]int{}
	}
	if s.data.LogSeen == nil {
		s.data.LogSeen = map[string]time.Time{}
	}
	// First observation establishes a baseline without alerting on history.
	firstRun := len(s.data.LogCounts) == 0 && len(s.data.LogSeen) == 0
	for k, v := range counts {
		if v > s.data.LogCounts[k] {
			if !firstRun {
				s.data.LogSeen[k] = time.Now()
			}
		}
		s.data.LogCounts[k] = v
	}
	s.saveLocked()
	if firstRun {
		return map[string]time.Time{}
	}

	out := make(map[string]time.Time, len(s.data.LogSeen))
	for k, t := range s.data.LogSeen {
		out[k] = t
	}
	return out
}

func (s *StateStore) ApplyAlerts(active []Alert) ([]Alert, []Event) {
	s.mu.Lock()
	defer s.mu.Unlock()

	prev := s.data.Alerts
	if prev == nil {
		prev = map[string]Alert{}
	}
	next := map[string]Alert{}
	var events []Event
	out := make([]Alert, 0, len(active))

	for _, a := range active {
		if old, ok := prev[a.ID]; ok {
			a.Since = old.Since
			a.Active = true
			next[a.ID] = a
			out = append(out, a)
			continue
		}
		a.Since = time.Now()
		a.Active = true
		next[a.ID] = a
		out = append(out, a)
		events = append(events, Event{TS: time.Now(), Kind: "fired", Alert: a})
	}
	for id, old := range prev {
		if _, ok := next[id]; ok {
			continue
		}
		old.Active = false
		events = append(events, Event{TS: time.Now(), Kind: "resolved", Alert: old})
	}
	s.data.Alerts = next
	s.saveLocked()
	return out, events
}

func (s *StateStore) ScheduleReboot(delay time.Duration) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.data.PendingRebootAt = time.Now().Add(delay)
	s.saveLocked()
}

func (s *StateStore) ClearReboot() {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !s.data.PendingRebootAt.IsZero() {
		s.data.PendingRebootAt = time.Time{}
		s.saveLocked()
	}
}

func (s *StateStore) RebootDue() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return !s.data.PendingRebootAt.IsZero() && time.Now().After(s.data.PendingRebootAt)
}

func (s *StateStore) RebootPending() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return !s.data.PendingRebootAt.IsZero()
}

func (s *StateStore) MarkRebootIssued() {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := time.Now()
	s.data.PendingRebootAt = time.Time{}
	s.data.LastReboot = now
	s.data.Reboots = append(s.data.Reboots, now)
	s.saveLocked()
}

func (s *StateStore) RecentReboot(d time.Duration) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return !s.data.LastReboot.IsZero() && time.Since(s.data.LastReboot) < d
}
