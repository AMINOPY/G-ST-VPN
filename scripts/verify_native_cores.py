#!/usr/bin/env python3
"""Verify the bundled Aether-family ELF payloads before Android packaging."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1] / "app" / "libs"
REQUIRED = {
    "arm64-v8a": {
        "libaether.so": (183, 2),
        "libpsiphon-tunnel-core.so": (183, 2),
        "liblyrebird.so": (183, 2),
    },
    "armeabi-v7a": {
        "libaether.so": (40, 1),
        "libpsiphon-tunnel-core.so": (40, 1),
        "liblyrebird.so": (40, 1),
    },
    "x86_64": {
        "libaether.so": (62, 2),
        "libpsiphon-tunnel-core.so": (62, 2),
        "liblyrebird.so": (62, 2),
    },
}

def verify(path: Path, machine: int, elf_class: int) -> None:
    if not path.is_file():
        raise ValueError(f"missing {path.relative_to(ROOT)}")
    data = path.read_bytes()[:20]
    if len(data) < 20 or data[:4] != b"\x7fELF":
        raise ValueError(f"not a valid ELF header: {path.relative_to(ROOT)}")
    if data[4] != elf_class:
        raise ValueError(f"wrong ELF class in {path.relative_to(ROOT)}")
    if data[5] != 1:
        raise ValueError(f"unsupported ELF endianness in {path.relative_to(ROOT)}")
    actual_machine = int.from_bytes(data[18:20], "little")
    if actual_machine != machine:
        raise ValueError(
            f"wrong ELF machine in {path.relative_to(ROOT)}: "
            f"expected {machine}, got {actual_machine}"
        )
    if path.stat().st_size < 1024 * 1024 and path.name in {
        "libaether.so", "libpsiphon-tunnel-core.so", "liblyrebird.so"
    }:
        raise ValueError(f"suspiciously small core payload: {path.relative_to(ROOT)}")

def main() -> int:
    errors = []
    for abi, binaries in REQUIRED.items():
        for name, (machine, elf_class) in binaries.items():
            path = ROOT / abi / name
            try:
                verify(path, machine, elf_class)
                print(f"OK {abi}/{name}")
            except (OSError, ValueError) as exc:
                errors.append(str(exc))
                print(f"ERROR {exc}", file=sys.stderr)

    # x86 is still a build ABI for the rest of the app, but this repository does not
    # currently ship the Aether/Psiphon/Lyrebird trio for it. Report this explicitly.
    x86_dir = ROOT / "x86"
    if x86_dir.exists() and any((x86_dir / name).exists() for name in REQUIRED["arm64-v8a"]):
        print("NOTICE x86 contains a partial Aether payload; it is not treated as supported.")

    if errors:
        return 1
    print("Aether, Psiphon and Lyrebird payloads are complete for the three bundled ABIs.")
    print("NOTICE x86 APKs do not include the Aether/Psiphon/Lyrebird runtime.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
