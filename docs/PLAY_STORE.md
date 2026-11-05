# Google Play publication guide

NAS Watchdog's GitHub APK and Google Play distribution use the same application ID and source. v1.5.1 targets **Android 16 (API 36)**, remains compatible with Android 8.0 (API 26), and publishes both a signed GitHub APK and a signed Play-compatible AAB. Unlike the direct APK, the AAB uses a distinct upload-only key, consistent with Play App Signing's recommended separation.

This is a maintainer checklist, not a promise that Google will approve an app or a substitute for legal/policy advice. Play requirements can change; read the linked current policies and every declaration in Console.

## Ownership, identity, and listing

Use **NAS Watchdog** as the title and independent project identity. Use `com.grey.naswatchdog` exactly; a Play application ID is permanent. Do not use the manufacturer's logo/product imagery, claim official status, or present yourself as the manufacturer.

Read [TRADEMARKS.md](../TRADEMARKS.md) and [Google's Impersonation](https://support.google.com/googleplay/android-developer/answer/9888374) and [Intellectual Property](https://support.google.com/googleplay/android-developer/answer/9888072) policies. Compatibility references in the full description and documentation are factual, with explicit non-affiliation wording. No firmware, manufacturer application, logo, copied screenshots, or proprietary manufacturer code is redistributed.

Copy [store-listing.md](../play/store-listing.md) into Console. Replace only the private contact placeholders with monitored contact information you are willing to publish. Provide real app screenshots and original graphics; see [play/assets/README.md](../play/assets/README.md).

## Signing strategy — decide on the first upload

The GitHub release's existing PKCS12 key signs the direct-install APK. A separate persistent Play upload key signs the AAB from independent secrets. Unlike the uploaded AAB, the final Play-delivered APK is signed with Play's configured **app signing key**. Google's current Console flow automatically enrolls newly created apps in its generated quantum-ready hybrid signing.

The owner-supplied Console export confirms that the listing currently has **three Google-generated app-signing identities**:

- `deployment_cert`: classical deployment on Android 16 and lower, SHA-256 beginning `3B:0A:59:C2`;
- `hybrid_classical_cert`: the classical component on Android 17 and higher, beginning `8D:FA:C5:39`;
- `hybrid_pqc_cert`: the post-quantum component on Android 17 and higher, beginning `5A:93:5F:29`.

None matches the GitHub APK signer's `C2:A2:05:40…C3:62:FC`. Full verified SHA-256/SHA-1 fingerprints and roles are recorded in [play/certificates.md](../play/certificates.md). With the current Play keys, **in-place GitHub ↔ Play upgrades are not promised**. Future GitHub APK releases still preserve the existing direct-install signing identity.

Before the first open-testing or production rollout, deliberately choose:

1. **Keep the current Google-generated identity.** Use Play's normal quantum-ready configuration and make no cross-channel in-place upgrade claim. A different classical deployment signer can require uninstall/reinstall on Android 16 and lower, removing app-local settings.
2. **Preserve the existing GitHub deployment identity.** While Console still offers **Protected with Play → Play Store protection → Manage Play app signing → Change key** before open/production rollout, provide a copy of the existing release private key only through Google's official key-transfer instructions. Verify the resulting classical **app-signing/deployment** certificate—not merely the upload certificate—matches the GitHub APK. Android 17 and higher can still use separate hybrid classical/PQC identities, so repeat the certificate/export check and do not promise one certificate for every Android version.

The AAB from the GitHub Release is signed by the separate upload identity `3F:1A:9C:89…F4:1F:03`; that signature authenticates the upload but is not the final delivered app signature. The four `ANDROID_PLAY_*` secrets already configure this upload key. If it is deliberately reset later, update those secrets and the public upload-certificate documentation together. Do not discard or replace private keys based on fingerprints alone, and do not assume a key-change option remains available after rollout.

Use **Internal app sharing** or **Internal testing** to install the actual Play-processed APK and inspect its signer before broader rollout. If App Links or fingerprint-authenticated APIs are later enabled, register all required app-signing identities, including both hybrid certificates on Android 17 and higher; do not deploy `assetlinks.json` without an owner-controlled HTTPS origin and tested intent filters.

Private keys remain at the operator's private backup location and in encrypted GitHub Actions secrets. They are never included in the source tree, Release assets, logs, Play text, or support responses. See [RELEASING.md](RELEASING.md) for backup and release mechanics.

## What is already automated

A stable `vX.Y.Z` tag runs build/test/lint, produces:

- `nas-watchdog-<version>-android.apk` — direct-install artifact, same signing identity.
- `nas-watchdog-<version>-play.aab` — signed upload artifact for Play Console.
- `SHA256SUMS.txt` — checksums for **both** app artifacts.
- `ghcr.io/greyyk/nas-watchdog:<version>` and `:latest` — public Linux AMD64 watchdog image.

APK verification uses `apksigner`; AABs use `jarsigner`/JAR certificate inspection because `apksigner` does not apply to bundles. Artifacts are uploaded only after Android, Go, and container checks succeed. Publishing to GitHub never automatically uploads or pushes a production release to Play.

## What only the account owner must do in Play Console

There is intentionally no Play API credential or automatic production deployment in this repository. The account owner must:

- [ ] Create/verify the developer identity and app with truthful account/contact information.
- [ ] Confirm the package ID, title, category, language, and free/no-ads status from the listing worksheet.
- [ ] Choose the signing approach above **before the first successful upload/rollout**.
- [ ] Upload the signed AAB; let Console inspect target API, permissions, version code, pre-launch reports, and any warnings.
- [ ] Supply screenshots, original high-resolution icon, feature graphic, optional accurate preview video, contact details, and privacy URL.
- [ ] Complete **Policy status → App content**: content ratings, target audience, news/COVID/financial or other declarations as actually applicable, ads, privacy, and **Data safety** using [data-safety.md](../play/data-safety.md).
- [ ] Complete the **foreground service `specialUse`** declaration and demonstration described in [review-notes.md](../play/review-notes.md).
- [ ] Explain that a separately configured server and hardware are required. Do not misrepresent sample screenshots/demo data as live real-user health.
- [ ] Test installation and upgrades on your own actual Android 8+ and Android 14/16-class devices where possible; test connection refusal, retry,SSE reconnect, notifications, stopped monitoring, rotation, battery/background restrictions, no sensors, and stale snapshots.
- [ ] Start in **Internal testing**. Add your own testers through the track and install the Play-delivered artifact—not merely the GitHub APK—to verify Play App Signing and app behavior.
- [ ] Review Play's current tester requirement for your account type/creation date. Newer qualifying personal accounts may require 12 opted-in closed testers continuously for 14 days before production access.
- [ ] Submit only after all blocking Console dashboard/policy tasks and real-device checks are addressed. Managed publishing can separate approval from a deliberate production go-live.

## Rejections and safe recovery

If Play flags branding, supply the independent identity/compatibility explanation and original-artifact evidence; never fabricate authorization. If foreground monitoring is questioned, show its user enable/disable control, visible persistent notification, live network/alerts use, and battery-saver fallback with the exact submitted build. If data declarations differ, update the policy/source first and correct the Console answer; do not hide behavior just to pass review.

GitHub Releases and GHCR remain the documented fallback channels even if Play review is not approved. An API target or policy deadline is not a reason to reuse an old tag or publish an unverified artifact.

## Current official references

- [Target API level requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [Create/set up an app and permanent application IDs](https://support.google.com/googleplay/android-developer/answer/9859152)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [Foreground-service requirements and video](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Google Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Test newer personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Metadata and intellectual-property policies](https://support.google.com/googleplay/android-developer/answer/9898842)
