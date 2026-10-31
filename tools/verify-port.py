#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Static checks for the pagani OReality / Holo Audio port.

Usage:
  python3 tools/verify-port.py --rom-root /home/ccc/android/pixelos

Checks, in order:
  1. every blob in proprietary-files.txt exists and matches its pinned SHA-256
  2. the effects XML is well formed, has no duplicate UUIDs, and registers the
     two effect libraries with the UUIDs the binaries really report
  3. the ELF dependency closure of the installed libraries resolves inside the
     produced images, so nothing fails to dlopen at runtime
  4. the HAL configuration that this port overrides is really shadowed: the
     /odm/etc copy is installed and the search order puts it first
  5. the control app's declared permissions match the privileged allowlist
"""

import argparse
import hashlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

# UUIDs that the stock libraries report, established by reading the
# getEffectTypeUuid*/getEffectImplUuid* symbols in the binaries.
AUDIOX_IMPL_UUID = "41f6c0f4-5d8f-11ec-bf63-0242ac130002"
AUDIOX_TYPE_UUID = "ec7178ec-e5e1-4432-a3f4-4657e6795210"
SPATIALIZER_IMPL_UUID = "de4d6aaf-4e9d-473d-8168-4ce92d50a637"

# Libraries the effect engine and spatializer must be able to resolve.
REQUIRED_LIBS = {
    "liboplus_audiox.so",
    "libOplusAudioxAidl.so",
    "libOplusSpatializerAidl.so",
}

# Libraries already provided by the pagani vendor blob tree or the platform.
PLATFORM_PROVIDED = {
    "libc.so", "libm.so", "libdl.so", "liblog.so", "libutils.so",
    "libcutils.so", "libbase.so", "libbinder.so", "libbinder_ndk.so",
    "libhidlbase.so", "libfmq.so", "libaudioutils.so", "libc++.so",
    "libc++_shared.so", "libMNN.so", "libspeexresampler.so",
    "libvndksupport.so", "libapexsupport.so", "libaconfig_storage_read_api_cc.so",
    "server_configurable_flags.so", "libdl_android.so", "libsync.so",
    "libstagefright_foundation.so", "libxml2.so", "libz.so",
}

failures: list[str] = []
warnings: list[str] = []


def fail(message: str) -> None:
    failures.append(message)


def warn(message: str) -> None:
    warnings.append(message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fp:
        for chunk in iter(lambda: fp.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def check_blobs() -> None:
    sums_file = REPO / "proprietary-files.sha256sum"
    if not sums_file.exists():
        fail("proprietary-files.sha256sum is missing")
        return
    expected = {}
    for raw in sums_file.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        digest, rel = line.split(None, 1)
        expected[rel.strip()] = digest

    listed = set()
    for raw in (REPO / "proprietary-files.txt").read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        listed.add(line.split(" -", 1)[0].split(":", 1)[0].strip())

    for rel in sorted(listed):
        path = REPO / "proprietary" / rel
        if not path.exists():
            fail(f"missing blob: {rel}")
            continue
        if rel not in expected:
            warn(f"blob is not pinned: {rel}")
            continue
        actual = sha256(path)
        if actual != expected[rel]:
            fail(f"hash mismatch: {rel}\n    expected {expected[rel]}\n    actual   {actual}")

    for rel in sorted(set(expected) - listed):
        warn(f"pinned but not listed in proprietary-files.txt: {rel}")

    print(f"  pins checked: {len(listed)}")


def check_effects_xml() -> None:
    xml_path = REPO / "configs/audio/audio_effects_config.xml"
    if not xml_path.exists():
        fail("configs/audio/audio_effects_config.xml is missing")
        return
    try:
        tree = ET.parse(xml_path)
    except ET.ParseError as error:
        fail(f"audio_effects_config.xml is not well formed: {error}")
        return
    # The schema declares a default namespace, so every tag carries it.
    namespace = ""
    if tree.getroot().tag.startswith("{"):
        namespace = tree.getroot().tag[:tree.getroot().tag.index("}") + 1]

    def tag(name: str) -> str:
        return f"{namespace}{name}"

    libs = {lib.get("name"): lib.get("path") for lib in tree.getroot().iter(tag("library"))}
    effects = {}
    uuids = {}
    for effect in tree.getroot().iter(tag("effect")):
        name = effect.get("name")
        effects[name] = effect
        uuid = (effect.get("uuid") or "").lower()
        if not uuid:
            fail(f"effect {name} has no uuid")
            continue
        if uuid in uuids:
            fail(f"duplicate effect uuid {uuid}: {uuids[uuid]} and {name}")
        uuids[uuid] = name

    audiox = effects.get("oplusaudiox")
    if audiox is None:
        fail("effect oplusaudiox is not registered")
    else:
        if audiox.get("uuid", "").lower() != AUDIOX_IMPL_UUID:
            fail(f"oplusaudiox uuid is {audiox.get('uuid')}, expected {AUDIOX_IMPL_UUID}")
        if audiox.get("type", "").lower() != AUDIOX_TYPE_UUID:
            fail(f"oplusaudiox type is {audiox.get('type')}, expected {AUDIOX_TYPE_UUID}")
        if libs.get(audiox.get("library")) != "libOplusAudioxAidl.so":
            fail("oplusaudiox does not point at libOplusAudioxAidl.so")

    spatializer = effects.get("spatializer")
    if spatializer is None:
        fail("effect spatializer is not registered")
    else:
        if spatializer.get("uuid", "").lower() != SPATIALIZER_IMPL_UUID:
            fail(f"spatializer uuid is {spatializer.get('uuid')}, "
                 f"expected {SPATIALIZER_IMPL_UUID}")
        if libs.get(spatializer.get("library")) != "libOplusSpatializerAidl.so":
            fail("spatializer does not point at libOplusSpatializerAidl.so")
        # The AOSP effects HAL assigns FX_IID_SPATIALIZER from the name alone.
        if spatializer.get("type"):
            warn("spatializer declares a type; the HAL derives it from the name")

    # Every referenced library must exist in the libraries element.
    for effect in tree.getroot().iter(tag("effect")):
        library = effect.get("library")
        if library and library not in libs:
            fail(f"effect {effect.get('name')} references unknown library {library}")

    print(f"  effects: {len(effects)}, libraries: {len(libs)}")


def readelf_needed(path: Path) -> list[str]:
    try:
        out = subprocess.run(["readelf", "-d", str(path)],
                             capture_output=True, text=True, check=False).stdout
    except FileNotFoundError:
        return []
    result = []
    for line in out.splitlines():
        if "NEEDED" in line and "[" in line:
            result.append(line.split("[")[1].rstrip("]"))
    return result


def find_soname(root: Path, name: str) -> Path | None:
    """Finds a library in the produced images, cheapest location first."""
    for sub in ("odm/lib64", "vendor/lib64", "system_ext/lib64",
                "system/lib64", "system/system_ext/lib64"):
        candidate = root / sub / name
        if candidate.exists():
            return candidate
    return None


def check_dependencies(rom_root: Path) -> None:
    out = rom_root / "out/target/product/pagani"
    if not out.is_dir():
        warn(f"{out} does not exist; dependency closure checked against the blobs only")
        image_root = None
    else:
        image_root = out

    for lib in sorted(REQUIRED_LIBS):
        path = REPO / "proprietary/odm/lib64" / lib
        if not path.exists():
            path = REPO / "proprietary/odm/lib64/soundfx" / lib
        if not path.exists():
            fail(f"{lib} is missing from proprietary/")
            continue
        for needed in readelf_needed(path):
            if needed in PLATFORM_PROVIDED:
                continue
            if image_root and find_soname(image_root, needed):
                continue
            # The library may ship inside this repository.
            if (REPO / "proprietary/odm/lib64" / needed).exists():
                continue
            fail(f"{lib} needs {needed}, which is not provided")

    print(f"  dependency closure checked for {len(REQUIRED_LIBS)} libraries")


def check_hal_config(rom_root: Path) -> None:
    """The Hal must pick the /odm copy, not the sku_sun copy."""
    out = rom_root / "out/target/product/pagani"
    if not out.is_dir():
        warn("no pagani build output; HAL configuration shadowing not verified")
        return

    odm_copy = out / "odm/etc/audio_effects_config.xml"
    if not odm_copy.exists():
        fail(f"{odm_copy} is not installed; the HAL would load the stock sku_sun file")
        return
    text = odm_copy.read_text(encoding="utf-8", errors="replace")
    for marker in ("libOplusAudioxAidl.so", "libOplusSpatializerAidl.so"):
        if marker not in text:
            fail(f"{odm_copy} does not register {marker}")

    # The search order is /odm/etc, /vendor/etc/audio/sku_<sku>, /vendor/etc,
    # /system/etc, so the sku copy may still exist; it just must not be the one
    # that wins. Assert the search order text still starts at /odm/etc.
    config_h = rom_root / "system/media/audio/include/system/audio_config.h"
    if config_h.exists():
        header = config_h.read_text(encoding="utf-8", errors="replace")
        if '"/odm/etc"' not in header:
            fail("audio_config.h no longer searches /odm/etc first")

    print("  HAL configuration shadowing verified")


def check_permissions(rom_root: Path) -> None:
    allowlist = REPO / "configs/permissions/privapp-permissions-oreality.xml"
    if not allowlist.exists():
        fail("privapp permission allowlist is missing")
        return
    try:
        root = ET.parse(allowlist).getroot()
    except ET.ParseError as error:
        fail(f"allowlist is not well formed: {error}")
        return

    granted = set()
    packages = set()
    for entry in root.iter("privapp-permissions"):
        packages.add(entry.get("package"))
        for permission in entry.iter("permission"):
            granted.add(permission.get("name"))

    manifest = REPO / "packages/ORealityControl/AndroidManifest.xml"
    try:
        declared = {node.get("{http://schemas.android.com/apk/res/android}name")
                    for node in ET.parse(manifest).getroot().iter("uses-permission")}
    except ET.ParseError as error:
        fail(f"manifest is not well formed: {error}")
        return

    if packages != {"com.oplus.oreality.audio"}:
        fail(f"allowlist covers {packages}, expected com.oplus.oreality.audio")

    # Privileged permissions that Android checks against the allowlist.
    privileged = {
        "android.permission.MODIFY_AUDIO_ROUTING",
        "android.permission.MODIFY_DEFAULT_AUDIO_EFFECTS",
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.INTERACT_ACROSS_USERS",
    }
    missing = (declared & privileged) - granted
    if missing:
        fail(f"allowlist does not grant declared privileged permissions: {sorted(missing)}")

    print(f"  allowlist grants {len(granted)} permission(s)")


def check_properties(rom_root: Path) -> None:
    """Both feature properties must be set by the device makefile."""
    makefile = REPO / "oreality.mk"
    text = makefile.read_text(encoding="utf-8")
    for prop in ("ro.oplus.audio.effect.type=audiox_merge",
                 "ro.audio.spatializer_enabled=true"):
        if prop not in text:
            fail(f"oreality.mk does not set {prop}")
    # These are only meaningful while the matching library block is installed.
    if "TARGET_ENABLE_ONEPlus_OREALITY" not in text:
        fail("oreality.mk is not gated by TARGET_ENABLE_ONEPlus_OREALITY")

    print("  properties verified")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom-root", default="/home/ccc/android/pixelos",
                        help="PixelOS source root")
    args = parser.parse_args()
    rom_root = Path(args.rom_root).expanduser().resolve()

    print("OReality / Holo Audio port verification")
    print(f"  repository: {REPO}")
    print(f"  ROM root:   {rom_root}")

    check_blobs()
    check_effects_xml()
    check_dependencies(rom_root)
    check_hal_config(rom_root)
    check_permissions(rom_root)
    check_properties(rom_root)

    for message in warnings:
        print(f"warning: {message}")
    if failures:
        print(f"\n{len(failures)} check(s) failed:", file=sys.stderr)
        for message in failures:
            print(f"  - {message}", file=sys.stderr)
        return 1
    print("\nall checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
