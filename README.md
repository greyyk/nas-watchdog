# ZettLabs NAS Watchdog

**ZettOS-first monitoring with a live web dashboard, rich Android app, and alerts that explain what actually happened.**

Purpose-built for ZettLabs D6 Ultra and D8 Ultra hardware. It catches what ordinary uptime checks miss: a btrfs pool forced read-only, a marginal DIMM, a hot NVMe controller, a stopped fan, unhealthy drives, or a container that is present but no longer running.

[![ZettLabs D6U tested](https://img.shields.io/badge/ZettLabs-D6U%20tested-35c58b)](#model-support)
[![D8U validation wanted](https://img.shields.io/badge/D8U-validation%20wanted-e9b949)](#model-support)
[![Docker · Linux AMD64](https://img.shields.io/badge/Docker-Linux%2FAMD64-2496ED)](#quick-start)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84)](#android-app)
[![MIT license](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

> **Built for ZettLabs. Tuned for ZettOS.** D6 Ultra (D6U) has been live-tested on ZettOS; D8 Ultra (D8U) needs owner validation. **D4 and D6 ARM models are not supported yet.** Running another OS on your D6U/D8U? The Docker service can also be adapted to other **AMD64 Linux** installations; see the [non-ZettOS guide](docs/OTHER_LINUX.md). Hardware reports and well-tested contributions are welcome.

**[ZettOS installation](docs/SETUP.md) · [Hardware discovery](#hardware-that-adapts-to-the-nas) · [Android app](#android-app) · [Configuration](docs/CONFIGURATION.md) · [Official Zettlab Wiki](https://wiki.zettlab.com/?locale=en-US)**

## Highlights

| Area | What you get |
|---|---|
| **Storage truth** | Pool read/write state, btrfs corruption and allocation, scrubs, RAID, bcache, per-disk I/O, full SMART attributes |
| **Complete thermals** | Every exposed hwmon temperature, per-core CPU readings, DIMMs, all populated SATA/NVMe devices, board sensors, hardware limits/alarms, thermal trips, fan RPM/PWM |
| **Interactive history** | 5m/1h/6h/24h charts, exact samples, min/average/max/change, threshold exposure, cooling response |
| **Actual Docker inventory** | Automatically discovers all containers, health, resources, ports, and logs; expected-container alerts are opt-in |
| **Alerts that explain** | ntfy, generic webhooks, Android notifications, and an external dead-man’s heartbeat |
| **Guarded actions** | Opt-in Docker controls, btrfs scrub operations, and reboot recovery—disabled by default |
| **Two native interfaces** | Embedded responsive web dashboard and a Material 3 / Compose Android app |

No external metrics server, account, Firebase project, or chart service is required. The Go backend uses only the standard library and serves its UI directly from the container.

## Model support

| Model | Architecture | Status |
|---|---|---|
| **D6 Ultra (D6U)** | Intel Core Ultra 5 125H / Linux AMD64 (`x86_64`) | **Tested** against a live ZettOS system |
| **D8 Ultra (D8U)** | Intel Core Ultra 5 125H / Linux AMD64 target | **Expected compatible; hardware validation wanted** |
| **D6 / D4** | RK3588 ARM | **Not supported in this release**; see the [contribution path](docs/COMPATIBILITY.md#arm-contributions) |

Sensor availability is firmware and kernel dependent. The watchdog reports what the host actually exposes; it never fabricates missing GPU, UPS, PSU, IPMI, fan, or drive readings. See **[Compatibility and hardware discovery](docs/COMPATIBILITY.md)** for exact behavior and a D8U validation checklist.

## Quick start

### 1. Enable ZettOS SSH

Sign in to ZettOS as an administrator and open **Settings → Security → SSH**. Enable SSH, note the configured port, then connect from your workstation:

```sh
ssh <your-zettos-user>@<nas-ip>
# If you selected a non-default port:
ssh -p <ssh-port> <your-zettos-user>@<nas-ip>
```

Menu names can vary by ZettOS release; the current reference is the **[official security/SSH guide](https://wiki.zettlab.com/?page=apps%2Fsettings%2Fsecurity&locale=en-US)**. Use an administrator account with permission to run `sudo docker`.

### 2. Check the built-in Docker support

ZettOS already includes Docker support, so there is normally **nothing to install**. Open the Docker app once to confirm it is available, then verify the CLI and host architecture over SSH:

```sh
uname -m                   # expected: x86_64
sudo docker --version
sudo docker compose version

findmnt -t btrfs -o TARGET,SOURCE,OPTIONS
# The known ZettOS default pool path is /zettos/pool/1.
```

For Docker’s ZettOS interface and Compose concepts, see the **[official Docker overview](https://wiki.zettlab.com/?page=apps%2Fdocker&locale=en-US)** and **[Compose guide](https://wiki.zettlab.com/?page=apps%2Fdocker%2Fcompose&locale=en-US)**. This project’s validated deployment path is SSH + Docker Compose CLI because it uses a local `.env`, a privileged container, and host-wide read-only mounts.

### 3. Configure

Place this repository somewhere persistent on ZettOS storage (for example under `/zettos/raid/`, not `/tmp`), using Git if installed or by downloading the repository and copying it over SSH/SFTP. Then:

```sh
cd /persistent/path/<repository>/watchdog
cp .env.example .env
openssl rand -hex 32
```

Paste the generated value into `WD_API_TOKEN` in `.env`, then protect the file:

```sh
chmod 600 .env
```

Review `POOL_HOST_PATH` if your pool is not mounted at `/zettos/pool/1`. All other settings—including notification destinations, thresholds, and expected containers—are documented in [`.env.example`](watchdog/.env.example) and the [configuration reference](docs/CONFIGURATION.md).

> The supplied Compose file **requires a non-empty API token**. Actions, automatic reboot, and expected-container alerts are off by default.

### 4. Run

```sh
sudo docker compose up -d --build
sudo docker compose ps
sudo docker compose logs --tail=50 watchdog
```

Open `http://<nas-ip>:8090` and enter the token when prompted. Do not expose this HTTP service directly to the internet; use your trusted LAN or an existing VPN.

**[Full ZettOS setup, verification, notifications, Android, and troubleshooting →](docs/SETUP.md)**

> **Not running ZettOS?** Use the **[other-Linux installation notes](docs/OTHER_LINUX.md)**. The AMD64 container is portable, but distro-specific hosts, pool paths, and recovery actions need validation.

## Hardware that adapts to the NAS

There are no fixed three-disk, two-NVMe, or three-fan assumptions:

- **SATA:** whole `sd*` block devices are discovered from `/sys/block`; every populated slot with a valid SMART temperature is included. The tests exercise all eight SATA positions plus non-disk exclusions.
- **NVMe:** controllers and sensor channels come from the resolved hwmon sysfs path, not hwmon enumeration order. One or two controllers—and additional channels per controller—are represented independently.
- **CPU:** `coretemp` package/core labels are mapped to actual physical core IDs and logical CPUs. Intel P/E topology is added when the kernel exposes it. A hottest-core fallback keeps the legacy CPU reading available if no package sensor exists.
- **Memory and board:** supported DIMM SPD sensors, other hwmon chips, extra board channels, and thermal zones are optional. Empty slots and missing devices produce no placeholder readings.
- **Cooling:** fan inputs from any hwmon driver are collected, including stopped fans at 0 RPM. PWM and valid hardware limits/alarms are included when available.
- **Docker:** inventory comes from the Docker API on every collection. Add `WD_EXPECTED_CONTAINERS` only for services that should alert when missing or stopped.

Missing capabilities remain visible as **not reported**, not as zero-valued hardware that never existed.

## Android app

The Android client includes live telemetry, last-good offline snapshots, CPU and thermal instruments, storage/SMART details, Docker controls and logs, network information, alert history, and guarded actions.

Tagged releases can publish a **signed release APK with SHA-256 checksums** through the automated [Release workflow](.github/workflows/release.yml). Maintainers configure one persistent signing identity as described in the [release guide](docs/RELEASING.md).

### Build

On a workstation with **JDK 17** and the Android SDK:

```sh
cd android
./gradlew assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` with Android Studio, by copying it to the phone, or with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

In **Settings**, enter `http://<nas-ip>:8090` and the same API token. Enable notifications and live monitoring for the reconnecting alert stream; battery-saver checks run approximately every 15 minutes as a fallback. Android and device-vendor battery policies can delay background work. Force-stopping the app stops background checks until it is opened again.

## Alerting

- **ntfy:** private/self-hosted or public broker, with optional bearer authentication. Use an unguessable topic on a shared server.
- **Webhook:** JSON events with kind, ID, severity, title, detail, and onset time.
- **Heartbeat:** periodic HTTP GET for Healthchecks.io, Uptime Kuma, or another push monitor. Put the monitor off the NAS so a total NAS failure still alerts you.
- **Android:** reconnecting server-sent events plus periodic WorkManager checks. No push-service account is needed.

Temperature alerts are grouped per CPU and per NVMe controller to prevent one hot device from generating a notification for every sensor channel. Resolved events are reported separately.

## Security and permissions

This is a high-trust host agent, not an ordinary unprivileged container:

- `/` is mounted **read-only** at `/host`; the pool is mounted **read-only** at `/pool`.
- `privileged: true` enables raw-device SMART access.
- `pid: host` is required for an accurate host mount table and host-namespace diagnostics.
- The Docker socket can control every container. Mutating API actions still require `WD_ALLOW_ACTIONS=true`, but anyone who compromises the container process could reach the socket directly.
- Remote actions always require both the feature flag and request confirmation. Automatic reboot is separately guarded by delay, cooldown, and reboot-loop suppression.

Use a long random token, keep `.env` private, patch and review updates, and keep the service behind your LAN/VPN boundary. Read the [security section in the setup guide](docs/SETUP.md#security-and-remote-actions).

## Operations notes

- History is stored **in memory** and restarts with the container. Up to 2,880 samples provide 24 hours at the default 30-second interval; changing the interval changes that time span.
- SMART is polled every 10 minutes by default. Other samples are normally collected every 30 seconds.
- The event buffer holds the latest 200 events; alert state and selected reboot/counter state persist in the Docker volume.
- After an update, use the same project directory so Compose retains the `watchdog-data` volume.

### Published containers

The release workflow publishes Linux AMD64 images to **GitHub Container Registry**:

```text
ghcr.io/<owner>/<repository>:<version>
ghcr.io/<owner>/<repository>:latest
```

To use a pinned release instead of a local build, set `WATCHDOG_IMAGE=ghcr.io/<owner>/<repository>:<version>` in `watchdog/.env`, then run `sudo docker compose pull` and `sudo docker compose up -d --no-build`. See [Release installation](docs/RELEASING.md#5-use-the-ghcr-image-on-zettos) for verification, upgrades, and package visibility.

## Documentation

- **[ZettOS setup, Android installation, updates, and troubleshooting](docs/SETUP.md)**
- **[Other Linux operating systems](docs/OTHER_LINUX.md)**
- **[Compatibility and hardware discovery](docs/COMPATIBILITY.md)**
- **[Configuration reference](docs/CONFIGURATION.md)**
- **[HTTP API](docs/API.md)**
- **[Publishing signed APKs and GHCR containers](docs/RELEASING.md)**
- **[Contributing](CONTRIBUTING.md)**

## Development and verification

```sh
# Go collector and API
cd watchdog
go test -race ./...
go vet ./...

# Android JVM tests, lint, and debug APK
cd ../android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The committed web CSS and vendored uPlot assets mean the Docker build does not need Node. To regenerate the UI CSS instead:

```sh
cd watchdog
npm ci
npm run build
```

GitHub Actions runs Go tests/race tests/vet, the Android tests/lint/APK build, Compose validation, and an AMD64 Docker image build. See [Continuous integration](.github/workflows/ci.yml).

Release preparation covered a live D6U watchdog/API and the Android JVM/lint/debug-build checks. Physical-device Android UI validation is still welcome; compiled Compose previews alone are not a substitute for testing a phone.

## Project layout

```text
watchdog/          Go collector, API, embedded dashboard, Dockerfile, Compose
android/           Kotlin / Jetpack Compose Android client
scripts/           Read-only hardware report for compatibility issues
docs/              Setup, compatibility, configuration, and API guides
.github/           CI and issue/PR templates
```

## Contributing

D8U validation, missing-sensor reports, Android UI testing, and documentation fixes are especially useful. ARM support needs an owner with D4/D6 hardware; it should be developed and tested rather than promised from the AMD64 implementation alone.

Use a small, redacted **[hardware report](docs/COMPATIBILITY.md#reporting-a-hardware-difference)** when opening a compatibility issue. Never upload `.env`, API tokens, ntfy topics/tokens, serial numbers, private IP addresses, raw logs, or a complete `/stats` response.

## License

NAS Watchdog is available under the **[MIT License](LICENSE)**. Bundled third-party assets retain their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
