# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog.

## [Unreleased]

- Planned `armeabi-v7a` APK publishing after a full 32-bit ClamAV runtime port and verification.
- Planned quarantine management screen with restore flow.

## [0.2.2] - 2026-11-11

### Fixed

- The engine readiness check reported installed `.cvd` databases as `Missing` and disabled the scan buttons: a single `ls` over several globs failed whenever one extension had no files. Each extension is now checked separately, using the same list as the in-app database check.
- APKs now carry `versionCode`/`versionName` in the manifest (0.2.0 and 0.2.1 APKs were built without them).

### Notes

- Scans run entirely on the device with throttled CPU use, so they are slower than cloud-based antivirus checks. This is deliberate: the earlier unthrottled scanner kept the CPU at 100%, heated phones to about 45 °C and made them unusable during a scan. There is no 100% load mode, for safety.
- First release published in the KEYTRON Prime F-Droid repository, signed with a dedicated release key (different from the debug-signed GitHub APKs).

## [0.2.0] - 2026-03-30

### Added

- Android-first `ClamGuard` app project with shared-storage scanning, database updates, ignore/quarantine/delete actions, and optional root-enhanced mode.
- Built-in no-root-first ClamAV runtime path for the Android app.
- Release asset convention for GitHub Releases:
  - `ClamGuard-vX.Y.Z-arm64-v8a.apk`
  - `ClamGuard-vX.Y.Z-magisk-module.zip`
- Versioned release tooling and Magisk module template tracked in this repository.

### Changed

- Repository documentation now explicitly attributes [ClamAV by Cisco Talos](https://github.com/Cisco-Talos/clamav) as the upstream engine base.
- Release packaging now treats the APK and the Magisk module as separate install artifacts.
