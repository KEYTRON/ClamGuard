#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="$(tr -d '\n' <"$PROJECT_DIR/VERSION")"
DIST_DIR="$PROJECT_DIR/dist/v$VERSION"

mkdir -p "$DIST_DIR"

if [[ -n "${RELEASE_KEYSTORE:-}" ]]; then
  APK_NAME="ClamGuard-v$VERSION-arm64-v8a.apk"
else
  APK_NAME="ClamGuard-v$VERSION-arm64-v8a-debug.apk"
fi

OUTPUT_APK="$DIST_DIR/$APK_NAME" "$PROJECT_DIR/build-tools/build-apk.sh"
"$PROJECT_DIR/release-tools/package-magisk-module.sh" "$DIST_DIR"

(
  cd "$DIST_DIR"
  sha256sum ./*.apk ./*.zip > SHA256SUMS.txt
)

echo "Release bundle ready in: $DIST_DIR"
ls -1 "$DIST_DIR"
