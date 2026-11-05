# Contributing

Thanks for helping make NAS Watchdog useful beyond one D6U installation. The most valuable contributions are real hardware validation, safe collector fixes, focused tests, and clear documentation.

## Ground rules

- Keep hardware development focused on **ZettLabs NAS** systems and the primary **ZettOS** experience. Generic AMD64 Linux adaptations are welcome, but must not regress or complicate the validated ZettOS path.
- Preserve existing API fields and Android compatibility unless a versioned change is genuinely necessary.
- Discover what the kernel/Docker exposes instead of hardcoding one owner’s bay counts, hwmon indexes, drive names, fan count, or applications.
- Treat missing hardware and optional capabilities as normal. Do not invent substitute readings.
- Avoid duplicate alert IDs and notification storms for multi-channel devices.
- Keep host filesystem mounts read-only and destructive actions disabled unless a change is specifically about those guarded features.
- Never include tokens, `.env`, private topics/URLs, serial numbers, IPs, raw logs, process arguments, or complete stats responses in fixtures/issues.
- Update docs when configuration, hardware behavior, routes, or support claims change.

## Development setup

### Watchdog

The Go service has no third-party Go dependencies. The Docker build uses Go 1.23 and Alpine. Unit tests use synthetic sysfs/SMART fixtures and do not need a NAS:

```sh
cd watchdog
go test -count=1 ./...
go test -race -count=1 ./...
go vet ./...
```

Build the production target:

```sh
docker build --platform linux/amd64 -t nas-watchdog:dev .
```

Validate Compose with a disposable local configuration:

```sh
cp .env.example .env
# Put a non-empty throwaway value in WD_API_TOKEN.
docker compose config --quiet
rm .env
```

The committed CSS and `ui/vendor/` assets are used by Docker. Only frontend CSS changes need Node:

```sh
npm ci
npm run build
```

Review the generated CSS diff; do not commit `node_modules/`.

### Android

Use JDK 17 and Android SDK platform 35:

```sh
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Reuse the existing Material 3 theme, instrument components, chart implementation, offline behavior, and guarded telemetry routing. Do not add a chart/UI framework for a small feature. Keep analytics in pure Kotlin where practical and add JVM tests for missing/old payloads, grouping, extrema, and thresholds.

No on-device claim should be made from compilation alone. If ADB/UI tests or screenshots are unavailable, say so in the PR.

## Hardware compatibility reports

Run from the repository root on the NAS:

```sh
sh scripts/hardware-report.sh > hardware-report.txt
```

The script is read-only. Review and redact its output anyway before attaching it. Then use the **Hardware compatibility** issue template and describe the exact model, populated slots, expected sensor, and actual result.

D8U validation is particularly useful. D4/D6 ARM work needs a real hardware owner and the investigation checklist in [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md#arm-contributions); changing a build architecture alone is not sufficient support.

## Tests to add for collector changes

Where applicable, cover:

- zero, one, and many matching devices
- noncontiguous IDs and a different hwmon enumeration order
- absent labels, topology, fans, DIMMs, and thermal files
- valid zero values (for example a stopped fan) versus unavailable data
- invalid/sentinel temperatures, limits, and trip points
- stable unique keys and legacy keys
- grouped alert fire/resolve behavior

Do not test by heating hardware, disconnecting cooling, damaging filesystems, removing drives under load, or enabling auto-reboot on an unverified configuration.

## Pull requests

Use a focused change with an explanation of the failure or hardware layout it addresses. Include test commands/results, real model and ZettOS/distro/kernel context where relevant, and screenshots for UI changes when possible. Call out anything not tested on hardware.

The PR template contains the release checklist. GitHub Actions must pass before maintainers should consider the change ready.
