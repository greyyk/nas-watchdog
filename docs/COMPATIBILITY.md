# Compatibility and hardware discovery

## Support matrix

| Zettlab model | Platform | Current status | Help needed |
|---|---|---|---|
| **D6 Ultra (D6U)** | Intel Core Ultra 5 125H, `linux/amd64` | Tested on a live six-bay/two-M.2 ZettOS host; actual populated devices vary | Regression reports and varied drive/fan configurations |
| **D8 Ultra (D8U)** | Intel Core Ultra 5 125H, `linux/amd64` | Same CPU/target with eight SATA bays and two M.2 slots; not yet validated on physical D8U hardware | Confirm sensors, all populated slots, SMART, fans, pool path, and Docker inventory |
| **D6 / D4** | RK3588 ARM | Explicitly not supported initially | An owner/contributor with hardware, ARM64 build/test work, and validation |

The model/CPU/bay details above follow Zettlab’s **[official model comparison](https://zettlab.com/pages/d4-d6)** and **[D6 Ultra/D8 Ultra specifications](https://zettlab.com/products/zettlab-d6-d8-ultra)**: both Ultra models use Intel Core Ultra 5 125H processors, with six/eight SATA bays and two M.2 slots respectively. Physical bay count is not the same as populated-drive count; the collector reports the devices that are actually present.

### Architecture terminology

**AMD64 does not mean an AMD-manufactured processor.** [Go defines `amd64` as 64-bit x86](https://go.dev/doc/install/source#environment), and [Docker selects `linux/amd64` on x86-64 systems](https://docs.docker.com/build/building/multi-platform/). Linux normally reports that architecture as `x86_64`. The Intel Core Ultra 5 125H explicitly supports [Intel 64 and a 64-bit instruction set](https://www.intel.com/content/www/us/en/products/sku/236848/intel-core-ultra-5-processor-125h-18m-cache-up-to-4-50-ghz/specifications.html), so the Ultra models use the native `linux/amd64` watchdog image.

D4/D6 use the ARM-based RK3588. NAS Watchdog publishes no ARM image and does not support those models in this release; changing a CPU-family label alone would not establish hardware compatibility.

Official specifications establish hardware identity, not sensor availability, NAS Watchdog support, or performance on a particular installation. The D6U validation and D8U owner-validation distinction remains unchanged.

### Operating-system support

| Host OS | Status | Notes |
|---|---|---|
| **ZettOS on D6U** | **Tested and primary** | SSH is enabled in **Settings → Security → SSH**; Docker support is already included. `/zettos/pool/1` is the known default pool path, but every installer should verify it |
| **ZettOS on D8U** | **Owner validation wanted** | Same AMD64 target; do not assume the pool or every sensor is identical |
| **Another AMD64 Linux OS** | **Experimental adaptation** | See [OTHER_LINUX.md](OTHER_LINUX.md). Standard Docker/`proc`/`sysfs` interfaces transfer, but distro/init/storage permissions and reboot behavior must be checked |
| **Any OS on D4/D6** | **Unsupported by this release** | ARM needs its own build, fixtures, hardware validation, and recovery review |

Android is independent of the NAS CPU architecture once a compatible watchdog server exists. The Android app supports Android 8.0 (API 26) and later.

“Expected compatible” for D8U is not a guarantee that every firmware sensor or board-specific feature is present. The app and API treat capabilities as optional.

## What is dynamic

| Area | Discovery behavior | Tests / limitations |
|---|---|---|
| SATA | Enumerates whole `sd*` devices from `/sys/block`, then queries SMART; every valid current SATA temperature is normalized into history | Fixture covers **eight SATA devices**. Partitions, loops, optical drives, and md devices are not SMART candidates. Direct ATA/SATA is the validated path; USB bridges/HBAs may need additional work |
| NVMe | Reads every NVMe hwmon channel and resolves the physical controller from sysfs | Fixtures cover **two controllers with unequal channel counts**. Composite and sensor labels follow kernel exposure; no fixed slot count |
| CPU | Discovers `coretemp` package/core channels by label and maps physical IDs to logical CPUs from topology files | Noncontiguous physical IDs and Intel P/E topology are covered. A hottest-core fallback preserves the legacy `cpu` key if there is no package channel. Other CPU drivers still appear as generic temperatures |
| DIMM | Recognizes `spd5118`, `jc42`, and `ee1004` as memory DIMM sensors | Any number of discovered modules is accepted. Other memory sensors remain visible as generic/other hwmon readings |
| Board / other | Collects valid `temp*_input` channels from every hwmon chip | Limits/alarms and channels are optional. Duplicate logical IDs are made unique instead of overwriting history values |
| Thermal zones | All `thermal_zoneN` directories with valid temperature/type data | Policies, enabled/disabled state, and valid trip/hysteresis data are read; disabled zones do not enter temperature history |
| Fans | Reads `fan*_input` from every hwmon driver; discovers labels and common PWM file layouts | 0 RPM is retained so a stopped fan does not disappear. Fan keys remain unique across controllers. PWM is fan-control state, not proof of airflow |
| Disks / RAID | `/proc/diskstats`, `/sys/block`, `/proc/mdstat` | Hotplug/replacement support follows current names; device-name changes can reset rates/history series |
| Containers | Docker `/containers/json?all=1`, then one-shot stats for each running container | All names/images/states are dynamic. No application list is baked into the UI. Expected-container alerts only apply to names configured by the user |
| Network | Host interfaces and host-network diagnostics | Virtual/physical detection is reported from exposed attributes; network namespace access is required for accurate host values |

Numbers in the D6U previews or fixtures are examples, not expected inventory requirements. An empty hardware inventory returns empty lists rather than crashing or manufacturing zero-temperature devices.

## Values that depend on firmware/kernel support

The following are optional and may be missing even on a fully supported model:

- Per-core `coretemp` channels and Intel `cpu_core` / `cpu_atom` P/E classification
- DIMM SPD readings, hardware maxima, critical limits, and alarm flags
- Fan tachometers and PWM controls
- Intel RAPL package/core/uncore power counters
- Intel package/core thermal-throttle counters
- GPU frequency/RC6 details and GPU hwmon temperatures
- EDAC counters, bcache, md RAID, thermal policies/trip points
- SATA vendor-observed lifetime temperature min/max in SMART raw attributes

The project does not claim unavailable **GPU temperatures, UPS temperatures, PSU sensors, IPMI, or other management-controller data**. A generic hwmon channel can still be displayed under “Other” when the host exposes it.

Hardware min/max/critical limits are metadata, not independent validation of a sensor. Obviously invalid sentinel limits/trips are filtered. Configured warning/critical thresholds remain separate from hardware limits.

## Official ZettOS starting points

- **[Zettlab Wiki (English)](https://wiki.zettlab.com/?locale=en-US)**
- **[Settings / Security / SSH](https://wiki.zettlab.com/?page=apps%2Fsettings%2Fsecurity&locale=en-US)** — enable SSH as an administrator before using this project’s CLI deployment
- **[Docker application guide](https://wiki.zettlab.com/?page=apps%2Fdocker&locale=en-US)** — ZettOS includes Docker support; confirm the app/service is available
- **[Docker Compose guide](https://wiki.zettlab.com/?page=apps%2Fdocker%2Fcompose&locale=en-US)** — official UI/Compose concepts; this project validates the SSH CLI workflow instead of a Docker-GUI import
- **[D4/D6/D6 Ultra/D8 Ultra model comparison](https://zettlab.com/pages/d4-d6)** — official CPU, architecture-family, bay, and slot details

These links explain ZettOS itself. The installation commands and compatibility statements in this repository are for this independent watchdog project, not official Zettlab support commitments.

## D8U validation checklist

Use the normal [setup guide](SETUP.md) with actions and auto-reboot disabled. Then check:

- [ ] `uname -m` reports `x86_64`, and the native AMD64 container starts successfully.
- [ ] Pool path and capacity are correct (`POOL_HOST_PATH` may differ).
- [ ] Every populated SATA bay appears; empty bays produce no fake devices.
- [ ] Both installed NVMe controllers appear if both slots are populated, with correct drive model association and only their real temperature channels.
- [ ] CPU package/core count and P/E labels, if offered by the kernel, match the host topology.
- [ ] DIMM sensors, board channels, fans, PWM, and thermal zones match available host hardware.
- [ ] Active fans have plausible RPM; a deliberately stopped fan should remain visible at 0 only if safe fan behavior can be observed without a stress test. Do not disconnect fans or induce heat for validation.
- [ ] All Docker containers appear, regardless of their names or Compose projects.
- [ ] SMART queries complete without excessive repeated errors, and browser/Android charts update.
- [ ] Alerts can be resolved without a notification loop. Do not create thermal/storage failures to test this.

A successful D8U report helps move the model from “validation wanted” to “community validated.”

## Reporting a hardware difference

From the repository root on the NAS:

```sh
sh scripts/hardware-report.sh > hardware-report.txt
```

The script only reads kernel, block-device, hwmon, thermal-zone, and basic version information; it makes no configuration or hardware changes. Review the file line by line before attaching it.

Include in the issue:

1. Exact Zettlab model and ZettOS (or other Linux distro) version
2. Number of populated SATA/NVMe slots (not drive serial numbers)
3. Expected sensor/device versus what the dashboard shows
4. The reviewed hardware report
5. Watchdog version and `docker compose logs --tail=100 watchdog` after redacting log details
6. Whether you are using the supplied Compose file or a modification

**Never attach** `.env`, API tokens, ntfy topic/token, webhook/heartbeat URLs, full `/stats` JSON, disk serials, raw host logs, process arguments, MAC addresses, private/public IPs, or other personal/service details. The hardware report intentionally avoids SMART output and full Docker stats because they can contain those details.

Open a **Hardware compatibility** issue using the provided template. For ARM, describe the actual NAS model and architecture rather than asking for an untested image.

## ARM contributions

D4/D6 are not just missing a prebuilt binary. ARM64 support needs evidence that the collection and recovery paths work on those hosts.

A useful ARM contribution should:

1. Add a deliberate `linux/arm64` build and CI cross-build/test path without weakening the known-good AMD64 release.
2. Inventory the actual ZettOS kernel interfaces on the D4/D6 before changing claims.
3. Handle CPU model/topology and thermal sources that differ from Intel `coretemp`, `cpu_core`, `cpu_atom`, RAPL, and thermal-throttle paths.
4. Validate SMART/raw-device permissions, pool mount-source detection, host namespace operations, network telemetry, and Docker stats on the ARM NAS.
5. Keep destructive actions/auto-reboot disabled until those exact host operations are tested safely.
6. Add fixtures from the real model with private identifiers removed, plus focused Go/Android parser tests.
7. Update this matrix only with an honest status such as “experimental” or “community tested.”

A well-written hardware report is still valuable before code exists. It lets a contributor decide whether ARM64 support is practical without guessing or shipping an unusable image.
