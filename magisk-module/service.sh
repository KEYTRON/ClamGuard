#!/system/bin/sh
MODDIR="${0%/*}"
chmod 0755 "$MODDIR/bin/clamscan" "$MODDIR/bin/freshclam"
if [ -x "$MODDIR/bin/arm64/clamscan.bin" ]; then
  chmod 0755 "$MODDIR/bin/arm64/clamscan.bin"
fi
if [ -x "$MODDIR/bin/arm64/freshclam.bin" ]; then
  chmod 0755 "$MODDIR/bin/arm64/freshclam.bin"
fi
if [ -d "$MODDIR/lib" ]; then
  chmod 0755 "$MODDIR/lib"
fi
