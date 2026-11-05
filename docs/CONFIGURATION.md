# Configuration reference

Configuration lives in `watchdog/.env`, created from [`watchdog/.env.example`](../watchdog/.env.example). The file is excluded from Git and Docker build contexts. Restart/recreate the service after changes:

```sh
cd watchdog
sudo docker compose up -d
```

Durations use Go syntax such as `30s`, `10m`, or `1h`. Temperatures are degrees Celsius; disk-capacity thresholds are percentages.

## Compose and access

| Variable | Default | Purpose |
|---|---|---|
| `WD_API_TOKEN` | **No default; required by supplied Compose** | Bearer token for protected dashboard/API routes. Generate with `openssl rand -hex 32` |
| `POOL_HOST_PATH` | `/zettos/pool/1` | Actual btrfs pool mountpoint on the host. Compose mounts it at `/pool` and sets the matching collector value |
| `WATCHDOG_BIND_ADDRESS` | `0.0.0.0` | Host address on which Docker publishes the service |
| `WATCHDOG_PORT` | `8090` | Host port mapped to container port 8080 |
| `WATCHDOG_IMAGE` | unset → `nas-watchdog:local` | Optional published image URI such as `ghcr.io/greyyk/nas-watchdog:1.5.1`; use `compose pull` then `up -d --no-build` |

`WATCHDOG_BIND_ADDRESS=127.0.0.1` is suitable only when a secure local tunnel/reverse proxy supplies remote access. Android clients on other devices cannot use their own loopback address to reach the NAS.

## Collector

| Variable | Default | Purpose |
|---|---|---|
| `WD_LISTEN` | `:8080` | Address inside the container; normally leave unchanged |
| `WD_HOST_ROOT` | `/host` | Internal read-only host-root mount; normally leave unchanged |
| `WD_POOL_PATH` | `/pool` | Internal pool mount; normally leave unchanged |
| `WD_INTERVAL` | `30s` | Main sample/collection interval |
| `WD_SMART_INTERVAL` | `10m` | Per-device SMART polling interval; cached between polls |
| `WD_LOG_INTERVAL` | `2m` | Log-summary scan interval |
| `WD_DATA_DIR` | `/data` | Persistent state directory backed by the `watchdog-data` volume |

History retains at most **2,880 samples**, independent of any configured interval. It therefore spans 24 hours only at the default 30-second rate. History is in memory; restart/recreate clears it.

SMART cadence affects normalized SATA temperature history as well as SMART details. NVMe hwmon temperatures follow the main collection interval.

## Docker expectations

```dotenv
WD_EXPECTED_CONTAINERS=
```

Every API-discovered container appears automatically. This list controls **alerts only**. Add exact Docker names, separated by commas, when a missing or non-running service deserves a warning:

```dotenv
WD_EXPECTED_CONTAINERS=container-one,container-two
```

Find names with:

```sh
sudo docker ps -a --format '{{.Names}}'
```

Blank means no missing/stopped expectation alerts. Container health, resources, ports, and logs are displayed independently of this list.

## Notification delivery

| Variable | Default | Purpose |
|---|---|---|
| `WD_NTFY_URL` | blank / disabled | ntfy base URL with no trailing topic |
| `WD_NTFY_TOPIC` | blank / disabled | Topic appended to the ntfy URL. Treat shared-server topics as secrets |
| `WD_NTFY_TOKEN` | blank | Optional ntfy bearer token |
| `WD_WEBHOOK_URL` | blank / disabled | Destination for JSON `POST` alert/resolved events |
| `WD_HEARTBEAT_URL` | blank / disabled | URL pinged by HTTP GET as a dead-man’s switch |
| `WD_HEARTBEAT_INTERVAL` | `60s` | Heartbeat interval; values below 10 seconds are clamped to 10 seconds |

ntfy requires both URL and topic. Webhook fields are `kind`, `id`, `severity`, `title`, `detail`, and `since`. Neither notifier maintains a durable retry queue; initial POST/GET failures are logged and that invocation is lost. Host heartbeat monitoring should run off the NAS.

Android live alerts are configured in the app itself, not by these variables.

## Actions and automatic reboot

| Variable | Default | Purpose |
|---|---|---|
| `WD_ALLOW_ACTIONS` | `false` | Enables explicitly confirmed, authenticated Docker, scrub, and manual reboot actions |
| `WD_ALLOW_REBOOT` | `false` | Enables **automatic** recovery when the pool stays read-only |
| `WD_REBOOT_DELAY` | `300s` | Grace period before an automatic reboot |
| `WD_REBOOT_COOLDOWN` | `45m` | Suppresses another automatic reboot too soon after a host reboot |
| `WD_REBOOT_WINDOW` | `30m` | Window used for reboot-loop detection |

Keep both flags off until telemetry, access control, and pool detection have been verified. A manual reboot action is controlled by `WD_ALLOW_ACTIONS`; automatic reboot additionally needs `WD_ALLOW_REBOOT=true`.

Actions require a request body containing `confirm:true` and are restricted to known action IDs/targets. They still have real host effects. The privileged container and Docker socket are high-trust boundaries independently of these API gates.

## Alert thresholds

| Component | Warning | Critical |
|---|---:|---:|
| DIMM | `WD_TEMP_DIMM_WARN=60` | `WD_TEMP_DIMM_CRIT=70` |
| CPU package/cores | `WD_TEMP_CPU_WARN=90` | `WD_TEMP_CPU_CRIT=100` |
| NVMe controllers/channels | `WD_TEMP_NVME_WARN=70` | `WD_TEMP_NVME_CRIT=85` |
| SATA | `WD_TEMP_HDD_WARN=50` | `WD_TEMP_HDD_CRIT=60` |
| ACPI board | `WD_TEMP_BOARD_WARN=60` | `WD_TEMP_BOARD_CRIT=75` |
| Pool usage (%) | `WD_DISK_WARN=85` | `WD_DISK_CRIT=95` |

Adjust for hardware specifications and ambient conditions, not to silence an unexplained thermal problem. CPU temperature alerts are grouped into one CPU alert; NVMe temperature alerts are grouped per controller. Hardware alarm flags can independently raise a temperature alert.

Other alerts cover pool read-only state, recent corruption changes, selected log-pattern changes, degraded md RAID, failed SMART health, bad-sector/media counters, and reboot loops. Exact behavior depends on the host exposing those sources.
