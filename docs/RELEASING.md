# Releasing NAS Watchdog

A stable tag publishes the open-source distribution artifacts. It does **not** upload to Google Play, install an app, or reconfigure a running NAS.

| Artifact | Destination |
|---|---|
| Signed direct-install APK | GitHub Release: `nas-watchdog-<version>-android.apk` |
| Signed Android App Bundle for Play | GitHub Release: `nas-watchdog-<version>-play.aab` |
| SHA-256 checksums for both | Same GitHub Release: `SHA256SUMS.txt` |
| Linux AMD64 watchdog | `ghcr.io/greyyk/nas-watchdog:<version>` and `:latest` |

The workflow accepts only `vMAJOR.MINOR.PATCH` tags, runs tests/lint, embeds tag-derived version metadata, verifies APK and AAB signing, builds/tests/pushes the image, and creates the Release only after all paths succeed.

## 1. Protect the persistent signing identity

The existing Android PKCS12 key is the long-lived project identity. The workflow reads these **encrypted GitHub Actions secrets**:

| Secret | Purpose |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Complete base64 PKCS12 keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Existing alias, currently `nas-watchdog` |
| `ANDROID_KEY_PASSWORD` | Key password |
| `ANDROID_PLAY_KEYSTORE_BASE64` | Separate, base64 PKCS12 Play upload keystore |
| `ANDROID_PLAY_KEYSTORE_PASSWORD` | Play upload keystore password |
| `ANDROID_PLAY_KEY_ALIAS` | Play upload alias |
| `ANDROID_PLAY_KEY_PASSWORD` | Play upload private-key password |

A configuration repository variable, `BUNDLETOOL_SHA256`, pins the checksum of bundletool 1.18.2 used to validate the signed AAB. Never turn this into a moving/latest validator without a deliberate review and updated hash.

The APK `release` build type uses the existing app/GitHub signing identity. The `play` build type uses the separate upload identity so the uploaded AAB is not misrepresented as the final Play-delivered app signature. The two public certificates are intentionally different; keep both backed up. Play's App integrity page—not this upload certificate alone—shows the effective distribution certificate.

Do not regenerate this key for every release or commit it as `.p12`, `.jks`, `.keystore`, `.pfx`, base64, or plaintext. The workflow decodes the keystore only on its ephemeral runner and checks the alias/password without printing them. If a secret is absent, release signing fails rather than emitting an unsigned or debug-signed APK.

For this publication checkout, the private signing files are kept outside Git. Back up the complete directory securely with its password. Losing it can prevent compatible Android upgrades and complicate Play App Signing; disclosure risks malicious signing under the project's identity.

For a **new fork**, generate your own key on a trusted machine outside that fork's repository:

```sh
mkdir -p "$HOME/.private/nas-watchdog-fork"
chmod 700 "$HOME/.private/nas-watchdog-fork"
keytool -genkeypair -v \
  -keystore "$HOME/.private/nas-watchdog-fork/release.p12" \
  -storetype PKCS12 -alias nas-watchdog \
  -keyalg RSA -keysize 4096 -validity 10000
```

Use a strong store/key password, keep an encrypted offline backup, then add your fork's own secrets through **Settings → Secrets and variables → Actions**. A fork must not reuse somebody else's identity, imply an official manufacturer relationship, or publish under `com.grey.naswatchdog` without deliberately managing that permanent package identity.

## 2. Prepare and test

1. Finish review on `main`; keep normal CI green.
2. Test Go, Android, web navigation/actions, API authorization, and the versioned container together.
3. Keep actions and automatic reboot off by default. Check inventory-driven hardware behavior and D6U/D8U compatibility claims.
4. Review third-party branding and original imagery in [TRADEMARKS.md](../TRADEMARKS.md).
5. Update `defaultConfig` development version metadata in `android/app/build.gradle.kts`, installation examples, store release notes, and versioned image pins.
6. Confirm no untracked `.env`, signing files, personal addresses, local SDK configuration, raw stats, secrets, or test logs are staged.
7. Choose a **new** semantic version. Never rewrite/reuse a published tag or recycle an Android version code.

Release CI sets `versionName` from the tag without `v`, and `versionCode = GITHUB_RUN_NUMBER + 1000`. This keeps production codes above the original development value and monotonically increasing on the release workflow's run-number sequence. Keep that workflow identity intact.

## 3. Publish the GitHub Release and image

From a clean reviewed checkout:

