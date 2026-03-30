#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="$(tr -d '\n' <"$PROJECT_DIR/VERSION")"
VERSION_CODE="$(tr -d '\n' <"$PROJECT_DIR/VERSION_CODE")"
DIST_DIR="${1:-$PROJECT_DIR/dist/v$VERSION}"
TEMPLATE_DIR="$PROJECT_DIR/magisk-module"
PAYLOAD_DIR="${CLAMGUARD_MAGISK_DIR:-/home/keytron46/clamguard-magisk}"
STAGE_DIR="$PROJECT_DIR/build/magisk-stage"
OUT_ZIP="$DIST_DIR/ClamGuard-v$VERSION-magisk-module.zip"

for required in bin lib usr db; do
  if [[ ! -d "$PAYLOAD_DIR/$required" ]]; then
    echo "Missing required Magisk payload directory: $PAYLOAD_DIR/$required" >&2
    exit 1
  fi
done

mkdir -p "$DIST_DIR"
rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR"

cp -R "$PAYLOAD_DIR/bin" "$STAGE_DIR/bin"
cp -R "$PAYLOAD_DIR/lib" "$STAGE_DIR/lib"
cp -R "$PAYLOAD_DIR/usr" "$STAGE_DIR/usr"
cp -R "$PAYLOAD_DIR/db" "$STAGE_DIR/db"

if [[ -d "$PAYLOAD_DIR/var" ]]; then
  cp -R "$PAYLOAD_DIR/var" "$STAGE_DIR/var"
else
  mkdir -p "$STAGE_DIR/var/lib/clamav" "$STAGE_DIR/var/log/clamav"
fi

cp "$TEMPLATE_DIR/README.md" "$STAGE_DIR/README.md"
cp "$TEMPLATE_DIR/customize.sh" "$STAGE_DIR/customize.sh"
cp "$TEMPLATE_DIR/post-fs-data.sh" "$STAGE_DIR/post-fs-data.sh"
cp "$TEMPLATE_DIR/service.sh" "$STAGE_DIR/service.sh"
sed \
  -e "s/@VERSION@/$VERSION/g" \
  -e "s/@VERSION_CODE@/$VERSION_CODE/g" \
  "$TEMPLATE_DIR/module.prop.template" >"$STAGE_DIR/module.prop"

chmod 0755 "$STAGE_DIR/customize.sh" "$STAGE_DIR/post-fs-data.sh" "$STAGE_DIR/service.sh"
find "$STAGE_DIR/bin" -type f -exec chmod 0755 {} +

rm -f "$OUT_ZIP"
(
  cd "$STAGE_DIR"
  zip -qr "$OUT_ZIP" module.prop customize.sh post-fs-data.sh service.sh bin lib usr var db README.md
)

echo "Built Magisk module: $OUT_ZIP"
