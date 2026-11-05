package main

import "time"

type Snapshot struct {
	TS           time.Time            `json:"ts"`
	Host         HostInfo             `json:"host"`
	CPU          CPUInfo              `json:"cpu"`
	Memory       MemoryInfo           `json:"memory"`
	Pressure     PressureInfo         `json:"pressure"`
	Pool         PoolInfo             `json:"pool"`
	Btrfs        BtrfsInfo            `json:"btrfs"`
	Mounts       []MountInfo          `json:"mounts"`
	Temps        []TempReading        `json:"temps"`
	ThermalZones []ThermalZoneReading `json:"thermal_zones"`
	Fans         []FanReading         `json:"fans"`
	RAID         []RAIDArray          `json:"raid"`
	Disks        []DiskInfo           `json:"disks"`
	SMART        []SMARTInfo          `json:"smart"`
	Net          []NetIface           `json:"net"`
	Containers   []ContainerInfo      `json:"containers"`
	Docker       DockerInfo           `json:"docker"`
	Processes    []ProcInfo           `json:"processes"`
	Logs         []LogSummary         `json:"logs"`
	Power        PowerInfo            `json:"power"`
	GPU          []GPUInfo            `json:"gpu"`
	Bcache       BcacheInfo           `json:"bcache"`
	NetStats     NetStatsInfo         `json:"net_stats"`
	Supplies     []PowerSupply        `json:"power_supplies"`
	Kernel       KernelInfo           `json:"kernel"`
	System       SystemInfo           `json:"system"`
	EDAC         EDACInfo             `json:"edac"`
	Journal      []JournalEntry       `json:"journal"`
	Dmesg        []string             `json:"dmesg"`
	Subvols      int                  `json:"subvolumes"`
	Alerts       []Alert              `json:"alerts"`
	Watchdog     WatchdogInfo         `json:"watchdog"`
}

type KernelInfo struct {
	Version     string `json:"version"`
	Cmdline     string `json:"cmdline"`
	ModuleCount int    `json:"module_count"`
}

type PowerInfo struct {
	PackageWatts float64            `json:"package_watts"`
	Parts        map[string]float64 `json:"parts"`
	EnergyUJ     uint64             `json:"energy_uj"`
}

type GPUInfo struct {
	Card    string   `json:"card"`
	FreqMHz int64    `json:"freq_mhz"`
	MinMHz  int64    `json:"min_mhz"`
	MaxMHz  int64    `json:"max_mhz"`
	RP0MHz  int64    `json:"rp0_mhz"`
	RC6Ms   int64    `json:"rc6_ms"`
	Engines []string `json:"engines"`
}

type BcacheInfo struct {
	Present           bool    `json:"present"`
	CacheMode         string  `json:"cache_mode"`
	State             string  `json:"state"`
	CacheHits         int64   `json:"cache_hits"`
	CacheMisses       int64   `json:"cache_misses"`
	BypassHits        int64   `json:"bypass_hits"`
	BypassMisses      int64   `json:"bypass_misses"`
	CacheBypassHits   int64   `json:"cache_bypass_hits"`
	CacheBypassMisses int64   `json:"cache_bypass_misses"`
	HitRatio          float64 `json:"hit_ratio"`
	AvailablePercent  float64 `json:"available_percent"`
}

type NetStatsInfo struct {
	TCPInUse        int     `json:"tcp_in_use"`
	TCPTimeWait     int     `json:"tcp_time_wait"`
	TCPAlloc        int     `json:"tcp_alloc"`
	SocketsUsed     int     `json:"sockets_used"`
	ActiveOpens     uint64  `json:"active_opens"`
	PassiveOpens    uint64  `json:"passive_opens"`
	CurrEstab       uint64  `json:"curr_estab"`
	InSegs          uint64  `json:"in_segs"`
	OutSegs         uint64  `json:"out_segs"`
	RetransSegs     uint64  `json:"retrans_segs"`
	InErrs          uint64  `json:"in_errs"`
	OutRsts         uint64  `json:"out_rsts"`
	RetransPct      float64 `json:"retrans_pct"`
	ListenOverflows uint64  `json:"listen_overflows"`
	ListenDrops     uint64  `json:"listen_drops"`
	ConntrackUsed   int     `json:"conntrack_used"`
	ConntrackMax    int     `json:"conntrack_max"`
	ARPEntries      int     `json:"arp_entries"`
	Routes          int     `json:"routes"`
}

type PowerSupply struct {
	Name   string `json:"name"`
	Type   string `json:"type"`
	Status string `json:"status"`
	Online string `json:"online"`
}

