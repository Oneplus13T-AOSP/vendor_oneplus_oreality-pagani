#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check pagani/OReality module ownership and evaluated Make install destinations."""
import argparse
import collections
import hashlib
import re
import subprocess
import sys
import shutil
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SHARED = {
    'liboplus_audiox': 'odm/lib64/liboplus_audiox.so',
    'libOplusSpatializerAidl': 'odm/lib64/soundfx/libOplusSpatializerAidl.so',
    'libMNN': 'odm/lib64/libMNN.so',
    'libc++_shared': 'odm/lib64/libc++_shared.so',
}


def evaluate_make(enabled):
    makefile = f'''TARGET_COPY_OUT_ODM := odm
TARGET_ENABLE_ONEPlus_OREALITY := {str(enabled).lower()}
include {REPO / 'oreality.mk'}
$(info packages: $(PRODUCT_PACKAGES))
$(info namespaces: $(PRODUCT_SOONG_NAMESPACES))
$(info copies: $(PRODUCT_COPY_FILES))
$(info properties: $(PRODUCT_VENDOR_PROPERTIES) $(PRODUCT_SYSTEM_PROPERTIES))
all: ; @:
'''
    result = subprocess.run(['make', '--no-print-directory', '-f', '-'], input=makefile,
                            capture_output=True, text=True, check=True)
    return {key: value.split() for key, value in
            (line.split(':', 1) for line in result.stdout.splitlines() if ':' in line)}


def module_names(bp):
    # bpfmt top-level module name declarations; ignore comments/string mentions.
    return set(re.findall(r'^\s{4}name:\s*"([^"]+)"', bp, re.M))


def check(rom, device, vendor=None):
    sys.dont_write_bytecode = True
    sys.path.insert(0, str(rom / 'tools/extract-utils'))
    from extract_utils.file import File
    from extract_utils.makefiles import file_stem_package_name

    files = [File(line.strip()) for line in (device / 'proprietary-files.txt').read_text().splitlines()
             if line.strip() and not line.lstrip().startswith('#')]
    destinations = {file.dst: file for file in files}
    device_modules = {file_stem_package_name(file, can_have_stem=True)[1]
                      for file in files if file.dst.endswith('.so')}
    bp = (REPO / 'Android.bp').read_text()
    local_modules = module_names(bp)
    errors = []
    if '"vendor/oneplus/pagani"' not in bp:
        errors.append('Soong namespace must import vendor/oneplus/pagani')
    collisions = local_modules & device_modules
    if collisions:
        errors.append(f'Make-exported module collisions: {sorted(collisions)}')
    for name, path in SHARED.items():
        file = destinations.get(path)
        if file is None or file_stem_package_name(file, can_have_stem=True)[1] != name:
            errors.append(f'pagani must generate {name} from {path}; regenerate vendor from the updated device tree')
    enabled = evaluate_make(True)
    disabled = evaluate_make(False)
    if any(disabled.values()):
        errors.append(f'OREALITY=false adds build inputs: {disabled}')
    for name in ('liboplus_audiox', 'libOplusSpatializerAidl', 'libOplusAudioxAidl'):
        if name not in enabled['packages']:
            errors.append(f'OREALITY=true must request {name}')
    copies = [entry.split(':') for entry in enabled['copies']]
    outputs = [entry[1] for entry in copies]
    duplicate_outputs = [path for path, count in collections.Counter(outputs).items() if count > 1]
    if duplicate_outputs:
        errors.append(f'duplicate OReality outputs: {duplicate_outputs}')
    overlap = set(outputs) & destinations.keys()
    if overlap:
        errors.append(f'pagani/OReality install collisions: {sorted(overlap)}')
    owned = {path for path in destinations if path.startswith('odm/etc/spatializer/')}
    all_tuning = {line.strip() for line in (REPO / 'proprietary-files.txt').read_text().splitlines()
                  if line.startswith('odm/etc/spatializer/')}
    expected_extra = {f'odm/etc/spatializer/SPAT_AUDIOX_ROOMMODEL_{kind}_SPK.bin'
                      for kind in ('MOVIE', 'MUSIC')}
    if owned & all_tuning != all_tuning - expected_extra:
        errors.append('pagani must provide the 20 shared spatializer tuning inputs')
    if set(outputs) & all_tuning != expected_extra:
        errors.append('OReality must copy only the two additional spatializer room models')
    if len(copies) != 49:
        errors.append(f'expected 47 AudioX + 2 room models, found {len(copies)} copies')
    if vendor:
        generated = module_names((vendor / 'Android.bp').read_text())
        errors.extend(f'missing generated pagani module: {name}' for name in SHARED if name not in generated)
        pins = {line.split(None, 1)[1].strip(): line.split(None, 1)[0]
                for line in (REPO / 'proprietary-files.sha256sum').read_text().splitlines()
                if line.strip() and not line.startswith('#')}
        for path in (set(SHARED.values()) | owned) & pins.keys():
            file = vendor / 'proprietary' / path
            allowed = {pins[path]}
            if path == SHARED['libOplusSpatializerAidl']:
                # The updated pagani extractor adds libbase.so to this stock ELF.
                # Accept that exact fixup as well as the pinned stock input.
                from extract_utils.fixups_blob import BlobFixupCtx, blob_fixup
                with tempfile.TemporaryDirectory(prefix='oreality-spatializer-') as temp:
                    expected = Path(temp) / 'libOplusSpatializerAidl.so'
                    shutil.copyfile(REPO / 'proprietary' / path, expected)
                    blob_fixup().add_needed('libbase.so').run(
                        BlobFixupCtx(str(device)), File(path), str(expected))
                    allowed.add(hashlib.sha256(expected.read_bytes()).hexdigest())
            if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest() not in allowed:
                errors.append(f'missing or nonmatching generated provider blob: {path}')
        generated_mk = (vendor / 'pagani-vendor.mk').read_text()
        for path in owned:
            partition, relative = path.split('/', 1)
            if f'$(TARGET_COPY_OUT_{partition.upper()})/{relative}' not in generated_mk:
                errors.append(f'generated pagani does not install {path}')
    print(f'Module collisions: {len(collisions)}; copy collisions: {len(overlap)}; '
          f'OReality copies true/false: {len(copies)}/{len(disabled.get("copies", []))}')
    if not vendor:
        print('Generated vendor artifacts were not checked; regenerate vendor/oneplus/pagani before building.')
    for error in errors:
        print('ERROR:', error, file=sys.stderr)
    return int(bool(errors))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom-root', required=True, type=Path)
    parser.add_argument('--device-tree', required=True, type=Path)
    parser.add_argument('--vendor-tree', type=Path,
                        help='optional generated pagani tree; verifies modules, blobs and tuning installs')
    args = parser.parse_args()
    return check(args.rom_root, args.device_tree, args.vendor_tree)


if __name__ == '__main__':
    raise SystemExit(main())
