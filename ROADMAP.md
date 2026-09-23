# ClamGuard roadmap

Stage: 0.2.1

Stages go in order: `[x]` is done, `[ ]` is planned. The current stage is the first unfinished one.

## Scanning
- [x] ClamAV engine (Cisco Talos) inside the app, no root required
- [x] Quick, full and selective scans with progress
- [x] Scanning APKs and shared storage
- [x] Virus database updates, including scheduled ones
- [x] Scans run only once databases are installed

## Handling threats
- [x] Quarantine, delete or ignore findings
- [x] Quarantine screen with file restore
- [x] Real-time protection: checking apps as they are installed

## Reliability on Android
- [x] Scans and database updates in a foreground service
- [x] Progress and results survive switching apps
- [x] Extended root mode through a Magisk module

## Distribution
- [x] GitHub releases: `arm64-v8a` APK and the Magisk module as separate files
- [x] CI build on every push
- [x] Own F-Droid repository on keytron-prime.org
- [ ] Release-signed APK instead of a debug-signed one
- [ ] APK for 32-bit devices (`armeabi-v7a`) after porting the ClamAV runtime