type HostInfo struct {
	Hostname  string  `json:"hostname"`
	Kernel    string  `json:"kernel"`
	OS        string  `json:"os"`
	UptimeSec float64 `json:"uptime_s"`
	BootID    string  `json:"boot_id"`
	Load1     float64 `json:"load1"`
	Load5     float64 `json:"load5"`
	Load15    float64 `json:"load15"`
	Procs     int     `json:"procs"`
	Threads   int     `json:"threads"`
	CPUModel  string  `json:"cpu_model"`
	CPUCores  int     `json:"cpu_cores"`
}

type CPUInfo struct {
	UsagePct      float64      `json:"usage_pct"`
	PerCore       []float64    `json:"per_core"`
	UserPct       float64      `json:"user_pct"`
	SysPct        float64      `json:"sys_pct"`
	IOWaitPct     float64      `json:"iowait_pct"`
	IrqPct        float64      `json:"irq_pct"`
	StealPct      float64      `json:"steal_pct"`
	IdlePct       float64      `json:"idle_pct"`
	FreqMHz       float64      `json:"freq_mhz"`
	Cores         int          `json:"cores"`
	Threads       int          `json:"threads"`
	Governor      string       `json:"governor"`
	MaxFreqMHz    int64        `json:"max_freq_mhz"`
	PerCoreDetail []CPUCore    `json:"per_core_detail"`
	CStates       []CState     `json:"c_states"`
	Throttle      ThrottleInfo `json:"throttle"`
}

type CPUCore struct {
	ID      int     `json:"id"`
	Usage   float64 `json:"usage"`
	FreqMHz int64   `json:"freq_mhz"`
}

type CState struct {
	Name    string `json:"name"`
	Count   uint64 `json:"count"`
	TimeMs  uint64 `json:"time_ms"`
	Latency int64  `json:"latency_us"`
}

type ThrottleInfo struct {
	PackageCount   int64 `json:"package_count"`
	PackageTotalMs int64 `json:"package_total_ms"`
	CoreCount      int64 `json:"core_count"`
	CoreTotalMs    int64 `json:"core_total_ms"`
}

type SystemInfo struct {
	Ctxt         uint64   `json:"ctxt"`
	Processes    uint64   `json:"processes"`
	ProcsRunning int      `json:"procs_running"`
	ProcsBlocked int      `json:"procs_blocked"`
	FailedUnits  []string `json:"failed_units"`
	NTPSynced    bool     `json:"ntp_synced"`
	Timezone     string   `json:"timezone"`
}

type EDACInfo struct {
	Corrected   int64 `json:"corrected"`
	Uncorrected int64 `json:"uncorrected"`
}

type JournalEntry struct {
	TS       string `json:"ts"`
	Unit     string `json:"unit"`
	Priority int    `json:"priority"`
	Message  string `json:"message"`
}

type MemoryInfo struct {
	Total        uint64  `json:"total"`
	Free         uint64  `json:"free"`
	Available    uint64  `json:"available"`
	Used         uint64  `json:"used"`
	UsedPct      float64 `json:"used_pct"`
	Buffers      uint64  `json:"buffers"`
	Cached       uint64  `json:"cached"`
	Dirty        uint64  `json:"dirty"`
	Writeback    uint64  `json:"writeback"`
	Slab         uint64  `json:"slab"`
	SReclaimable uint64  `json:"sreclaimable"`
	SwapTotal    uint64  `json:"swap_total"`
	SwapFree     uint64  `json:"swap_free"`
	SwapUsed     uint64  `json:"swap_used"`
	SwapPct      float64 `json:"swap_pct"`
	OOMKills     int64   `json:"oom_kills"`
	PageFaults   int64   `json:"page_faults"`
	MajorFaults  int64   `json:"major_faults"`
}

type PressureLine struct {
	Some10  float64 `json:"some10"`
	Some60  float64 `json:"some60"`
	Some300 float64 `json:"some300"`
	Full10  float64 `json:"full10"`
	Full60  float64 `json:"full60"`
	Full300 float64 `json:"full300"`
}

type PressureInfo struct {
	CPU PressureLine `json:"cpu"`
	Mem PressureLine `json:"memory"`
	IO  PressureLine `json:"io"`
}

type PoolInfo struct {
	Path            string           `json:"path"`
	HostPath        string           `json:"host_path"`
	Device          string           `json:"device"`
	ReadOnly        bool             `json:"read_only"`
	ExpectedRW      bool             `json:"expected_rw"`
	TotalBytes      uint64           `json:"total_bytes"`
	UsedBytes       uint64           `json:"used_bytes"`
	FreeBytes       uint64           `json:"free_bytes"`
	UsedPct         float64          `json:"used_pct"`
	DeviceStats     map[string]int64 `json:"device_stats"`
	CorruptionTotal int64            `json:"corruption_total"`
	Status          string           `json:"status"`
	Error           string           `json:"error,omitempty"`
}

type ChunkUsage struct {
	Profile string `json:"profile"`
	Total   uint64 `json:"total"`
	Used    uint64 `json:"used"`
}

