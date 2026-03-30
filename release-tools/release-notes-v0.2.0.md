# ClamGuard v0.2.0

First public repository release of ClamGuard.

Included assets:

- `ClamGuard-v0.2.0-arm64-v8a-debug.apk`
- `ClamGuard-v0.2.0-magisk-module.zip`
- `SHA256SUMS.txt`

Highlights:

- Android-first antivirus UI built around ClamAV
- built-in no-root-first ClamAV runtime path for the Android app
- separate Magisk module for root-side extended mode
- release tooling and versioned Magisk module metadata tracked in the repository

Notes:

- the current APK asset is `arm64-v8a` only
- `armeabi-v7a` is not published yet
- the APK in this release is debug-signed
- ClamGuard uses [ClamAV by Cisco Talos](https://github.com/Cisco-Talos/clamav) as its upstream scanning engine base
