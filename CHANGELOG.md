# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog.

## [Unreleased]

- Planned `armeabi-v7a` APK publishing after a full 32-bit ClamAV runtime port and verification.
- Planned quarantine management screen with restore flow.

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