type ScrubInfo struct {
	Status              string  `json:"status"`
	Started             string  `json:"started"`
	DurationSec         int64   `json:"duration_s"`
	DataBytesScrubbed   uint64  `json:"data_bytes_scrubbed"`
	TreeBytesScrubbed   uint64  `json:"tree_bytes_scrubbed"`
	ReadErrors          int64   `json:"read_errors"`
	CsumErrors          int64   `json:"csum_errors"`
	VerifyErrors        int64   `json:"verify_errors"`
	UncorrectableErrors int64   `json:"uncorrectable_errors"`
	CorrectedErrors     int64   `json:"corrected_errors"`
	ProgressPct         float64 `json:"progress_pct"`
}

type BtrfsInfo struct {
	DeviceStats map[string]int64      `json:"device_stats"`
	DF          map[string]ChunkUsage `json:"df"`
	Scrub       ScrubInfo             `json:"scrub"`
	Error       string                `json:"error,omitempty"`
}

type MountInfo struct {
	Device  string  `json:"device"`
	Target  string  `json:"target"`
	FSType  string  `json:"fstype"`
	RO      bool    `json:"ro"`
	Options string  `json:"options"`
	Total   uint64  `json:"total"`
	Used    uint64  `json:"used"`
	Free    uint64  `json:"free"`
	UsedPct float64 `json:"used_pct"`
}

type TemperatureLimits struct {
	Min       *float64 `json:"min_c,omitempty"`
	Max       *float64 `json:"max_c,omitempty"`
	LCrit     *float64 `json:"lcrit_c,omitempty"`
	Crit      *float64 `json:"crit_c,omitempty"`
	Emergency *float64 `json:"emergency_c,omitempty"`
}

type TempReading struct {
	Key      string             `json:"key"`
	Label    string             `json:"label"`
	C        float64            `json:"c"`
	Warn     float64            `json:"warn,omitempty"`
	Crit     float64            `json:"crit,omitempty"`
	Category string             `json:"category,omitempty"`
	Source   string             `json:"source,omitempty"`
	Chip     string             `json:"chip,omitempty"`
	Sensor   string             `json:"sensor,omitempty"`
	Device   string             `json:"device,omitempty"`
	CoreType string             `json:"core_type,omitempty"`
	CPUs     []int              `json:"cpus,omitempty"`
	Limits   *TemperatureLimits `json:"limits,omitempty"`
	Alarms   []string           `json:"alarms,omitempty"`
}

type ThermalTrip struct {
	Index int     `json:"index"`
	Type  string  `json:"type"`
	TempC float64 `json:"temp_c"`
	HystC float64 `json:"hyst_c"`
}

type ThermalZoneReading struct {
	Key    string        `json:"key"`
	Label  string        `json:"label"`
	C      float64       `json:"c"`
	Mode   string        `json:"mode,omitempty"`
	Policy string        `json:"policy,omitempty"`
	Trips  []ThermalTrip `json:"trips,omitempty"`
}

type FanReading struct {
	Key   string `json:"key"`
	Label string `json:"label"`
	RPM   int    `json:"rpm"`
	PWM   int    `json:"pwm,omitempty"`
}

type RAIDArray struct {
	Name     string `json:"name"`
	Level    string `json:"level"`
	State    string `json:"state"`
	Members  string `json:"members"`
	Degraded bool   `json:"degraded"`
	Sync     string `json:"sync,omitempty"`
	Detail   string `json:"detail"`
}

type DiskInfo struct {
	Name       string  `json:"name"`
	Model      string  `json:"model"`
	Serial     string  `json:"serial"`
	SizeBytes  uint64  `json:"size_bytes"`
	Rotational bool    `json:"rotational"`
	ReadBytes  uint64  `json:"read_bytes"`
	WriteBytes uint64  `json:"write_bytes"`
	ReadRate   float64 `json:"read_rate"`
	WriteRate  float64 `json:"write_rate"`
	ReadIOPS   float64 `json:"read_iops"`
	WriteIOPS  float64 `json:"write_iops"`
	UtilPct    float64 `json:"util_pct"`
	IOMs       float64 `json:"io_ms"`
}

type SMARTInfo struct {
	Device           string           `json:"device"`
	Type             string           `json:"type"`
	Model            string           `json:"model"`
	Serial           string           `json:"serial"`
	Health           string           `json:"health"`
	Temperature      float64          `json:"temperature"`
	TemperatureMin   *float64         `json:"temperature_min_c,omitempty"`
	TemperatureMax   *float64         `json:"temperature_max_c,omitempty"`
	PowerOnHours     int64            `json:"power_on_hours"`
	Reallocated      int64            `json:"reallocated"`
	Pending          int64            `json:"pending"`
	Uncorrectable    int64            `json:"uncorrectable"`
	CRCErrors        int64            `json:"crc_errors"`
	PercentageUsed   int64            `json:"percentage_used"`
	MediaErrors      int64            `json:"media_errors"`
	DataUnitsRead    uint64           `json:"data_units_read"`
	DataUnitsWritten uint64           `json:"data_units_written"`
	UnsafeShutdowns  int64            `json:"unsafe_shutdowns"`
	AvailableSpare   int64            `json:"available_spare"`
	Attributes       map[string]int64 `json:"attributes,omitempty"`
	Table            []SMARTAttribute `json:"table,omitempty"`
	Error            string           `json:"error,omitempty"`
}

