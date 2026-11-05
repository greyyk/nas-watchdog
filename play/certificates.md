# Public Android signing certificates

Only public certificates belong here. PKCS12/JKS files, private keys, and passwords must never be copied into this repository.

## Play upload

Public PEM: [upload-certificate.pem](upload-certificate.pem)

- Alias: `nas-watchdog-play-upload`
- SHA-256: `3F:1A:9C:89:55:AD:CB:C3:B0:C2:55:13:8D:71:5F:1C:F1:16:C6:A9:50:57:2C:A5:ED:C7:F9:87:78:F4:1F:03`
- Purpose: verifies developer-uploaded `nas-watchdog-<version>-play.aab`; this is **not** the final app signing identity delivered by Google Play.

## Existing GitHub APK / classical app signing

- Alias: `nas-watchdog`
- SHA-256: `C2:A2:05:40:07:5B:02:AF:4B:0C:CA:3C:80:31:BE:8B:8D:4E:F4:11:44:43:91:5F:60:E0:93:93:98:C3:62:FC`
- Purpose: retains the v1.4.0 GitHub APK signing identity. Import this existing classical key through Play's official enrollment/change-key instructions **before open/production rollout** if in-place GitHub ↔ Play upgrades are wanted on Android 16 and below.

Do not confuse upload and distribution certificates. In **Play Console → App integrity**, confirm the uploaded bundle's certificate matches the upload certificate above, then inspect the **classical app signing key** that Google will use on Android 16 and below. Hybrid/quantum-ready certificates can be different on Android 17+, so follow Play's currently displayed certificates and do not promise a single certificate for every future Android version.

Certificates can be visible publicly and do not allow signing by themselves. Report a suspected private-key disclosure privately through the repository's security policy rather than opening an issue containing secret material.
