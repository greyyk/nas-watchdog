# Testing and release evidence

NAS Watchdog has three layers of verification: server/API tests, browser tests, and Android tests. No verification step should operate privileged actions or send credentials to a real host unless the operator deliberately supplies an isolated testing endpoint.

## Collector and embedded web UI

```sh
cd watchdog
go test -race -count=1 ./...
go vet ./...
npm ci
npm run check
npm run build
```

`app.css` is generated from `ui-src/input.css` and the marked HTML/JS source. Keep the regenerated file committed so normal Docker builds remain Node-free. UI tests use synthetic data and may run a purely local fixture server; it has neither Docker access nor host device mounts.

The test fixtures intentionally exercise multiple SATA/NVMe devices, fan/history gaps, missing optional sensors, alert states, logs, and long inventory text. The fixture does not represent a new hardware claim or a production credential. Do not substitute a raw live `/stats` export for an explicitly synthetic fixture.

## Android JVM and package tests

```sh
cd android
./gradlew --no-daemon \
  testDebugUnitTest lintDebug assembleDebug \
  testReleaseUnitTest lintRelease assembleRelease \
  testPlayUnitTest lintPlay bundlePlay
```

The `play` build type intentionally signs an AAB with the separate Play upload key. Local builds without signing environment variables remain unsigned; that is expected for packaging tests and is not a publishable artifact. Release CI always supplies the two persistent signing identities.

Demo JVM tests validate reserved local data routing, heterogeneous hardware shape, history, and the disabled-action response. No internet or NAS connection is required or expected.

## Instrumented Android tests

Start an API 36 emulator, with no real device/server credentials:

```sh
cd android
./gradlew --no-daemon connectedDebugAndroidTest
```

The instrumentation suite verifies that onboarding does not unexpectedly request notification permission, synthetic setup works, user-enabled foreground monitoring actually starts and stops, persistent notification channels are distinct, boot/update sync respects opt-out, and privacy/disconnect controls are present.

Run a separate minimum-API 26 smoke test for that SQLite-free client lifecycle: setup, local demo, navigation, chart rendering, log views, phone back, and clean disconnect. On Android 14/16 also exercise notification denied/re-granted, process pause/resume, stale connections, orientation, large font, 16 KB page-size build compatibility, and a safe synthetic alert.

Record the exact emulator/tool/app source revision with the results. Never mutate the production Compose service, storage pool, fan, drive health, reboot controls, or real application logs just to create a test event.

## Release and channel checks

- `apksigner verify` for direct-install APKs, including matching the existing public app-signing certificate.
- `jarsigner -verify` and checksum-pinned `bundletool validate` for Play AABs; do not use `apksigner` as an AAB validator.
- `bundletool dump manifest` asserts package/version code/name and target SDK. Inspect `dump config` for the expected 16 KB native ZIP metadata and validate Play-generated APKs in internal testing.
- Web browser screenshots verify phone/landscape/tablet/desktop layout and real navigation, not just rendered Markdown.
- Anonymous GHCR tag inspection plus a clean `docker compose pull; up --no-build` checks public deployment.
- Play App Signing must be tested via an installed Play-delivered artifact from Internal app sharing or an internal track before production. An upload certificate is not the same as an on-device signing certificate.

## Synthetic demo and capture hygiene

Use **Explore synthetic demo**, not a user's NAS. All listing graphics are original project artwork and all screenshot content is labeled synthetic. Check clips/screenshots before publishing for unexpected hostnames, IP addresses, tokens, disk serials, log contents, or third-party branding. A test video must demonstrate the exact app code/version submitted to Play; if the foreground behavior changes thereafter, rerecord it.

Keep test SDK/AVD downloads under ignored `.test-runtime/` when using project-local tooling. Never commit that runtime, generated APK `.idsig` files, keystores, real tokens, debug command history, or arbitrary ADB backups.
