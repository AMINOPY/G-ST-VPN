#!/usr/bin/env bash
set -euo pipefail

# Reinstall the pinned upstream Aether runtime into the Android jniLibs payload.
# v2.3.0 keeps compatibility with the app minimum of Android 7 (API 24).
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="v2.3.0"
BASE="https://github.com/CluvexStudio/Aether/releases/download/$VERSION"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if command -v sha256sum >/dev/null 2>&1; then
  sha256() { sha256sum "$1" | awk '{print $1}'; }
else
  sha256() { shasum -a 256 "$1" | awk '{print $1}'; }
fi

install_abi() {
  local abi="$1" asset="$2" expected="$3"
  local archive="$WORK/$asset" unpack="$WORK/$abi"
  mkdir -p "$unpack" "$ROOT/app/libs/$abi"

  echo "Downloading Aether $VERSION for $abi"
  curl -fsSL --retry 3 --retry-delay 2 "$BASE/$asset" -o "$archive"
  local actual
  actual="$(sha256 "$archive")"
  if [ "$actual" != "$expected" ]; then
    echo "Checksum mismatch for $asset: expected $expected, got $actual" >&2
    exit 1
  fi

  tar -tzf "$archive" > "$WORK/$abi.contents.txt"
  grep -Fxq "aether" "$WORK/$abi.contents.txt" || {
    echo "$asset does not contain the Aether executable" >&2; exit 1;
  }
  grep -Fxq "pt/psiphon-tunnel-core" "$WORK/$abi.contents.txt" || {
    echo "$asset does not contain the Psiphon helper" >&2; exit 1;
  }
  grep -Fxq "pt/lyrebird" "$WORK/$abi.contents.txt" || {
    echo "$asset does not contain the Lyrebird transport helper" >&2; exit 1;
  }

  tar -xzf "$archive" -C "$unpack"
  install -m 0755 "$unpack/aether" "$ROOT/app/libs/$abi/libaether.so"
  install -m 0755 "$unpack/pt/psiphon-tunnel-core" "$ROOT/app/libs/$abi/libpsiphon-tunnel-core.so"
  install -m 0755 "$unpack/pt/lyrebird" "$ROOT/app/libs/$abi/liblyrebird.so"

  for file in libaether.so libpsiphon-tunnel-core.so liblyrebird.so; do
    test -s "$ROOT/app/libs/$abi/$file"
    chmod 0755 "$ROOT/app/libs/$abi/$file"
  done

  if [ ! -d "$ROOT/app/src/main/assets/aether-licenses/$VERSION" ]; then
    mkdir -p "$ROOT/app/src/main/assets/aether-licenses/$VERSION"
    cp -R "$unpack/LICENSES/." "$ROOT/app/src/main/assets/aether-licenses/$VERSION/"
  fi
  echo "Installed Aether $VERSION + Psiphon + Lyrebird for $abi"
}

install_abi "arm64-v8a" "aether-android-arm64.tar.gz" \
  "038748745244828ea41a808958f16a1c25053dc61db977c95ceb2d3a0b2f7970"
install_abi "armeabi-v7a" "aether-android-armv7.tar.gz" \
  "9e9c59b188ff235053ae22b9f007f31de3e9bc71c8252ef73b0d626b85b64dae"
install_abi "x86_64" "aether-android-x86_64.tar.gz" \
  "9acd35dc7bafd4bbf0f4bd8337cb26c4efe6ab0d84ec01d1a0a9fa68168b09c1"

echo "Aether $VERSION native runtime reinstall completed."
