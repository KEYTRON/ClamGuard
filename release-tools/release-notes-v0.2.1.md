# ClamGuard v0.2.1

Compatibility release after the Android package id migration.

Included assets:

- `ClamGuard-v0.2.1-arm64-v8a-debug.apk`
- `ClamGuard-v0.2.1-magisk-module.zip`
- `SHA256SUMS.txt`

Highlights:

- Android package id changed from `com.keytron46.clamguard` to `com.keytron.clamguard`
- manual quick, full, and selective scans now run through a foreground service instead of the activity
- scan progress and result state are persisted so the UI can recover after switching apps
- background scan/update jobs use foreground-service execution for better Android lifecycle behavior
- old `com.keytron46.clamguard` installs should be removed before installing this release

Notes:

- the current APK asset is `arm64-v8a` only
- `armeabi-v7a` is not published yet
- the APK in this release is debug-signed
- ClamGuard uses [ClamAV by Cisco Talos](https://github.com/Cisco-Talos/clamav) as its upstream scanning engine base
