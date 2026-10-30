#!/usr/bin/env python3
"""Check that Aether-family native libraries survive APK packaging."""
from pathlib import Path
from zipfile import ZipFile
import sys

APKS = sorted(Path("app/build/outputs/apk").rglob("*.apk"))
REQUIRED = ("libaether.so", "libpsiphon-tunnel-core.so", "liblyrebird.so")

def main() -> int:
    if not APKS:
        print("No APKs found under app/build/outputs/apk", file=sys.stderr)
        return 1

    errors = []
    for apk in APKS:
        try:
            with ZipFile(apk) as archive:
                names = set(archive.namelist())
                present_abis = sorted({
                    name.split("/")[1]
                    for name in names
                    if name.startswith("lib/") and len(name.split("/")) > 2
                    and name.endswith(".so")
                })
                for abi in present_abis:
                    aether = f"lib/{abi}/libaether.so"
                    if aether not in names:
                        continue
                    missing = [f"lib/{abi}/{name}" for name in REQUIRED if f"lib/{abi}/{name}" not in names]
                    if missing:
                        errors.append(f"{apk}: incomplete {abi} Aether payload: {', '.join(missing)}")
                    else:
                        print(f"OK {apk.name}: {abi} Aether/Psiphon/Lyrebird packaged")
        except Exception as exc:
            errors.append(f"{apk}: {exc}")

    if errors:
        for error in errors:
            print(f"ERROR {error}", file=sys.stderr)
        return 1
    print("APK native payload check completed.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
