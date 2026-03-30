# ClamGuard

ClamGuard is an Android antivirus frontend built around ClamAV.

The project provides an Android-first UI for:
- scanning shared storage and APK files
- updating virus databases
- quarantine, delete, and ignore actions
- optional extended mode with root access

## Release Assets

GitHub Releases should publish separate install artifacts:

- `ClamGuard-vX.Y.Z-arm64-v8a.apk`
- `ClamGuard-vX.Y.Z-magisk-module.zip`

Current status:

- `arm64-v8a` APK is the only APK target ready for release right now
- `armeabi-v7a` is planned, but should not be published until the full 32-bit ClamAV runtime is built and verified
- the Magisk module is a separate root-side runtime package and should stay a separate zip

## Versioning

- Current version: `0.2.0`
- Current versionCode: `2`

Version metadata lives in:

- [VERSION](/home/keytron46/git/ClamGuard/VERSION)
- [VERSION_CODE](/home/keytron46/git/ClamGuard/VERSION_CODE)

## Building

Build the APK locally:

```bash
cd /home/keytron46/git/ClamGuard
./build-tools/build-apk.sh
```

Build release artifacts with release naming:

```bash
cd /home/keytron46/git/ClamGuard
./release-tools/build-release-artifacts.sh
```

By default this produces:

- `dist/vX.Y.Z/ClamGuard-vX.Y.Z-arm64-v8a-debug.apk`
- `dist/vX.Y.Z/ClamGuard-vX.Y.Z-magisk-module.zip`

If release signing variables are provided, the APK is emitted without the `-debug` suffix.

See [docs/RELEASES.md](/home/keytron46/git/ClamGuard/docs/RELEASES.md) for the release layout and environment variables.

## ClamAV Base

ClamGuard uses [ClamAV by Cisco Talos](https://github.com/Cisco-Talos/clamav) as its scanning engine base.

ClamAV is licensed under `GPL-2.0`, and this project is distributed under the same license family to stay compatible with that upstream base.

## License

This repository is licensed under `GPL-2.0`.

See [LICENSE](/home/keytron46/git/ClamGuard/LICENSE).
