# Play Data safety worksheet

This worksheet records observed data behavior and a **conservative starting point**, not a legal conclusion or a mechanically prescribed form. Play defines collection as transmitting user data **off the device**, irrespective of whether the destination belongs to the developer. A real self-hosted NAS is not automatically outside that policy merely because no developer telemetry endpoint exists.

Use the current Console questionnaire, inspect the actual bundle/runtime, and read [Google's complete Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469) before submitting. The owner is responsible for accurate answers. Internal-testing-only apps may be exempt from public Data safety presentation under current policy, but production/other active tracks need accurate declarations.

## Facts established by source review

- **No** ads SDK, analytics SDK, Firebase, crash-reporting SDK, developer telemetry endpoint, developer account, user profiles, advertising identifier, push marketing or advertising personalization.
- **No** collection of phone location, contacts, photos, microphone, camera, financial data, messages, SMS, calendar, fitness, or the installed-phone-app inventory.
- Requests in normal NAS mode go to an address intentionally entered by the user. It may be a private address, LAN name, VPN address, or HTTPS host. It is the user's administrator-controlled NAS/service, not the NAS Watchdog publisher's server.
- The request includes the user's watchdog connection token. Authentication, log searches, and action POSTs offload requests to that server. UI interactions selected in the app can thus be transmitted, even if Android declares no tracking permission.
- Stats responses fetched into app memory can include hostnames, OS/kernel versions, local addresses/MAC addresses, hardware inventory, mount/log paths, resource usage, running processes, and Docker names/images. These are collected operational NAS data, not phone diagnostics. NAS logs can contain whatever personal data another application chooses to log, so log queries require consciousness of host contents.
- Most data is stored/transiently processed in memory. The last-good snapshot is written locally in `noBackupFilesDir` after SMART serials, MAC addresses and process command lines are redacted. Other host/log/mount/container information remains. The in-app **Disconnect & clear local data** control clears local URL/token/preferences and all cached snapshots.
- Android backup/device-transfer excludes app preferences/files. Local preference and snapshot persistence is not equal to encryption; do not call those at-rest data encrypted.
- HTTP direct-LAN requests are **not encrypted in transit**. HTTPS is supported but requires a valid user-configured server/proxy certificate. The setup UI and privacy policy explicitly warn about HTTP and discourage public exposure.
- The synthetic demo is entirely local-on-device generated data, contacts no NAS or outside server, and has disabled actions. This preserves a useful feature without supplying credentials.
- Optional server-side ntfy/webhook/heartbeat integrations are configured by the server administrator and not made silently by the app. Include any actual additional service/provider behavior in declarations if your own build changes that supply chain.

## Console answers to evaluate for actual real-NAS mode

Start with **Yes** for user-data collection because connection credentials/app interactions/log query content and NAS-identifying data are transmitted off device to the configured server under the actual policy definition. Do not answer No based solely on “self-hosted” or “no analytics.” If Console or Google provides a specific exemption applicable to user-administered endpoint traffic, document that determination before changing to No.

| Data category | What to select and evaluate | Handling notes |
|---|---|---|
| **App activity → App interactions / Other actions** | Included: page/view/action choices and explicit operational requests can be sent to selected NAS | Purpose **App functionality**. No advertising/analytics. If handled only for the immediate request, indicate the current form's ephemeral-processing option as applicable rather than implying no collection occurred |
| **App activity → In-app search history / Other user-generated content** | Included when the user submits log regex/search terms or other entered operational requests | Only intentionally user-entered queries. Evaluate current Console wording to distinguish queries from other user content; don't declare categories with no actual traffic |
| **Device or other IDs** | Include if the current NAS response contains network MAC or other stable identifying values | They describe the user's monitored NAS, not an Android advertising ID. Existing local-snapshot persistence removes MACs, so particular memory-only handling may be ephemeral. Do not advertise a tracking identifier implementation |
| **Personal info / App info & performance** | Select only if the actual form/data includes a matching identifiable field or diagnostic type | Raw NAS log data can be varied; don't exclude a category by assumption when your own deployment/log contains it. None of these is intentionally obtained as a user profile |
| All other categories | No matching requested Android permission/behavior established in the given source | Verify permissions/actual data values again each release |

Those category-level selections are conservative rather than a claim that the publisher receives them. Keep **No** for third-party sharing for the supplied app: no data is sold, shared with advertisers/data brokers, or uploaded to a developer-managed service. Conditional third-party disclosures may apply to optional user-selected integrations. User-initiated and service-provider exceptions require the current framework and legal interpretation; do not assume one applies to every network service.

### Collection purpose and user choice

For any selected operational category, use **App functionality**. No analytics, advertising, marketing, personalization, or developer account management purpose applies in the unmodified build.

Connection/query data can generally be **user-chosen/optional** because the synthetic demo is usable without it. Review the current definition of optional; the corresponding real NAS feature becomes unavailable without selecting a working server/credentials. Do not say background collection is automatic by default: notification/live settings explicitly ask user permission, and new installations default to off.

### Security and deletion questions

| Form question | Suggested answer |
|---|---|
| Is all collected user data encrypted in transit? | **No** for the current HTTP-allowing build. HTTPS is available, but not universal or automatic. Do not select Yes unless enforcing HTTPS throughout every supported app version/configuration |
| Can users request deletion? | **Yes**. The app exposes **Settings → Disconnect & clear local data**, plus app uninstall/Clear data. Policy documents local retention and server-operation metadata. Only return to a lesser answer if actual Console meaning or a changed app version no longer supports the in-app mechanism |
| Independently reviewed global security standard? | **No**, unless a real authorized lab review has been completed. Android lint/unit tests/emulator runs are not an independent MASVS review |

## Each-release review

1. Inspect manifest permissions and any new network SDK; verify no automatic service/analytics was introduced.
2. Compare the AAB and app against this source/data worksheet and current CSV form.
3. Keep privacy link stable: `https://github.com/greyyk/nas-watchdog/blob/main/PRIVACY.md`.
4. If changing direct HTTP, host-log caching, subscriptions, push providers, or any demo/review endpoint, update code/pseudonymous redaction, this worksheet, and privacy disclosures **before** upload.
5. Never compromise real NAS credentials to provide review access. The bundled synthetic demo avoids such exposure and should remain inert and visibly labeled.

Google notes that Data safety review does not verify all factual behavior for you; the developer remains responsible. This document is not a lawyer's determinations and does not guarantee acceptance or absence of legal/policy issues.
