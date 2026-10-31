#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Extract the OnePlus 13T (pagani) OReality / Holo Audio blobs from a stock dump.

Usage:
  python3 extract-files.py <dump-root>
  python3 extract-files.py --verify-only

The dump root is the directory that contains `odm/`, `system/`, `system_ext/`,
`vendor/`, `product/`, `my_product/` ... as produced by dumpyara for a
ColorOS/OxygenOS 17 pagani build.

All files are copied preserving their relative path under ./proprietary/.
Generated files are excluded from git; proprietary-files.sha256sum is the
committed source of truth.
"""

import argparse
import hashlib
import shutil
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent
LIST = REPO / "proprietary-files.txt"
SUMS = REPO / "proprietary-files.sha256sum"
DEST = REPO / "proprietary"


def read_list() -> list[str]:
    entries: list[str] = []
    for raw in LIST.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        # LineageOS syntax: "src[:dst] [-SHA1]"
        line = line.split(" -", 1)[0].strip()
        src = line.split(":", 1)[0].strip()
        entries.append(src)
    return entries


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fp:
        for chunk in iter(lambda: fp.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def load_sums() -> dict[str, str]:
    if not SUMS.exists():
        return {}
    out: dict[str, str] = {}
    for raw in SUMS.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        digest, rel = line.split(None, 1)
        out[rel.strip()] = digest
    return out


def verify() -> int:
    expected = load_sums()
    if not expected:
        print(f"error: {SUMS.name} is missing or empty", file=sys.stderr)
        return 2
    failures = 0
    for rel, digest in sorted(expected.items()):
        path = DEST / rel
        if not path.exists():
            print(f"missing  {rel}", file=sys.stderr)
            failures += 1
            continue
        actual = sha256(path)
        if actual != digest:
            print(f"mismatch {rel}\n  expected {digest}\n  actual   {actual}",
                  file=sys.stderr)
            failures += 1
    if failures:
        print(f"\n{failures} file(s) failed verification", file=sys.stderr)
        return 1
    print(f"verified {len(expected)} file(s)")
    return 0


def extract(dump: Path, write_sums: bool) -> int:
    entries = read_list()
    expected = load_sums()
    missing: list[str] = []
    unpinned: list[str] = []
    copied = 0

    for rel in entries:
        src = dump / rel
        if not src.is_file():
            missing.append(rel)
            continue
        dst = DEST / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        copied += 1
        digest = sha256(src)
        if rel in expected and expected[rel] != digest:
            print(f"warning: {rel} differs from the pinned dump", file=sys.stderr)
        if rel not in expected:
            unpinned.append(f"{digest}  {rel}")

    if missing:
        print("error: missing files in the supplied dump:", file=sys.stderr)
        for rel in missing:
            print(f"  {rel}", file=sys.stderr)
        print("\nThis dump is not a ColorOS/OxygenOS 17 pagani build.", file=sys.stderr)
        return 1

    if write_sums and unpinned:
        if SUMS.exists():
            existing = SUMS.read_text(encoding="utf-8").rstrip("\n")
        else:
            existing = ("# SPDX-License-Identifier: Apache-2.0\n"
                        "# SHA-256 of every file in proprietary-files.txt.\n"
                        "# Regenerate with: python3 extract-files.py --write-sums <dump>\n")
        SUMS.write_text(existing + "\n" + "\n".join(sorted(unpinned)) + "\n",
                        encoding="utf-8")
        print(f"added {len(unpinned)} new pin(s) to {SUMS.name}")

    print(f"extracted {copied} file(s) into {DEST}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dump", nargs="?", help="stock dump root")
    parser.add_argument("--verify-only", action="store_true",
                        help="verify proprietary/ against the pinned hashes")
    parser.add_argument("--write-sums", action="store_true",
                        help="append hashes for files not yet pinned")
    args = parser.parse_args()

    if args.verify_only:
        return verify()
    if not args.dump:
        parser.error("a dump root is required unless --verify-only is used")

    dump = Path(args.dump).expanduser().resolve()
    if not (dump / "odm").is_dir() and not (dump / "system").is_dir():
        print(f"error: {dump} does not look like a full dump root", file=sys.stderr)
        return 2
    return extract(dump, args.write_sums)


if __name__ == "__main__":
    sys.exit(main())
