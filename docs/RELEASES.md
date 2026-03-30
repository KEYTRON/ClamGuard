# Releases

## What gets published

Each GitHub Release should contain separate assets for each install path:

- `ClamGuard-vX.Y.Z-arm64-v8a.apk`
- `ClamGuard-vX.Y.Z-magisk-module.zip`

Do not bundle the APK and the Magisk module into one archive.

## Current support matrix

| Artifact | Status | Notes |
|---|---|---|
| `arm64-v8a` APK | ready | Current Android app runtime target |
| `armeabi-v7a` APK | not ready | Publish only after a real 32-bit runtime port and device verification |
| Magisk module zip | ready | Separate root-side payload for extended mode |

## Version source

Release scripts read:

- `VERSION`
- `VERSION_CODE`

Update both before cutting a new release.

## APK signing

`build-tools/build-apk.sh` supports two signing modes:

- debug signing by default
- release signing when these environment variables are provided:
  - `RELEASE_KEYSTORE`
  - `RELEASE_KEY_ALIAS`
  - `RELEASE_STORE_PASS`
  - `RELEASE_KEY_PASS`

If release signing variables are missing, the release artifact is intentionally named with `-debug`.

## Magisk module packaging

The repository tracks the versioned Magisk metadata and wrapper scripts under [magisk-module](/home/keytron46/git/ClamGuard/magisk-module).

The heavy runtime payload is packaged from a staging directory defined by:

- `CLAMGUARD_MAGISK_DIR`

If it is unset, the scripts default to:

- `/home/keytron46/clamguard-magisk`

The staging directory must contain:

- `bin/`
- `lib/`
- `usr/`
- `db/`

`var/` is optional and will be created if missing.

## Build commands

Build only the APK:

```bash
cd /home/keytron46/git/ClamGuard
./build-tools/build-apk.sh
```

Build the Magisk module zip:

```bash
cd /home/keytron46/git/ClamGuard
./release-tools/package-magisk-module.sh
```

Build the full release bundle:

```bash
cd /home/keytron46/git/ClamGuard
./release-tools/build-release-artifacts.sh
```

The full bundle writes to:

- `dist/vX.Y.Z/`

and includes:

- the APK
- the Magisk module zip
- `SHA256SUMS.txt`
