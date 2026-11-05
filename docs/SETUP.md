# ZettOS setup guide

This is the recommended installation path for a compatible Zettlab NAS running **ZettOS**. It takes you from enabling SSH to a verified dashboard, Android alerts, updates, and troubleshooting.

**NAS Watchdog is unofficial, independent software.** It is not affiliated with or endorsed by Zettlab; no manufacturer firmware or proprietary application is included. Read [Third-party names and independence](../TRADEMARKS.md).

**Validated hardware:** D6 Ultra (D6U) on ZettOS / `x86_64`. **D8 Ultra (D8U):** owner validation wanted. **D4/D6 ARM:** not supported yet. See [COMPATIBILITY.md](COMPATIBILITY.md).

> **Using another operating system?** Start with [OTHER_LINUX.md](OTHER_LINUX.md), then return to the shared configuration and Android steps below. The container is AMD64-only today; distro-specific paths and host integrations need validation.

## 1. Prepare ZettOS

You need:

- A D6U/D8U with an existing, healthy btrfs storage pool
- An administrator account allowed to enable SSH and run `sudo docker`
- A workstation and phone/browser on the same trusted network (or an existing VPN)
- A persistent project directory on ZettOS storage—not `/tmp`

### Enable SSH in ZettOS

1. Sign in to the ZettOS web interface as an administrator.
2. Open **Settings → Security → SSH**.
3. Enable SSH and note the port shown by ZettOS.
4. Save, then connect from your workstation:

```sh
ssh <your-zettos-user>@<nas-ip>
# For a custom SSH port:
ssh -p <ssh-port> <your-zettos-user>@<nas-ip>
```

