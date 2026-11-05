# Play review access and foreground-service evidence

## App access — no production NAS

Select the **Explore synthetic demo** button on the app's setup screen. It needs no URL/token entry and no external server or internet connection. It presents an original, synthetic NAS inventory through the app's ordinary data/parsing and monitoring paths. The header says **DEMO** and the host is **Demo NAS · synthetic data**.

The demo's CPU, DIMM, six-SATA/two-NVMe, fan, pool, SMART, container, network, history, event, and log content is example data, not sampled production information. Selecting sensors, history ranges, mobile navigation, charts, and settings works. All actions are explicitly disabled; no app-supplied synthetic action can use a real NAS API.

- Choose **More → Settings** for alert controls, privacy policy, independence statement, or local-data removal.
- A synthetic warning labeled **DEMO · Example thermal warning** demonstrates delivery without overheating real hardware.
- To leave, open **Settings → Disconnect & clear local data** and confirm. This removes the demo URL/session and local caches. It does not modify storage on a real NAS or remote service.
- The synthetic demo uses only locally generated data and is not a developer-operated collection endpoint. It is documented in the privacy policy; don't mistake its generated demo identity for a real-hardware credential.

## Foreground service declaration: `specialUse`

The real user-enabled feature is **continuous NAS health monitoring**, not a background data backup or automatic sync task. Until the user disables the service, it maintains a direct, reconnecting server-sent-event connection to a server address the user configured. Its purpose is timely awareness of conditions that could otherwise silently damage availability or stored data while the app is closed.

Suggested declaration:

> After connecting, a user explicitly enables Alert notifications and Live background alerts in Settings. The app's specialUse foreground service keeps a health event stream open to their configured self-hosted NAS and generates notifications for new operational problems. A persistent, low-importance Background monitoring notification reports stream state. The same in-app switch stops the service. Approximately 15-minute WorkManager checks are a best-effort fallback when the live stream is off or interrupted, not an exact alarm. Deferred/interrupted stream work can delay warning about a hot CPU/storage device, stopped fan/container, disk fault, or an unwritable storage pool until reconnection/fallback. The app does not access a camera, microphone, location, media projection, or personal-health sensors and does not perform automatic background transfer of files.

The `specialUse` classification requires review and cannot be guaranteed. Read [current foreground-service policy](https://support.google.com/googleplay/android-developer/answer/13315670) and [declaration/video instructions](https://support.google.com/googleplay/android-developer/answer/13392821). Do not relabel it `dataSync`: it is not a file/data-transfer task.

## Record with the exact submitted build

Record the device screen continuously (no staged screenshots or cuts over missing work). Keep API/properties/build metadata private as appropriate. Base demonstration sequence:

1. Open app on a cleared setup/install state.
2. Press **Explore synthetic demo**, show **DEMO** and synthetic host label.
3. Open **More → Settings → Alerts & background monitoring**.
4. Enable **Alert notifications**, then **Live background alerts**. If Android needs notification permission, show that real prompt and allow it in the recording.
5. Leave the app for Home. Open notification/shade or system Active apps view; show the persistent **Background monitoring** notice and its connection status.
6. Show the synthetic warning with visible **DEMO** wording.
7. Re-open the app and return to Settings. Disable **Live background alerts**, return to Home/shade, and show that the background monitor has stopped.
8. Optionally show the active issue in Overview/Thermal before disabling; say in the video caption that it is synthetic.

Use `play/review/foreground-monitoring.mp4` as the recorded synthetic foreground-service demonstration. Confirm it still matches the exact release you upload; rerecord after changing foreground-service or monitoring-control behavior. Its redacted demo data has no real server address/token. **Upload the video to your own video hosting (for example an unlisted YouTube link), then paste that HTTPS URL into Play Console.** A repository-local video is not itself a video-link field accepted by every Console form.

## Review limitations that must stay honest

The demo does not establish production NAS hardware support or demonstrate real root/privileged host access. The release notes/Play listing must say a separately configured NAS Watchdog server is required for real monitoring. Store screenshots must show the synthetic-demo label instead of implying live observations of somebody else's hardware.

The test credentials are inert, cannot reach a production NAS, and require no Play App Access password. Never put an actual `WD_API_TOKEN`, private LAN/VPN IP, public NAS endpoint, or production app/log telemetry into Console, a test report, a video, or an issue.
