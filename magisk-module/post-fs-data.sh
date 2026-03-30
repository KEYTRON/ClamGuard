#!/system/bin/sh
MODDIR="${0%/*}"
mkdir -p "$MODDIR/bin/arm64" "$MODDIR/db" "/sdcard/ClamGuard/quarantine"
mkdir -p "$MODDIR/var/log/clamav" "$MODDIR/var/lib/clamav" "$MODDIR/usr/etc/clamav" "$MODDIR/usr/etc/tls"
chmod 0755 "$MODDIR/bin" "$MODDIR/bin/arm64" "$MODDIR/db" "$MODDIR/lib" "$MODDIR/usr" "$MODDIR/usr/bin"
chmod 0755 "$MODDIR/bin/clamscan" "$MODDIR/bin/freshclam" "$MODDIR/bin/arm64/clamscan.bin" "$MODDIR/bin/arm64/freshclam.bin"

TERMUX_CLAMAV_DIR="/data/data/com.termux/files/usr/etc/clamav"
if [ -d "/data/data/com.termux/files/usr/etc" ]; then
  mkdir -p "$TERMUX_CLAMAV_DIR/certs"
  cp -f "$MODDIR/usr/etc/clamav/certs/clamav.crt" "$TERMUX_CLAMAV_DIR/certs/clamav.crt"
  chmod 0644 "$TERMUX_CLAMAV_DIR/certs/clamav.crt"
fi