If your ZettOS release names or places the controls differently, use the **[official ZettOS security/SSH guide](https://wiki.zettlab.com/?page=apps%2Fsettings%2Fsecurity&locale=en-US)**. If SSH is absent entirely, update ZettOS through its normal **Settings → System Update** flow and consult the official guide. Do not use unofficial scripts or edit `sshd_config` to work around a missing feature.

Keep public-key/password security and firewall rules managed by ZettOS. Opening SSH to the internet is neither needed nor recommended for this project.

### Confirm the included Docker support

ZettOS already includes Docker support; you normally do **not** install Docker or Compose packages. Open the Docker app once to make sure it is available, then check over SSH:

```sh
uname -m
sudo docker --version
sudo docker compose version
```

For this release, `uname -m` must return `x86_64`. If Docker is unavailable, use the **[official ZettOS Docker overview](https://wiki.zettlab.com/?page=apps%2Fdocker&locale=en-US)** and **[Compose guide](https://wiki.zettlab.com/?page=apps%2Fdocker%2Fcompose&locale=en-US)** rather than installing a second package-managed Docker daemon.

This guide deliberately uses the **Docker Compose CLI**. The generic Docker create-container/project UI is not the validated install path because the watchdog needs `.env`, `pid: host`, raw SMART access, a privileged container, and host-wide read-only mounts.

### Find the ZettOS pool mountpoint

The known ZettOS default is `/zettos/pool/1`. Confirm it for your actual pool:

```sh
findmnt -t btrfs -o TARGET,SOURCE,FSTYPE,OPTIONS
```

If `findmnt` is unavailable:

```sh
grep ' btrfs ' /proc/mounts
```

Choose the pool’s own mountpoint—the `TARGET` column or second whitespace-separated field—not a shared folder below it. Record that exact path for `POOL_HOST_PATH` if it differs from `/zettos/pool/1`.

## 2. Put the source in persistent ZettOS storage

On your workstation, either clone the repository or choose **Code → Download ZIP** on GitHub and extract it:

```sh
git clone https://github.com/greyyk/nas-watchdog.git
```

Copy the complete repository to the NAS over SSH/SFTP. If Git is installed on ZettOS, cloning directly there also works.

A validated D6U installation uses `/zettos/raid/nas-watchdog`; that path is an **example, not a requirement**. Choose a persistent directory appropriate for your own pool and permissions. Avoid system folders and temporary storage.

The remaining commands assume the checkout is at `/zettos/raid/nas-watchdog`; substitute your actual directory if different:

```sh
cd /zettos/raid/nas-watchdog/watchdog
```

Keep using this same directory for future updates so Compose keeps using the same project and persistent Docker volume.

## 3. Create `.env`

```sh
cp .env.example .env
```

Generate a random API token:

```sh
openssl rand -hex 32
```

Edit `.env` with the available editor and paste the complete value:

```dotenv
WD_API_TOKEN=paste-the-generated-hex-value-here
```

Set the host mountpoint if it differs:

```dotenv
POOL_HOST_PATH=/your/actual/btrfs/mountpoint
```

Compose uses that one value both to mount the directory at `/pool` and to find the correct host mount record. Do **not** change `WD_POOL_PATH=/pool` or `WD_HOST_ROOT=/host` for a normal installation.

Protect the secrets file:

```sh
chmod 600 .env
```

`.env` is excluded by `.gitignore` and `.dockerignore`. The supplied Compose file fails with a useful message if `WD_API_TOKEN` is empty.

### Optional settings

- `WATCHDOG_PORT=8090` changes the host port.
- `WATCHDOG_BIND_ADDRESS=0.0.0.0` makes the dashboard reachable on the NAS network interfaces. Binding to `127.0.0.1` is appropriate only when another secure proxy/tunnel provides access; the Android app cannot connect to the NAS through its own loopback address.
- `WD_INTERVAL=30s` controls normal collection.
- `WD_SMART_INTERVAL=10m` limits SMART disk churn.
- `WD_EXPECTED_CONTAINERS=` is blank initially. Leave it blank while checking inventory.

Full settings are in [CONFIGURATION.md](CONFIGURATION.md).

## 4. Start the service

On the NAS, from `watchdog/`:

```sh
sudo docker compose up -d --build
sudo docker compose ps
sudo docker compose logs --tail=100 watchdog
```

The first build downloads the Go/Alpine build images and installs runtime tools in the image. ZettOS needs **no additional Go, Node, smartmontools, or btrfs host packages** for this project. Building natively on the D6U/D8U avoids AMD64 emulation on a workstation.

### Use a published GHCR container instead

After the repository has a published release image, you can avoid compiling Go on the NAS. Keep the normal token/pool settings and add this to `.env`, replacing all placeholders:

```dotenv
WATCHDOG_IMAGE=ghcr.io/greyyk/nas-watchdog:1.5.1
```

Then pull and start that exact version:

```sh
sudo docker compose pull
sudo docker compose up -d --no-build
```

The image is `ghcr.io/greyyk/nas-watchdog:<version>`. A corresponding `:latest` tag is also published, but pinning an exact version keeps updates deliberate. If later switching back to a local build, comment out `WATCHDOG_IMAGE` and run `sudo docker compose up -d --build`.

Public GHCR packages need no registry token. The maintainer’s [release guide](RELEASING.md#5-use-the-ghcr-image-on-zettos) covers package visibility and first-time publication.

`docker compose ps` should eventually show **healthy**. Refresh statistics after the first full collection. Initial scans can be slower on systems with many drives or devices that do not respond promptly to SMART queries.

### Verify the important paths

In the dashboard, confirm:

1. Hostname, CPU model, and uptime match the NAS—not the container.
2. The pool device, capacity, and mount state match the actual btrfs pool.
3. Every populated internal SATA and NVMe device appears.
4. Temperature and fan readings look plausible. A stopped fan may legitimately report 0 RPM; a missing fan channel is not rendered as a fake device.
5. Docker lists the containers you actually run.

If these values are wrong, stop before enabling actions and use [Troubleshooting](#9-troubleshooting).

## 5. Open the web dashboard

From a device on the same trusted network:

```text
http://<nas-ip>:8090
```

Enter the API token when prompted. The token is stored in that browser’s local storage. Opening `/?token=<token>` also works, but the prompt is preferable because a token in a URL may enter browser history or proxy logs; the UI removes the query parameter after storing it.

The dashboard is HTTP by design for direct LAN/VPN use. Do not port-forward it to the public internet. If you add a TLS reverse proxy, maintain equivalent authentication and carefully test server-sent events.

## 6. Install the Android app

### Explore without a server

On first launch choose **Explore synthetic demo**. This locally generated, clearly labeled preview needs no NAS, internet connection, or credentials. It is suitable for trying navigation, charts, logs, and notification controls. Actions are disabled. It does not demonstrate real host health or hardware compatibility.

Use **Settings → Disconnect & clear local data** to remove the demo, connection credentials, preferences, and cached snapshots from this device without modifying a NAS.

### Install a signed release APK

Once the repository’s release pipeline has published a version, download `nas-watchdog-<version>-android.apk` and `SHA256SUMS.txt` from that version’s GitHub Release. The `*-play.aab` asset is for Play Console upload and **cannot be installed with `adb`**; phone users should choose the APK or a published Play listing.

Future GitHub APKs retain the existing direct-install signer. The current Play export uses different Google-generated app-signing certificates, so in-place GitHub ↔ Play upgrades are **not** promised under that configuration. See [certificate roles and fingerprints](../play/certificates.md) and the controlled [signing strategy](PLAY_STORE.md#signing-strategy--decide-on-the-first-upload). A different classical deployment signer can require uninstalling/reinstalling on Android 16 and lower, which clears app-local settings.

Verify from a workstation before installing. On macOS:

```sh
shasum -a 256 -c SHA256SUMS.txt
```

On Linux:

```sh
sha256sum -c SHA256SUMS.txt
```

Install by copying the APK to Android or using ADB:

```sh
adb install -r nas-watchdog-<version>-android.apk
```

Use only APKs from the repository’s Releases page, built by its Release workflow. Keep future updates tied to the same signing key. If switching from a locally signed debug build, Android may require uninstalling that build first; its local settings will be cleared.

### Build from source

For development or a custom build, compile the debug APK on a workstation.

### Requirements

- JDK 17
- Android SDK with platform 36 (Android Studio’s SDK Manager is the easiest route)
- Android 8.0 / API 26 or newer on the phone

### Build and install

```sh
cd android
./gradlew assembleDebug
```

The APK is:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Install over ADB:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Alternatively copy the APK to the phone and allow installation from the file manager/browser when Android asks. Debug builds are for personal installs and testing; configure release signing before publishing production APKs.

### Connect

1. Open **Settings**.
2. Set the server to exactly `http://<nas-ip>:8090` (no `/api` suffix, token query, or embedded credentials).
3. Paste the same `WD_API_TOKEN`.
4. Save and confirm live telemetry loads.
5. Allow notification permission when requested.
6. Leave **Live alerts / background monitoring** enabled for the reconnecting live stream, or disable it for battery-saver checks only.

WorkManager checks are scheduled approximately every 15 minutes and can be delayed by Android. Aggressive vendor battery controls can reduce delivery further; exempt the app from battery restriction if timely alerts matter. Force-stopping the app disables its background execution until it is opened again. The NAS must remain reachable through LAN/VPN.

## 7. Configure notifications

### ntfy

Set, for example:

```dotenv
WD_NTFY_URL=https://ntfy.sh
WD_NTFY_TOPIC=replace-with-a-long-unguessable-topic
WD_NTFY_TOKEN=
```

Use your own ntfy server’s URL when available. Set `WD_NTFY_TOKEN` only if the server/topic requires it. A public-broker topic name is effectively a secret: anyone who learns it may be able to subscribe or publish depending on server policy. Do not commit it.

Subscribe to the topic in an ntfy client, then test alert delivery using a real controlled event rather than deliberately damaging a pool or overheating hardware.

### Webhook

```dotenv
WD_WEBHOOK_URL=https://your-service.example/alert-hook
```

The watchdog POSTs JSON containing `kind`, `id`, `severity`, `title`, `detail`, and `since`. Resolved events have a prefixed title and lower ntfy priority. Notification HTTP errors appear in container logs; deliveries are not durably queued.

### Dead-man’s heartbeat

```dotenv
WD_HEARTBEAT_URL=https://your-monitor.example/push/unique-id
WD_HEARTBEAT_INTERVAL=60s
```

Point this at a monitor on another host or an external service such as Healthchecks.io. An unreachable NAS eventually stops pinging and the external monitor raises an alert. Uptime Kuma running on the same NAS cannot detect a complete NAS outage by itself; host it elsewhere.

Restart the service after changing `.env`:

```sh
sudo docker compose up -d
```

## 8. Security and remote actions

### Understand the trust boundary

The stock configuration is intentionally privileged so it can inspect host mounts, raw drives, logs, network/host namespaces, and Docker. It is not an unprivileged sandbox:

- Host `/` and the pool are mounted **read-only**.
- `privileged: true` permits SMART access to raw devices.
- `pid: host` ensures `/proc/1/mounts` describes the host rather than the container.
- Host namespace access supports journal, dmesg, host network, and optional reboot operations.
- The Docker socket grants control of Docker. A read-only bind on a socket would **not** reliably make its API read-only.

Do not expose the API to untrusted networks. Use a strong unique token, trusted LAN/VPN access, normal OS security practices, and reviewed updates. Dashboard/API data includes device details, process arguments, logs, and network information that may themselves be sensitive.

### Actions are opt-in

After verifying monitoring and access control:

```dotenv
WD_ALLOW_ACTIONS=true
WD_ALLOW_REBOOT=false
```

Run:

```sh
sudo docker compose up -d
```

`WD_ALLOW_ACTIONS=true` permits explicitly confirmed container start/stop/restart, btrfs scrub start/cancel, and manual reboot requests from authenticated clients. A wrong container selection or reboot can interrupt services.

`WD_ALLOW_REBOOT` separately controls **automatic** read-only-pool recovery. Leave it `false` until you understand the failure mode and have verified backups. If enabled, auto-reboot waits for `WD_REBOOT_DELAY`, observes `WD_REBOOT_COOLDOWN`, and suppresses loops. It is not a repair for chronic hardware or memory problems.

No hardware stress test, forced disk failure, unsafe SMART test, or forced read-only pool is needed—or recommended—for installation verification.

## Updating

Keep your private `.env`; it is not part of Git. From the repository root after reviewing upstream changes:

```sh
git pull
cd watchdog
sudo docker compose up -d --build
sudo docker compose ps
```

`git pull` may overwrite local tracked-source edits. Fork/patch or save your own source changes first; your `.env` and named Docker volume are separate.

Updating/recreating the container clears in-memory chart history and the recent in-memory event list. The named `watchdog-data` volume retains persistent alert/reboot state. Use the same Compose project directory to retain the same volume.

### Back up the persistent state (optional)

State is operational metadata, not a substitute for NAS backups. With the container stopped:

```sh
sudo docker compose stop watchdog
sudo docker run --rm -v watchdog_watchdog-data:/data -v "$PWD":/backup alpine \
  sh -c 'tar -czf /backup/watchdog-state.tgz -C /data .'
sudo docker compose start watchdog
```

The default Compose project name is normally `watchdog` because the file is in `watchdog/`; confirm with `docker volume ls` if you changed project naming. Never restore an old alert state blindly onto a different NAS.

### Remove the service

From `watchdog/`:

```sh
sudo docker compose down
```

That removes the container/network but retains the named volume. `docker compose down -v` additionally deletes watchdog state; it does not delete the host pool.

## 9. Troubleshooting

### Compose says `WD_API_TOKEN` must be set

Create `watchdog/.env` from `.env.example` and set a non-empty token. The token may contain a long generated hex string without quotes. Restart with `sudo docker compose up -d`.

### Dashboard asks for a token / Android says access denied

Use the exact current `WD_API_TOKEN`. In the app, enter only the base server URL, not `?token=...`. After a token change, update the app and browser and restart Compose.

### Pool shows zero capacity or incorrect device

Check `POOL_HOST_PATH` against `findmnt`/`/proc/mounts`. It must be the **host’s actual pool mountpoint**, not `/pool`, a subvolume, or a shared folder below it. Change `.env`, run `sudo docker compose up -d`, and recheck. Also confirm `pid: host` remains set.

### SATA drives are missing

Check `ls -l /sys/block` and `smartctl -a /dev/sdX` on the host. The collector uses whole `sd*` block devices, not partitions. SMART needs the supplied privileged raw-device mounts. USB/HBA/RAID-controller behavior varies; direct ATA support is the validated path. A failed/unsupported SMART query is reported with an error instead of invented health or temperature data.

### NVMe channels look mislabeled

Controller identity is derived from the resolved sysfs path and is not based on `hwmon2`, `hwmon3`, etc. Extra temperature channels and invalid placeholder limits should be omitted or separated correctly. If not, run the read-only [hardware report](COMPATIBILITY.md#reporting-a-hardware-difference) and open a hardware issue after redacting sensitive information.

### DIMMs, fans, power, P/E labels, or throttle counters are absent

Those details require kernel/firmware exposure. Not every model exposes DIMM SPD sensors, fan tachometers, PWM, Intel RAPL power, hybrid CPU topology, or Intel thermal-throttle counters. Generic hwmon temperatures/fans are still collected. Absence alone is not evidence that the hardware is broken.

### Docker inventory or logs are empty

Confirm `/var/run/docker.sock` exists and the socket mount is present. Inspect `sudo docker compose logs watchdog`. Expected-container alerts are independent of inventory: all API-discovered containers are shown even if `WD_EXPECTED_CONTAINERS` is blank.

### Container is unhealthy or restarting

```sh
sudo docker compose ps
sudo docker inspect --format '{{json .State.Health}}' nas-watchdog
sudo docker compose logs --tail=200 watchdog
```

The health endpoint deliberately returns failure when the monitored pool is read-only. Check the web/API snapshot and host storage state rather than treating every unhealthy status as a watchdog bug.

### Android is live only while open

Check notification permission, the live-monitoring setting, VPN connectivity, battery exemption, and whether the app was force-stopped. Periodic checks are not exact 15-minute real-time alarms. Android cannot reach the NAS when LAN/VPN connectivity is down.

### Prepare a useful issue

Run `sh scripts/hardware-report.sh` from the repository root. Review every line before attaching it. Do not attach `.env`, tokens, private webhook/heartbeat URLs, full raw stats, raw logs, disk serials, public/private IP addresses, or process command lines. Use the **Hardware compatibility** or **Bug report** issue template.
