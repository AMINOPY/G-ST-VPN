#!/usr/bin/env bash
set -euo pipefail

# Reinstall the pinned upstream Aether runtime into the Android jniLibs payload.
# v2.4.0 is built for Android API 29+, so the app gates Aether on Android 10+.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="v2.4.0"
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

  tar -tzf "$archive" | grep -qx 'aether' || {
    echo "$asset does not contain the Aether executable" >&2; exit 1;
  }
  tar -tzf "$archive" | grep -qx 'pt/psiphon-tunnel-core' || {
    echo "$asset does not contain the Psiphon helper" >&2; exit 1;
  }
  tar -tzf "$archive" | grep -qx 'pt/lyrebird' || {
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
  "02f1a1d673cf2f3e5592c90ce7ef4d6f03e218d41a333bdd175e0da92ce2073c"
install_abi "armeabi-v7a" "aether-android-armv7.tar.gz" \
  "13a170d175eab5a64d69c6fa3c174430754f01171d616a00eba0b3598315da1d"
install_abi "x86_64" "aether-android-x86_64.tar.gz" \
  "b8d18baf6b1efe120d04109f819d08ca822c1fafeb3d61297b0e035cf095ae07"

echo "Aether $VERSION native runtime reinstall completed."
