package main

import "testing"

func TestAlertEventsHaveCreationTimestamps(t *testing.T) {
	store := newStateStore(t.TempDir())
	_, fired := store.ApplyAlerts([]Alert{{ID: "temp_cpu", Title: "CPU is hot"}})
	if len(fired) != 1 || fired[0].Kind != "fired" || fired[0].TS.IsZero() {
		t.Fatalf("invalid fired event: %+v", fired)
	}
	_, resolved := store.ApplyAlerts(nil)
	if len(resolved) != 1 || resolved[0].Kind != "resolved" || resolved[0].TS.IsZero() || resolved[0].Alert.Since.IsZero() {
		t.Fatalf("invalid resolved event: %+v", resolved)
	}
}
