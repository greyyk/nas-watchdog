package main

import (
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	Listen        string
	HostRoot      string
	PoolPath      string
	PoolHostPath  string
	Interval      time.Duration
	DataDir       string
	SmartInterval time.Duration
	LogInterval   time.Duration

	NtfyURL           string
	NtfyTopic         string
	NtfyToken         string
	WebhookURL        string
	HeartbeatURL      string
	HeartbeatInterval time.Duration

	AllowReboot    bool
	AllowActions   bool
	RebootDelay    time.Duration
	RebootCooldown time.Duration
	RebootWindow   time.Duration
	RebootCommand  string

	TempDimmWarn  float64
	TempDimmCrit  float64
	TempCPUWarn   float64
	TempCPUCrit   float64
	TempNVMeWarn  float64
	TempNVMeCrit  float64
	TempHDDWarn   float64
	TempHDDCrit   float64
	TempBoardWarn float64
	TempBoardCrit float64
	DiskWarn      float64
	DiskCrit      float64

	ExpectedContainers []string
	APIToken           string
}

func loadConfig() *Config {
	return &Config{
		Listen:        env("WD_LISTEN", ":8080"),
		HostRoot:      env("WD_HOST_ROOT", "/host"),
		PoolPath:      env("WD_POOL_PATH", "/pool"),
		PoolHostPath:  env("WD_POOL_HOST_PATH", "/zettos/pool/1"),
		Interval:      envDur("WD_INTERVAL", 30*time.Second),
		DataDir:       env("WD_DATA_DIR", "/data"),
		SmartInterval: envDur("WD_SMART_INTERVAL", 10*time.Minute),
		LogInterval:   envDur("WD_LOG_INTERVAL", 2*time.Minute),

		NtfyURL:           env("WD_NTFY_URL", ""),
		NtfyTopic:         env("WD_NTFY_TOPIC", ""),
		NtfyToken:         env("WD_NTFY_TOKEN", ""),
		WebhookURL:        env("WD_WEBHOOK_URL", ""),
		HeartbeatURL:      env("WD_HEARTBEAT_URL", ""),
		HeartbeatInterval: envDur("WD_HEARTBEAT_INTERVAL", 60*time.Second),

		AllowReboot:    envBool("WD_ALLOW_REBOOT", false),
		AllowActions:   envBool("WD_ALLOW_ACTIONS", false),
		RebootDelay:    envDur("WD_REBOOT_DELAY", 300*time.Second),
		RebootCooldown: envDur("WD_REBOOT_COOLDOWN", 45*time.Minute),
		RebootWindow:   envDur("WD_REBOOT_WINDOW", 30*time.Minute),
		RebootCommand: env("WD_REBOOT_COMMAND",
			"nsenter -t 1 -m -u -i -n -p -- systemctl reboot"),

		TempDimmWarn:  envFloat("WD_TEMP_DIMM_WARN", 60),
		TempDimmCrit:  envFloat("WD_TEMP_DIMM_CRIT", 70),
		TempCPUWarn:   envFloat("WD_TEMP_CPU_WARN", 90),
		TempCPUCrit:   envFloat("WD_TEMP_CPU_CRIT", 100),
		TempNVMeWarn:  envFloat("WD_TEMP_NVME_WARN", 70),
		TempNVMeCrit:  envFloat("WD_TEMP_NVME_CRIT", 85),
		TempHDDWarn:   envFloat("WD_TEMP_HDD_WARN", 50),
		TempHDDCrit:   envFloat("WD_TEMP_HDD_CRIT", 60),
		TempBoardWarn: envFloat("WD_TEMP_BOARD_WARN", 60),
		TempBoardCrit: envFloat("WD_TEMP_BOARD_CRIT", 75),
		DiskWarn:      envFloat("WD_DISK_WARN", 85),
		DiskCrit:      envFloat("WD_DISK_CRIT", 95),

		ExpectedContainers: envList("WD_EXPECTED_CONTAINERS", nil),
		APIToken:           env("WD_API_TOKEN", ""),
	}
}

func env(k, def string) string {
	if v := strings.TrimSpace(os.Getenv(k)); v != "" {
		return v
	}
	return def
}

func envDur(k string, def time.Duration) time.Duration {
	if v := strings.TrimSpace(os.Getenv(k)); v != "" {
		if d, err := time.ParseDuration(v); err == nil {
			return d
		}
	}
	return def
}

func envFloat(k string, def float64) float64 {
	if v := strings.TrimSpace(os.Getenv(k)); v != "" {
		if f, err := strconv.ParseFloat(v, 64); err == nil {
			return f
		}
	}
	return def
}

func envBool(k string, def bool) bool {
	if v := strings.TrimSpace(os.Getenv(k)); v != "" {
		if b, err := strconv.ParseBool(v); err == nil {
			return b
		}
	}
	return def
}

func envList(k string, def []string) []string {
	v := strings.TrimSpace(os.Getenv(k))
	if v == "" {
		return def
	}
	var out []string
	for _, p := range strings.Split(v, ",") {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}