```sh
git switch main
git pull --ff-only
git tag -a v1.5.0 -m "NAS Watchdog v1.5.0"
git push origin main v1.5.0
```

Open **Actions → Release**. The jobs are:

1. **Validate:** stable tag syntax.
2. **Android app:** release unit tests/lint, signed APK and AAB, `apksigner` verification for the APK and `jarsigner`/certificate checks for the bundle.
3. **GHCR image:** race-enabled Go tests, `linux/amd64` build, version/source/revision labels, pushes exact version + `latest`, registry verification.
4. **Release:** downloads staged app artifacts and publishes the APK, AAB, and combined `SHA256SUMS.txt`.

The first publication of a GHCR package may need its visibility changed to **public** in package settings. The public `greyyk/nas-watchdog` package should be linked to this repository through its source label. Use `packages: write` on only the publishing job; use no long-lived registry PAT in source.

The repository was formerly named `greyyk/zettlabs-nas-watchdog`. GitHub repository links redirect, but GHCR repositories do not become a universal redirect system. New deployments must use **ghcr.io/greyyk/nas-watchdog**. Retain `ghcr.io/greyyk/zettlabs-nas-watchdog:1.4.0` as an explicitly pinned old image only when still needed; upgrade deliberately.

## 4. Verify a Release locally

Download the APK, AAB, and `SHA256SUMS.txt` into one directory. On macOS:

```sh
shasum -a 256 -c SHA256SUMS.txt
adb install -r nas-watchdog-1.5.0-android.apk
```

On Linux use `sha256sum -c SHA256SUMS.txt`. For the AAB, use `jarsigner -verify nas-watchdog-1.5.0-play.aab`; **do not use apksigner on an AAB**. Inspect the bundle in Play App Bundle Explorer after upload and compare its public certificates with the existing persistent release certificate.

A debug APK signed by another local certificate cannot be updated directly by a production APK. Back up/re-enter connection settings and uninstall the old debug package if Android requires it. Do not uninstall production users' apps as a routine migration strategy. Installing the GitHub APK and Play edition works in place only when the application ID and effective **app signing certificate** are the same and the update version code is higher.

## 5. Install or update the container on ZettOS

Enable SSH and built-in Docker first using [SETUP.md](SETUP.md). From a reviewed release checkout's `watchdog/` directory, create `.env` if needed:

```sh
cp .env.example .env
openssl rand -hex 32
```

Set that random output in `WD_API_TOKEN`, check `POOL_HOST_PATH` against `findmnt -t btrfs`, and pin the published image:

```dotenv
WATCHDOG_IMAGE=ghcr.io/greyyk/nas-watchdog:1.5.0
```

```sh
chmod 600 .env
sudo docker compose pull
sudo docker compose up -d --no-build
sudo docker compose ps
```

`pull`/`up --no-build` makes the remote-image choice explicit and prevents a local source rebuild. Use no inline comment after `WATCHDOG_IMAGE=...`, and keep the name digest/version deliberate rather than silently tracking `latest`. For a source build, remove/comment out `WATCHDOG_IMAGE` and run `sudo docker compose up -d --build`.

A public GHCR package needs no registry credential. The watchdog `WD_API_TOKEN` is separate from Docker registry access. Recreating the container clears in-memory history and bounded event history; the `watchdog-data` volume retains its selected alert/reboot operational state when you keep the same Compose project.

## 6. Google Play is a separate controlled step

Follow **[PLAY_STORE.md](PLAY_STORE.md)**. In particular:

- Choose Play App Signing and deliberately preserve or separate the GitHub/Play **app-signing** identity on first setup.
- Upload the versioned **`.aab`**, not the GitHub APK.
- Supply truthful privacy/Data Safety, foreground-service demo/declaration, original listing graphics/screenshots, compatibility limitations, and support contacts.
- Start with Internal testing and install the Play-delivered version on real test devices. Account-specific closed testing may be required before production.

There are no Play Console API credentials in GitHub Actions, and no automatic production Play rollout. Do not grant a cloud CI job production publishing rights without an independent security review.

## Failed or incomplete releases

The Release job depends on the Android and container jobs; their failure prevents a normal Release from being created. If GHCR was updated but the final Release failed, fix permissions/network/root cause and use a new patch version for an unambiguous recovery rather than retagging consumed image tags. GitHub asset uploads and Play Console submission are separate operations.

Container publication never deploys to a live NAS. App publication never installs onto a phone. Perform an actual upgrade validation before recommending a version broadly.
