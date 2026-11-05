# NAS Watchdog privacy policy

**Effective:** November 5, 2026
**Distribution:** GitHub and Google Play editions of NAS Watchdog

NAS Watchdog is independent, self-hosted monitoring software operated at your direction. This policy describes the software's data flows. Your own NAS configuration, hosting provider, network, and optional notification services have their own policies.

## Information the software processes

You configure a watchdog server address and API token. The Android app stores those settings in its private app storage. Android backup is disabled, including for device-to-device transfer, to reduce the chance of connection credentials leaving the device through a backup.

When you connect, your device sends the server address, authentication token, and ordinary HTTP requests to the server **you configure**. The app retrieves operational telemetry such as host and OS names, CPU/memory use, temperatures, storage/SMART health, Docker container status, network data, app/system log content exposed by your watchdog, and alert events. These records can reveal device names, local addresses, process/container names, or paths. No personal NAS documents are requested or collected by this app simply to display monitoring statistics.

Views that show host logs can include information written by other software on your NAS. Limit watchdog access and review what your own services log.

The **Explore synthetic demo** button uses only data generated inside the app on your device. It contacts no NAS, internet service, or developer endpoint, and its actions are disabled. Its local cache is subject to the same removal controls below.

## Google Play closed-testing signup

Joining the optional Google Play closed test is separate from using the app. If you submit the [tester signup form](https://forms.gle/7LxUcDdEPudx7pb3A), Google Forms processes the **Google Play email address** you enter and is subject to Google's terms and privacy policy. The maintainer uses that address only to review your request and, if approved, add it to the Play Console tester list. The address is not added to an advertising list. You can ask for removal from the tester list or for deletion of the signup response through the contact channel below.

This optional signup is not required to use the synthetic demo, the signed GitHub APK, or any self-hosted watchdog feature. Do not provide NAS credentials or telemetry in the form or fallback email.

## Developer collection, analytics, advertising, and sharing

The NAS Watchdog software in this repository contains **no developer-operated telemetry endpoint, advertising SDK, analytics SDK, account system, crash-reporting SDK, or tracking identifier**. The project developer does not receive your NAS address, API token, telemetry, logs, or alerts through the app's normal operation.

The software does not sell data or share it with advertising or data-broker services. GitHub, Google Play, your device/OS vendor, network operator, VPN provider, and any services you deliberately configure can process information under their own respective terms when you use them.

## Notifications and optional server integrations

Android's user-enabled live monitoring maintains a foreground connection to the configured watchdog and may display health details in device notifications. Periodic WorkManager checks provide a best-effort fallback. A persistent notification controls the monitoring service; you can turn live monitoring off in Settings. Operating-system notification or battery settings can affect visibility and timing.

The self-hosted watchdog—not the Android app itself unless you configure it there—can send alert details to a user-selected ntfy server or generic webhook and heartbeat requests to an external monitor. Configure only services you trust. A public-broker ntfy topic and a private webhook/heartbeat URL should be treated as secrets.

## Retention and removal

- Android settings and the app's last-good telemetry snapshot remain on the device until you clear app data, reset/edit the connection, or uninstall the app. Uninstall or **Android Settings → Apps → NAS Watchdog → Storage → Clear data** to remove them.
- Web browser tokens and theme/navigation preferences stay in that browser's local storage until you clear site data.
- Watchdog temperature/resource history is kept in memory and normally resets when the container restarts. A bounded event list is also in memory.
- The `watchdog-data` Docker volume stores operational alert/reboot state. Removing it deletes that watchdog state; it does not erase or back up the host NAS pool.
- The app does not create a developer-accessible account, so there is no developer server account to delete.

Delete or rotate an exposed token through your watchdog `.env`, then update/reconnect clients. Do not send unredacted stats, logs, tokens, private addresses, serials, or full hardware dumps through public support channels.

## Security and network use

Use a long random token and a trusted LAN or VPN; do not expose the HTTP watchdog port directly to the internet. Direct LAN monitoring can use HTTP when your device and server allow a private connection. Use a properly configured HTTPS reverse proxy where appropriate. The container has privileged host access and the Docker socket, so keep them private; see the [security guidance](docs/SETUP.md#8-security-and-remote-actions).

## Children and changes

This utility is not directed to children. It has no social features or advertising. Material changes to this software privacy policy will be published with the project documentation. The source repository history records those changes.

## Contact

For privacy questions, corrections, or removal of a closed-testing signup, email <nas.closed.beta.numeric688@passmail.net> with the subject **“NAS Watchdog privacy”**. For non-private questions, use the public project issue tracker at <https://github.com/greyyk/nas-watchdog/issues> after removing all secrets and private telemetry. Google Play users may also use the support contact shown on the store listing. Do not email or post your API token, logs, or a raw stats response.

This policy describes the provided source and release configuration; a fork, modified build, separately hosted service, or third-party network product may behave differently.
