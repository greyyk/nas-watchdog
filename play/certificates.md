# Public Android signing certificates

Only public certificates belong here. PKCS12/JKS files, private keys, and passwords must never be copied into this repository. These fingerprints were checked against the owner-supplied Console export on November 5, 2026.

## Play upload

Public PEM: [upload-certificate.pem](upload-certificate.pem)

- Alias: `nas-watchdog-play-upload`
- SHA-256: `3F:1A:9C:89:55:AD:CB:C3:B0:C2:55:13:8D:71:5F:1C:F1:16:C6:A9:50:57:2C:A5:ED:C7:F9:87:78:F4:1F:03`
- Purpose: verifies developer-uploaded `nas-watchdog-<version>-play.aab`; this is **not** an app-signing identity delivered to devices.

## Play app signing — current Google-generated identities

Play's quantum-ready hybrid configuration uses three app-signing keys. The exported file names and fingerprints are:

| Console export | SHA-256 | SHA-1 | Purpose |
| --- | --- | --- | --- |
| `deployment_cert.der` | `3B:0A:59:C2:71:5A:D5:24:ED:77:4D:25:CA:F3:8E:5A:CC:97:7A:DA:7E:BC:4F:98:0D:BC:FE:37:4C:AE:24:E8` | `F3:90:EA:B5:5D:FC:1C:BB:BF:DA:9F:88:2A:F9:6B:42:16:8D:8A:0C` | Classical deployment signature used on Android 16 and lower. |
| `hybrid_classical_cert.der` | `8D:FA:C5:39:15:EE:77:86:50:44:D2:F4:07:B6:60:9D:D7:F7:A9:1C:0F:3E:CC:B7:51:1A:FF:EB:BD:F3:81:06` | `DF:76:3C:9D:2E:9C:86:46:1D:CD:C9:F0:8D:08:25:DA:36:F3:83:7C` | Classical component of the hybrid signature used on Android 17 and higher. This deliberately differs from the deployment key. |
| `hybrid_pqc_cert.der` | `5A:93:5F:29:06:B9:B5:90:6E:35:EA:9A:8A:80:D4:CF:56:C7:FD:3A:B0:F6:88:65:A6:D9:C7:3B:17:51:F6:B8` | `C2:54:27:45:51:A0:60:52:EF:96:4E:EF:66:16:68:6B:67:80:02:06` | Post-quantum ML-DSA-65 component of the hybrid signature used on Android 17 and higher. |

The three values above are **not upload-key alternatives**. Play signs delivered APKs with the applicable app-signing identities; the upload key only authenticates the developer's AAB upload. See [Google's Play App Signing guidance](https://support.google.com/googleplay/android-developer/answer/9842756).

## Existing GitHub APK signing

- Alias: `nas-watchdog`
- SHA-256: `C2:A2:05:40:07:5B:02:AF:4B:0C:CA:3C:80:31:BE:8B:8D:4E:F4:11:44:43:91:5F:60:E0:93:93:98:C3:62:FC`
- Purpose: signs GitHub release APKs and preserves direct APK-to-APK upgrades from v1.4.0.

**This GitHub certificate does not match any of the three current Play app-signing certificates.** Therefore, do not promise in-place upgrades between the existing GitHub APK and Play-delivered builds under the current Play configuration. A different classical deployment signer can require uninstall/reinstall on Android 16 and lower, which removes app-local settings. If cross-channel upgrades are required, follow [the controlled signing decision](../docs/PLAY_STORE.md#signing-strategy--decide-on-the-first-upload) before the first open-testing or production rollout and verify the resulting deployment certificate again. Future GitHub APK releases continue to use the existing GitHub signer.

## App Links and third-party API registration

If Android App Links or a fingerprint-authenticated third-party API is enabled, follow Play's current guidance and register all three app-signing fingerprints where required. A statement containing only `deployment_cert`'s fingerprint does not cover both hybrid identities on Android 17 and higher. Do not deploy an `assetlinks.json` until there is an owner-controlled HTTPS origin and the corresponding app intent filters have been deliberately configured and tested. This repository does not currently deploy that file.

Certificates can be visible publicly and do not allow signing by themselves. Report a suspected private-key disclosure privately through the repository's security policy rather than opening an issue containing secret material.