type SMARTAttribute struct {
	ID       int    `json:"id"`
	Name     string `json:"name"`
	Value    int    `json:"value"`
	Worst    int    `json:"worst"`
	Thresh   int    `json:"thresh"`
	RawValue int64  `json:"raw_value"`
	RawText  string `json:"raw_text"`
	Failed   string `json:"failed,omitempty"`
}

type NetIface struct {
	Name      string  `json:"name"`
	State     string  `json:"state"`
	SpeedMbps int64   `json:"speed_mbps"`
	RxBytes   uint64  `json:"rx_bytes"`
	TxBytes   uint64  `json:"tx_bytes"`
	RxRate    float64 `json:"rx_rate"`
	TxRate    float64 `json:"tx_rate"`
	RxPackets uint64  `json:"rx_packets"`
	TxPackets uint64  `json:"tx_packets"`
	RxErrs    uint64  `json:"rx_errs"`
	TxErrs    uint64  `json:"tx_errs"`
	RxDrops   uint64  `json:"rx_drops"`
	TxDrops   uint64  `json:"tx_drops"`
	MAC       string  `json:"mac"`
	MTU       int     `json:"mtu"`
	Duplex    string  `json:"duplex"`
	Virtual   bool    `json:"virtual"`
}

type ContainerInfo struct {
	Name         string   `json:"name"`
	ID           string   `json:"id"`
	Image        string   `json:"image"`
	State        string   `json:"state"`
	Status       string   `json:"status"`
	Health       string   `json:"health"`
	Created      string   `json:"created"`
	RestartCount int      `json:"restart_count"`
	CPUPct       float64  `json:"cpu_pct"`
	MemUsage     uint64   `json:"mem_usage"`
	MemLimit     uint64   `json:"mem_limit"`
	MemPct       float64  `json:"mem_pct"`
	NetRx        uint64   `json:"net_rx"`
	NetTx        uint64   `json:"net_tx"`
	BlockRead    uint64   `json:"block_read"`
	BlockWrite   uint64   `json:"block_write"`
	Ports        []string `json:"ports"`
}

type DockerInfo struct {
	Version         string `json:"version"`
	APIVersion      string `json:"api_version"`
	Driver          string `json:"driver"`
	StorageDriver   string `json:"storage_driver"`
	Running         int    `json:"running"`
	Paused          int    `json:"paused"`
	Stopped         int    `json:"stopped"`
	Images          int    `json:"images"`
	NCPU            int    `json:"ncpu"`
	MemTotal        uint64 `json:"mem_total"`
	OperatingSystem string `json:"operating_system"`
	Error           string `json:"error,omitempty"`
}

type ProcInfo struct {
	PID     int     `json:"pid"`
	Name    string  `json:"name"`
	State   string  `json:"state"`
	CPUPct  float64 `json:"cpu_pct"`
	MemPct  float64 `json:"mem_pct"`
	RSS     uint64  `json:"rss"`
	Threads int     `json:"threads"`
	Cmdline string  `json:"cmdline"`
}

type LogSummary struct {
	Source    string         `json:"source"`
	Path      string         `json:"path"`
	Size      int64          `json:"size"`
	ModTime   time.Time      `json:"mod_time"`
	Matches   map[string]int `json:"matches"`
	LastMatch string         `json:"last_match"`
	Error     string         `json:"error,omitempty"`
}

type Alert struct {
	ID       string    `json:"id"`
	Severity string    `json:"severity"`
	Title    string    `json:"title"`
	Detail   string    `json:"detail"`
	Since    time.Time `json:"since"`
	Active   bool      `json:"active"`
}

type WatchdogInfo struct {
	Version      string `json:"version"`
	IntervalSecs int    `json:"interval_s"`
	// RebootEnabled controls the automatic recovery loop, not a user's
	// explicitly confirmed manual reboot through the AllowActions gate.
	RebootEnabled  bool `json:"reboot_enabled"`
	ActionsEnabled bool `json:"actions_enabled"`
	RebootDelaySec int  `json:"reboot_delay_s"`
	AlertCount     int  `json:"alert_count"`
}

type Event struct {
	TS    time.Time `json:"ts"`
	Kind  string    `json:"kind"`
	Alert Alert     `json:"alert"`
}
