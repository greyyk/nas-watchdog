# Running on another Linux operating system

NAS Watchdog is **ZettOS-first**, but its collector and UI run in Docker and use standard Linux `/proc`, `/sys`, Docker, smartmontools, and btrfs interfaces. If you have replaced ZettOS on a supported AMD64 Zettlab NAS—or are evaluating equivalent AMD64 hardware—you can adapt the same container.

This path is **experimental**. It does not mean every distribution, storage layout, hypervisor, RAID controller, or non-Zettlab sensor is supported. The known-good combination remains **D6 Ultra + ZettOS + Linux AMD64**. D8 Ultra still needs owner validation. D4/D6 RK3588 ARM models are outside the current release even if you install another OS.

## What transfers unchanged

- Docker UI and all discovered Docker containers
- Standard `/proc` CPU, load, memory, pressure, process, and disk statistics
- Standard hwmon temperature/fan/thermal-zone discovery
- SATA SMART and NVMe controller temperature collection when raw devices and host permissions permit them
- Android app, bearer authentication, history, ntfy/webhook/heartbeat delivery
- btrfs pool and scrub tools when the configured pool is actually btrfs

ZettOS-specific application log discovery simply finds no files when `/zettos/raid/non-snapshot/log/app` does not exist. Ordinary `/var/log` sources, host journal, and dmesg remain available when supported by the host and its permissions.

## Prerequisites

Use a Linux host with:

- `uname -m` returning **`x86_64`**
- Docker Engine and the Compose v2 plugin
- Shell access and `sudo`/root-equivalent Docker permission
- A real filesystem pool mounted on the host, preferably btrfs for full storage coverage
- Standard host PID/mount visibility and the ability to use the supplied privileged host mounts
- Internet or registry access for the initial Go/Alpine build images

Install Docker using your distribution’s supported method or the **[official Docker Engine installation guide](https://docs.docker.com/engine/install/)**. Confirm `docker compose version`; do not assume a standalone legacy `docker-compose` binary provides the same behavior. This project targets Docker’s API/socket and has not been adapted to Podman.

> If Docker runs inside a VM, WSL, or another nested environment, the watchdog observes that environment and whatever devices/mounts you pass through—not automatically the physical NAS beneath it. Direct host installation is the meaningful validation path.

## Install

1. Clone or copy this repository to persistent host storage.
2. Find the pool mountpoint:

   ```sh
   uname -m
   sudo docker --version
   sudo docker compose version
   findmnt -t btrfs -o TARGET,SOURCE,FSTYPE,OPTIONS
   ```

3. If the pool is not btrfs, inspect your mounts with `findmnt`. Basic capacity and read-only state may be useful, but btrfs allocation, corruption, device-stat, scrub, and subvolume features will not apply. Do not enable automatic recovery on an unvalidated filesystem.
4. Set the actual pool path and other shared values:

   ```sh
   cd /persistent/path/<repository>/watchdog
   cp .env.example .env
   ```

   ```dotenv
   POOL_HOST_PATH=/mnt/your-real-pool
   WD_API_TOKEN=generate-a-unique-long-random-token
   ```

   For example, `/mnt/storage` and `/srv/storage` are valid **only if they are the actual mounted pool root**. Do not use a bind-mounted subdirectory, a Docker container path, or a ZettOS path merely because it appears in the example file.
5. Generate the token locally with `openssl rand -hex 32`, paste it into `.env`, and run `chmod 600 .env`. If OpenSSL is unavailable, generate it on a trusted workstation; do not use a public web generator.
6. Follow **[“Start the service” in the shared setup guide](SETUP.md#4-start-the-service)** and verify hostname, pool, drives, sensors, and Docker inventory before enabling anything else.

Compose mounts the host at `/host:ro` and the selected pool at `/pool:ro` on every OS. It also sets `WD_POOL_HOST_PATH` from `POOL_HOST_PATH`, so those values cannot accidentally disagree inside a normal Compose deployment.

## Host-dependent behavior

| Feature | What to check outside ZettOS |
|---|---|
| Host mounts | `pid: host` and `/proc/1/mounts` must expose the real host mount namespace; incorrect pool configuration must be fixed before use |
| SMART | The container needs raw block-device access; USB bridges, HBAs, and hardware RAID may hide drives or require explicit protocol handling |
| Network | Physical/virtual interfaces and namespace commands must represent the host rather than a VM or container-only network |
| Journal/dmesg | `journalctl`, `dmesg`, `systemctl`, `timedatectl`, and `nsenter` availability depends on the distro/init system and permissions |
| CPU detail | Intel `coretemp`, hybrid P/E topology, RAPL, and thermal-throttle files are exposed only on compatible kernels/CPUs; other hwmon channels still appear |
| Reboot | The default guarded command uses host `systemctl reboot` through `nsenter`; leave manual/automatic reboot disabled on non-systemd or otherwise unverified hosts |
| Fan behavior | PWM history is state telemetry, not an instruction to the firmware. Never disconnect cooling or stop fans to test portability |

Actions stay off. Automatic reboot stays off. On a non-ZettOS system, treat them as unvalidated until each exact host action and its recovery effect have been safely reviewed and tested.

## CPU temperature fallback

The strongest thermal mapping is currently implemented for Intel `coretemp`, which is what the D6U validation host uses. On another AMD64 distribution using the same CPU, the naming should remain stable. A non-Intel AMD64 platform can still expose valid temperatures through other hwmon drivers, but they appear under generic/other sensors rather than claiming Intel package/P-E topology or Intel power/throttle counters.

That behavior is intentional: a generic temperature is better than a fabricated CPU-package identity.

## Getting help

Use the normal **Hardware compatibility** or **Bug report** issue template. Replace “ZettOS version” with the distro/release, include the kernel and architecture, and describe how the host/pool differs. A redacted output from:

```sh
sh scripts/hardware-report.sh > hardware-report.txt
```

helps determine whether the issue is an absent kernel interface, a permissions problem, or a collector mapping bug. Review every line before sharing, and keep actions/auto-reboot disabled while validating a new OS.
