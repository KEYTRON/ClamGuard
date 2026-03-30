# ClamGuard Magisk Module

This directory stores the versioned Magisk module metadata and wrapper scripts for ClamGuard.

The final release zip is assembled by:

- taking these tracked files
- overlaying the runtime payload from `CLAMGUARD_MAGISK_DIR`
- emitting `ClamGuard-vX.Y.Z-magisk-module.zip`

The payload directory itself is intentionally not committed here because it contains large runtime binaries and virus databases.
