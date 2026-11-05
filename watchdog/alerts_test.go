package main

import (
	"strings"
	"testing"
	"time"
)

func evaluateTemperatureAlerts(t *testing.T, temps []TempReading) []Alert {
	t.Helper()
	cfg := &Config{TempCPUWarn: 90, TempCPUCrit: 100, TempDimmWarn: 60, TempDimmCrit: 70, TempNVMeWarn: 70, TempNVMeCrit: 85, RebootWindow: 30 * time.Minute}
	return evaluateAlerts(cfg, newStateStore(t.TempDir()), &Snapshot{Temps: temps})
}

func TestTemperatureAlertsGroupCPUCoresIntoOneAlert(t *testing.T) {
	alerts := evaluateTemperatureAlerts(t, []TempReading{
		{Key: "cpu", Label: "CPU", C: 51, Warn: 90, Crit: 100, Category: "cpu_package"},
		{Key: "cpu_core_0", Label: "Core 0", C: 49, Warn: 90, Crit: 100, Category: "cpu_core"},
		{Key: "cpu_core_8", Label: "Core 8", C: 96, Warn: 90, Crit: 100, Category: "cpu_core"},
		{Key: "cpu_core_12", Label: "Core 12", C: 95, Warn: 90, Crit: 100, Category: "cpu_core"},
	})
	if len(alerts) != 1 {
		t.Fatalf("got %d alerts, want one CPU alert: %+v", len(alerts), alerts)
	}
	if alerts[0].ID != "temp_cpu" || alerts[0].Severity != "warning" || !strings.Contains(alerts[0].Title, "Core 8") {
		t.Fatalf("unexpected grouped CPU alert: %+v", alerts[0])
	}

	alerts = evaluateTemperatureAlerts(t, []TempReading{
		{Key: "cpu", Label: "CPU", C: 101, Warn: 90, Crit: 100, Category: "cpu_package"},
		{Key: "cpu_core_0", Label: "Core 0", C: 102, Warn: 90, Crit: 100, Category: "cpu_core"},
	})
	if len(alerts) != 1 || alerts[0].Severity != "critical" || !strings.Contains(alerts[0].Title, "Core 0") {
		t.Fatalf("hottest critical core did not drive the CPU alert: %+v", alerts)
	}
}

func TestTemperatureAlertsGroupNVMeSensorsPerController(t *testing.T) {
	alerts := evaluateTemperatureAlerts(t, []TempReading{
		{Key: "nvme0", Label: "NVMe 0 Composite", C: 72, Warn: 70, Crit: 85, Category: "nvme", Device: "/dev/nvme0"},
		{Key: "nvme0_sensor_1", Label: "NVMe 0 Sensor 1", C: 86, Warn: 70, Crit: 85, Category: "nvme", Device: "/dev/nvme0"},
		{Key: "nvme1", Label: "NVMe 1 Composite", C: 71, Warn: 70, Crit: 85, Category: "nvme", Device: "/dev/nvme1"},
	})
	if len(alerts) != 2 {
		t.Fatalf("got %d alerts, want one per NVMe controller: %+v", len(alerts), alerts)
	}
	if alerts[0].ID != "temp_nvme0" || alerts[0].Severity != "critical" || alerts[1].ID != "temp_nvme1" || alerts[1].Severity != "warning" {
		t.Fatalf("unexpected NVMe alerts: %+v", alerts)
	}
}

func TestHardwareTemperatureAlarmsUseConfiguredAlertLifecycle(t *testing.T) {
	alerts := evaluateTemperatureAlerts(t, []TempReading{
		{Key: "dimm0", Label: "DIMM 0", C: 56, Warn: 60, Crit: 70, Category: "dimm", Alarms: []string{"max"}},
	})
	if len(alerts) != 1 || alerts[0].ID != "temp_dimm0" || alerts[0].Severity != "warning" || !strings.Contains(alerts[0].Detail, "max temperature alarm") {
		t.Fatalf("max alarm not surfaced: %+v", alerts)
	}

	alerts = evaluateTemperatureAlerts(t, []TempReading{
		{Key: "cpu", Label: "CPU", C: 70, Warn: 90, Crit: 100, Category: "cpu_package"},
		{Key: "cpu_core_0", Label: "Core 0", C: 70, Warn: 90, Crit: 100, Category: "cpu_core", Alarms: []string{"crit"}},
	})
	if len(alerts) != 1 || alerts[0].ID != "temp_cpu" || alerts[0].Severity != "critical" || !strings.Contains(alerts[0].Title, "hardware temperature alarm") {
		t.Fatalf("critical hardware alarm not surfaced: %+v", alerts)
	}
}

func TestContainerAlertsOnlyCoverExplicitExpectedNames(t *testing.T) {
	containers := []ContainerInfo{
		{Name: "app-one", State: "running"},
		{Name: "app-two", State: "exited", Status: "Exited (0)"},
	}
	state := newStateStore(t.TempDir())
	cfg := &Config{RebootWindow: 30 * time.Minute}
	if alerts := evaluateAlerts(cfg, state, &Snapshot{Containers: containers}); len(alerts) != 0 {
		t.Fatalf("all containers are informational by default; unexpected alerts: %+v", alerts)
	}

	cfg.ExpectedContainers = []string{"app-two", "another-service"}
	alerts := evaluateAlerts(cfg, state, &Snapshot{Containers: containers})
	if len(alerts) != 2 || alerts[0].ID != "container_another-service" || alerts[1].ID != "container_app-two" {
		t.Fatalf("expected only the stopped and explicitly missing containers to alert: %+v", alerts)
	}
}

func TestTemperatureAlertsIgnoreCoolSensorsWithoutAlarms(t *testing.T) {
	alerts := evaluateTemperatureAlerts(t, []TempReading{
		{Key: "cpu", Label: "CPU", C: 50, Warn: 90, Crit: 100, Category: "cpu_package"},
		{Key: "unknown", Label: "Ambient", C: 20, Category: "other"},
	})
	if len(alerts) != 0 {
		t.Fatalf("unexpected alerts: %+v", alerts)
	}
}
