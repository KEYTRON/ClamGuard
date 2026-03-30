#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
BUILD_TOOLS_DIR="$(find "$SDK_DIR/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
PLATFORM_DIR="$(find "$SDK_DIR/platforms" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
ANDROID_JAR="$PLATFORM_DIR/android.jar"
APP_VERSION="$(tr -d '\n' <"$PROJECT_DIR/VERSION" 2>/dev/null || printf '0.0.0')"
BUILD_DIR="$PROJECT_DIR/build"
ASSETS_DIR="$PROJECT_DIR/assets"
NATIVE_LIBS_DIR="$PROJECT_DIR/native-libs"
GEN_DIR="$BUILD_DIR/generated"
CLASSES_DIR="$BUILD_DIR/classes"
DEX_DIR="$BUILD_DIR/dex"
COMPILED_RES="$BUILD_DIR/compiled-res.zip"
UNALIGNED_APK="$BUILD_DIR/clamguard-unaligned.apk"
UNSIGNED_APK="$BUILD_DIR/clamguard-unsigned.apk"
ALIGNED_APK="$BUILD_DIR/clamguard-aligned.apk"
SIGNED_APK="${OUTPUT_APK:-$BUILD_DIR/clamguard-debug.apk}"
KEYSTORE="$BUILD_DIR/debug.keystore"
SIGNING_MODE="debug"

if [[ -n "${RELEASE_KEYSTORE:-}" ]]; then
  : "${RELEASE_KEY_ALIAS:?RELEASE_KEY_ALIAS is required when RELEASE_KEYSTORE is set}"
  : "${RELEASE_STORE_PASS:?RELEASE_STORE_PASS is required when RELEASE_KEYSTORE is set}"
  : "${RELEASE_KEY_PASS:?RELEASE_KEY_PASS is required when RELEASE_KEYSTORE is set}"
  SIGNING_MODE="release"
fi

mkdir -p "$BUILD_DIR" "$GEN_DIR" "$CLASSES_DIR" "$DEX_DIR" "$(dirname "$SIGNED_APK")"
rm -rf "$GEN_DIR" "$CLASSES_DIR" "$DEX_DIR"
mkdir -p "$GEN_DIR" "$CLASSES_DIR" "$DEX_DIR"
rm -f "$COMPILED_RES" "$UNALIGNED_APK" "$UNSIGNED_APK" "$ALIGNED_APK" "$SIGNED_APK"

if [[ ! -x "$BUILD_TOOLS_DIR/aapt2" ]]; then
  echo "Missing aapt2 in $BUILD_TOOLS_DIR" >&2
  exit 1
fi

if [[ ! -f "$ANDROID_JAR" ]]; then
  echo "Missing android.jar in $PLATFORM_DIR" >&2
  exit 1
fi

"$BUILD_TOOLS_DIR/aapt2" compile --dir "$PROJECT_DIR/res" -o "$COMPILED_RES"
"$BUILD_TOOLS_DIR/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$PROJECT_DIR/AndroidManifest.xml" \
  --java "$GEN_DIR" \
  --min-sdk-version 26 \
  --target-sdk-version 33 \
  $(if [[ -d "$ASSETS_DIR" ]]; then printf -- '-A %q ' "$ASSETS_DIR"; fi) \
  -o "$UNALIGNED_APK" \
  "$COMPILED_RES"

find "$PROJECT_DIR/src" "$GEN_DIR" -name '*.java' | sort > "$BUILD_DIR/java-sources.txt"

javac \
  -source 8 \
  -target 8 \
  -Xlint:-options \
  -classpath "$ANDROID_JAR" \
  -d "$CLASSES_DIR" \
  @"$BUILD_DIR/java-sources.txt"

"$BUILD_TOOLS_DIR/d8" \
  --lib "$ANDROID_JAR" \
  --min-api 26 \
  --output "$DEX_DIR" \
  $(find "$CLASSES_DIR" -name '*.class' | sort)

cp "$UNALIGNED_APK" "$UNSIGNED_APK"
(
  cd "$DEX_DIR"
  zip -q -j "$UNSIGNED_APK" classes.dex
)
if [[ -d "$NATIVE_LIBS_DIR/lib" ]]; then
  (
    cd "$NATIVE_LIBS_DIR"
    zip -q -r "$UNSIGNED_APK" lib
  )
fi

if [[ -x "$BUILD_TOOLS_DIR/zipalign" ]]; then
  "$BUILD_TOOLS_DIR/zipalign" -f 4 "$UNSIGNED_APK" "$ALIGNED_APK"
else
  cp "$UNSIGNED_APK" "$ALIGNED_APK"
fi

if [[ "$SIGNING_MODE" == "release" ]]; then
  "$BUILD_TOOLS_DIR/apksigner" sign \
    --ks "$RELEASE_KEYSTORE" \
    --ks-key-alias "$RELEASE_KEY_ALIAS" \
    --ks-pass "pass:$RELEASE_STORE_PASS" \
    --key-pass "pass:$RELEASE_KEY_PASS" \
    --v4-signing-enabled false \
    --out "$SIGNED_APK" \
    "$ALIGNED_APK"
else
  if [[ ! -f "$KEYSTORE" ]]; then
    keytool -genkeypair \
      -keystore "$KEYSTORE" \
      -storepass android \
      -keypass android \
      -alias androiddebugkey \
      -dname "CN=Android Debug,O=Android,C=US" \
      -keyalg RSA \
      -keysize 2048 \
      -validity 10000
  fi

  "$BUILD_TOOLS_DIR/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --v4-signing-enabled false \
    --out "$SIGNED_APK" \
    "$ALIGNED_APK"
fi

rm -f "$SIGNED_APK.idsig"

echo "Built APK: $SIGNED_APK"
echo "Version: $APP_VERSION"
echo "Signing: $SIGNING_MODE"
