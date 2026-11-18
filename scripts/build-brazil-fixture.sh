#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ "$(uname -s)" != Darwin ]] || [[ "$(uname -m)" != arm64 ]]; then
  echo "This test build requires an Apple Silicon Mac." >&2
  exit 1
fi

NDK="${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to an installed Android NDK}"
LINKER="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/x86_64-linux-android26-clang"
if [[ ! -x "$LINKER" ]]; then
  echo "Android x86_64 NDK linker not found: $LINKER" >&2
  exit 1
fi

export CARGO_TARGET_DIR="$ROOT/target/fixture-cargo"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$LINKER"
export IPHONEOS_DEPLOYMENT_TARGET=17.0
FEATURE=experimental-brazil-fixtures
OUT="$ROOT/target/fixture-bindings"

mkdir -p "$OUT/kotlin" "$OUT/swift" "$OUT/android-jni/x86_64" "$OUT/ios-headers"
cargo build --offline -p basquete-app-ffi --lib --features "$FEATURE"
cargo run --offline -q -p basquete-app-ffi --features "$FEATURE" --bin uniffi-bindgen -- generate \
  --library "$CARGO_TARGET_DIR/debug/libbasquete_app_ffi.dylib" \
  --language kotlin --no-format --out-dir "$OUT/kotlin"
cargo run --offline -q -p basquete-app-ffi --features "$FEATURE" --bin uniffi-bindgen -- generate \
  --library "$CARGO_TARGET_DIR/debug/libbasquete_app_ffi.dylib" \
  --language swift --no-format --out-dir "$OUT/swift"
cargo build --offline -p basquete-app-ffi --lib --features "$FEATURE" --target x86_64-linux-android
cargo build --offline -p basquete-app-ffi --lib --features "$FEATURE" --target aarch64-apple-ios-sim
cp "$CARGO_TARGET_DIR/x86_64-linux-android/debug/libbasquete_app_ffi.so" "$OUT/android-jni/x86_64/"
cp "$OUT/swift/BasqueteAppFFIFFI.h" "$OUT/ios-headers/"
cp "$OUT/swift/BasqueteAppFFIFFI.modulemap" "$OUT/ios-headers/module.modulemap"

STAGING="$(mktemp -d "$ROOT/target/fixture-xcframework.XXXXXXXX")"
xcodebuild -create-xcframework \
  -library "$CARGO_TARGET_DIR/aarch64-apple-ios-sim/debug/libbasquete_app_ffi.a" \
  -headers "$OUT/ios-headers" -output "$STAGING/basquete_app_ffi.xcframework"
ditto "$STAGING/basquete_app_ffi.xcframework" "$OUT/basquete_app_ffi.xcframework"
echo "Fixture bindings and libraries ready in $OUT"
