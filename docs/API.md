# HTTP API

The watchdog serves HTTP on host port `8090` by default. All `/api/*` routes except `/api/v1/health` use the configured bearer token. Interactive dashboard assets are also authenticated through the UI’s stored token when API requests are made; unauthenticated users receive a token prompt.

Prefer an authorization header:

```sh
curl -H "Authorization: Bearer $WATCHDOG_TOKEN" \
  http://<nas-ip>:8090/api/v1/stats
```

`?token=<token>` is also supported for the dashboard/SSE workflow, but query credentials can leak through browser history and proxy logs. Do not place tokens in public issues or committed scripts.

There is no metrics-side database, OAuth server, account system, or cloud relay. Use a trusted LAN/VPN and do not expose the HTTP listener directly to the internet.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/stats` | Latest full host/container/storage/thermal snapshot |
| GET | `/api/v1/history?range=5m\|1h\|6h\|24h` | In-memory time-series samples |
| GET | `/api/v1/stream` | Server-sent events, with a compact `stats` event about every five seconds |
| GET | `/api/v1/events` | Recent fired/resolved event buffer (up to 200 events) |
| GET | `/api/v1/logs/sources` | Discovered host/application log sources and match counts |
| GET | `/api/v1/logs?source=<id>&lines=<n>&q=<regexp>` | Read/filter an allowed discovered log |
| GET | `/api/v1/journal?lines=<n>&q=<regexp>` | Host journal lines |
| GET | `/api/v1/dmesg?lines=<n>&q=<regexp>` | Host kernel ring buffer lines |
| GET | `/api/v1/containers/logs?name=<name>&tail=<n>` | Logs for a container in the current Docker inventory |
| POST | `/api/v1/actions` | Opt-in guarded action |
| GET | `/api/v1/health` | Liveness/read-only-pool health; no bearer token required |

Line/tail values are bounded server-side. Log filters use Go regular expressions. Log, stats, process, Docker, and host responses may contain sensitive operational details and are protected routes except health.

## Snapshot and additive sensor metadata

`/api/v1/stats` returns host, CPU/memory/pressure, pool, btrfs, thermal, RAID, disk, SMART, network, Docker, process, log-summary, power, kernel/system, alert, settings, and watchdog-version fields. Versioned `v1` routes are intended to evolve additively so older clients can ignore new values.

Temperature entries always originate in actual hwmon or accepted SMART measurements and retain the original fields:

```json
{
  "key": "nvme0_sensor_1",
  "label": "NVMe 0 Sensor 1",
  "c": 38.85,
  "warn": 70,
  "crit": 85,
  "category": "nvme",
  "source": "hwmon",
  "chip": "nvme",
  "sensor": "temp2",
  "device": "/dev/nvme0",
  "limits": {"max_c": 84.85},
  "alarms": []
}
```

Depending on the source, optional fields include:

- `category`: `cpu_package`, `cpu_core`, `dimm`, `nvme`, `hdd`, `board`, `other`
- `source`, `chip`, `sensor`, `device`
- `core_type`: `performance` or `efficiency` when exposed
- `cpus`: associated logical CPU IDs
- `limits`: optional `min_c`, `max_c`, `lcrit_c`, `crit_c`, `emergency_c`
- `alarms`: active hardware alarm names

Invalid placeholder limits are omitted. Extra sensors/controllers produce additional entries rather than being discarded. Duplicate logical keys are disambiguated rather than overwritten in history.

`thermal_zones` includes `key`, `label`, `c`, and optional `mode`, `policy`, and `trips`. Trip objects contain `index`, `type`, `temp_c`, and `hyst_c` when valid.

SMART SATA records may additionally include `temperature_min_c` and `temperature_max_c` when those vendor-observed values can be parsed from SMART raw text. These are observed extrema, not manufacturer operating limits.

## History

```json
{
  "samples": [
    {
      "ts": "2026-01-01T12:00:00Z",
      "cpu": 12.5,
      "temps": {"cpu": 52, "nvme0": 38.85},
      "fans": {"zettos_pwm_fan_fan1": {"rpm": 1450, "pwm": 144}}
    }
  ]
}
```

Each sample also includes the established CPU/memory/load/network/disk/pool/corruption fields. New maps are optional to clients parsing older responses. Temperature keys represent all current normalized sensors; enabled thermal zones are also included in `temps`. Disabled zones are not. Sensors that appear/disappear create gaps, not synthetic zero values.

The buffer holds up to 2,880 samples. It resets on container restart and covers 24 hours only at the default 30-second interval. SMART-derived SATA temperatures update at the separate SMART interval.

An omitted or unrecognized `range` currently returns the retained buffer. Clients should always request one of the documented ranges.

## Events and live stream

Event objects have this shape:

```json
{
  "ts": "2026-01-01T12:34:00Z",
  "kind": "fired",
  "alert": {
    "id": "temp_cpu",
    "severity": "warning",
    "title": "Core 8 is 91.0°C",
    "detail": "...",
    "since": "2026-01-01T12:33:30Z",
    "active": true
  }
}
```

`kind` is `fired` or `resolved`. `ts` is the event-creation time; newer Android clients fall back to `since` when an older server omits it. The event list is in memory and is not the durable state store.

The SSE stream emits `event: stats` and a JSON `data` line containing timestamp, basic CPU/memory/load/pool values, temperatures, and current alerts. Consumers must tolerate reconnections and missing initial snapshots.

## Actions

Actions return HTTP 403 unless `WD_ALLOW_ACTIONS=true`; each request also needs `confirm:true`.

```json
{"action":"container_restart","target":"container-name","confirm":true}
```

Supported action IDs:

- `container_start`, `container_stop`, `container_restart`
- `scrub_start`, `scrub_cancel`
- `reboot`
- `reboot_cancel` for a pending automatic reboot

Container names must match the latest discovered inventory. A successful response confirms that the operation was issued, not that the container later became healthy or that a scrub completed. Inspect live telemetry/logs for the outcome. Automatic reboot separately requires `WD_ALLOW_REBOOT=true`.

## Health

```json
{"ok":true,"hostname":"NAS","pool_read_only":false,"alerts":0,"ts":"...","uptime_s":1234}
```

HTTP 503 is returned before the first snapshot or when the configured pool is read-only. Therefore Docker can report the container `unhealthy` during precisely the storage incident the watchdog is monitoring. Health does **not** mean every optional sensor is present or that every alert is resolved; `ok:true` can coexist with non-read-only active alerts.
