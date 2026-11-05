# Releasing NAS Watchdog

A pushed stable version tag produces all three distribution artifacts:

| Artifact | Destination |
|---|---|
| Signed Android APK | GitHub Release assets |
| APK SHA-256 checksum | Same Release, in `SHA256SUMS.txt` |
| Linux AMD64 container | GitHub Container Registry (GHCR), tagged with the version and `latest` |

For the repository at `github.com/<owner>/<repository>`, the image is:

```text
ghcr.io/<owner>/<repository>:<version>
```

The workflow rejects nonstandard tag names, runs tests, creates version metadata, verifies the APK signature and pushed container, and only then publishes the GitHub Release with generated notes.

## 1. One-time Android release signing

**Do not skip this or use a debug key for public releases.** A persistent signing key lets Android recognize future APKs as updates from the same publisher. If you lose it, users may have to uninstall before installing later builds.

Generate the key on a trusted workstation, outside this repository:

```sh
mkdir -p "$HOME/.private/nas-watchdog"
chmod 700 "$HOME/.private/nas-watchdog"
keytool -genkeypair -v \
  -keystore "$HOME/.private/nas-watchdog/nas-watchdog-release.p12" \
  -storetype PKCS12 \
  -alias nas-watchdog \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Use the same strong store and key password for PKCS12 compatibility. Save the password in a password manager and make a secure offline backup of both it and the `.p12` file. Never put either one in this repository, an issue, CI logs, or a NAS stats attachment.

The certificate’s identifying name/organization can be your own public project identity; do not claim to be Zettlab.

### Add GitHub Actions secrets

Encode the keystore on the workstation.

macOS:

```sh
base64 -i "$HOME/.private/nas-watchdog/nas-watchdog-release.p12" | pbcopy
```

Linux:

```sh
base64 -w0 "$HOME/.private/nas-watchdog/nas-watchdog-release.p12"
# Copy the complete printed value to your password manager, then clear the screen.
```

Other platforms can use any standard base64 encoder, provided it produces a valid value without truncation.

In GitHub open **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Complete base64 keystore content, with no explanatory text |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `nas-watchdog` if you used the command above |
| `ANDROID_KEY_PASSWORD` | Key password; normally the same as the store password here |

The workflow decodes into the runner’s private temporary directory, checks the alias/password using `keytool`, signs the release, and deletes the runner afterward. Ordinary CI and local debug builds do not receive these secrets.

If a tag release says a signing secret is missing, configure it rather than bypassing signing. Do not generate a new key for every release.

## 2. Prepare the release

1. Keep the main branch buildable and the ordinary CI workflow green.
2. Review changes against the hardware compatibility/auth/action defaults.
3. Update release notes/docs and choose the next version using semantic versioning.
4. Ensure the source does not contain `.env`, local keystores, or personal defaults. Git ignores common local artifacts; review `git status --ignored` when in doubt.

The first release after the current app `1.3` / version code `4` could reasonably be `v1.4.0`, subject to your own versioning decision. Do not reuse existing tags.

Release tags must be exactly:

```text
vMAJOR.MINOR.PATCH
```

Examples: `v1.4.0`, `v1.4.1`, `v2.0.0`. The current simple publication flow intentionally handles stable releases, not `-rc` prereleases.

## 3. Publish

From a clean, reviewed checkout:

```sh
git tag -a v1.4.0 -m "NAS Watchdog v1.4.0"
git push origin v1.4.0
```

Open **Actions → Release**. The workflow:

1. Validates the tag.
2. Runs Android release tests/lint, signs the APK from secrets, verifies it with `apksigner`, and stages `SHA256SUMS.txt`.
3. Runs Go race tests and builds `linux/amd64` with the version embedded into the watchdog and OCI labels.
4. Pushes `ghcr.io/<owner>/<repository>:1.4.0` and `:latest` using the workflow’s short-lived `GITHUB_TOKEN`.
5. Inspects the result in GHCR.
6. Publishes the GitHub Release and uploads the signed APK and checksum after both build paths succeed.

The Android `versionName` comes from the tag without `v`. `versionCode` is the release workflow run number plus 1000, which keeps it above the existing development version code and monotonically increasing across new runs/tags. Keep the workflow path intact so the run-number sequence remains stable. Never rewrite/reuse a published tag.

After the first package publication, open the repository’s **Packages** entry and verify:

- The package is linked to the repository (the workflow supplies the OCI source label).
- Package visibility is **public** if you want anonymous users to pull it. First-published package visibility may depend on repository/account settings.
- Both the exact version and `latest` tags show `linux/amd64`.

If pushing GHCR is denied, check **Actions → General → Workflow permissions**, repository package permissions, and any organization policy that restricts `packages: write`. Do not replace the token with a broadly scoped personal access token stored publicly or permanently.

## 4. Verify the released APK

Download `nas-watchdog-<version>-android.apk` and `SHA256SUMS.txt` into the same directory.

On macOS:

```sh
shasum -a 256 -c SHA256SUMS.txt
adb install -r nas-watchdog-<version>-android.apk
```

On Linux:

```sh
sha256sum -c SHA256SUMS.txt
adb install -r nas-watchdog-<version>-android.apk
```

Users switching from a locally built debug APK to the signed release may need to uninstall the debug build first because Android treats different signing certificates as different publishers. This clears local app settings; re-enter the server/token afterward. Keep the same release key for all future releases to enable normal upgrades.

For phone-only installation, download the APK on the phone, verify the checksum on a trusted computer when possible, open the APK, and allow installation only for the file manager/browser you trust. Enter the NAS URL and token in Settings after Android connection verification.

## 5. Use the GHCR image on ZettOS

After [normal ZettOS preparation](SETUP.md#1-prepare-zettos) and copying the release’s `watchdog/` configuration:

```sh
cd /persistent/path/<repository>/watchdog
cp .env.example .env
```

Set your normal token/pool values and add:

```dotenv
WATCHDOG_IMAGE=ghcr.io/<owner>/<repository>:1.4.0
```

Then:

```sh
chmod 600 .env
sudo docker compose pull
sudo docker compose up -d --no-build
sudo docker compose ps
```

`--no-build` deliberately prevents a local source rebuild when a release image was requested. Use `WATCHDOG_IMAGE` without a line comment; replace `<owner>`, `<repository>`, and the version with complete values (no angle brackets).

Using `:<version>` rather than `:latest` is recommended so updates require an explicit reviewed choice. To update, change to the newly verified version and repeat `pull` / `up -d --no-build`. Native ZettOS source builds remain available with `sudo docker compose up -d --build` when `WATCHDOG_IMAGE` is unset or commented out.

No GHCR PAT is needed for a public package. If you deliberately keep the package private, the Docker host needs its own read-only package credentials outside the watchdog API token; do not confuse those identity systems.

## Failed or incomplete releases

The GitHub Release is the final job, so a failed APK or container build should prevent it from being created. If the package uploaded but the final Release job failed, inspect permissions and create/fix the release after resolving the cause. Avoid retagging already consumed artifacts; a new patch version is the least ambiguous recovery.

Publishing a container is not automatic NAS deployment, and publishing an APK is not automatic Android installation. Validate the actual upgrade before encouraging other users to install it.
