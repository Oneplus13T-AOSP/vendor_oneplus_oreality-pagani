#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Recursively check ELF providers and strong dynamic symbols against explicit inputs.

A reference image from a different device is useful for finding missing symbols,
but is NOT proof that pagani's installed images or linker namespaces work.
"""
import argparse
import re
import subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent


def readelf(path, *options):
    result = subprocess.run(['readelf', '--wide', *options, str(path)],
                            capture_output=True, text=True, check=True)
    return result.stdout


def symbols(path):
    defined, undefined = set(), set()
    for line in readelf(path, '--dyn-syms').splitlines():
        # IFUNC is printed as '<OS specific>: 10' by some readelf versions;
        # parsing fixed column indices silently loses libc's string functions.
        match = re.search(r'\s+(GLOBAL|WEAK)\s+(DEFAULT|PROTECTED|HIDDEN|INTERNAL)\s+(\S+)\s+(\S+)', line)
        if not match:
            continue
        binding, visibility, index, name = match.groups()
        if index == 'UND':
            if binding != 'WEAK':
                undefined.add(name)
        elif visibility in {'DEFAULT', 'PROTECTED'}:
            # A default-version export satisfies both versioned and bare uses.
            if '@@' in name:
                base, version = name.split('@@', 1)
                defined.update({base, base + '@' + version})
            else:
                defined.add(name)
    return defined, undefined


def audit(roots, effect_root):
    visited, missing = {}, []

    def visit(path):
        path = path.resolve()
        if path in visited:
            return
        if 'AArch64' not in readelf(path, '--file-header'):
            raise ValueError(f'not an AArch64 ELF: {path}')
        visited[path] = symbols(path)
        needed = re.findall(r'\(NEEDED\).*?\[(.*?)\]', readelf(path, '--dynamic'))
        for name in needed:
            provider = next((root / name for root in roots if (root / name).is_file()), None)
            if provider is None:
                missing.append((path.name, name))
            else:
                visit(provider)

    for path in (effect_root / 'liboplus_audiox.so',
                 effect_root / 'soundfx/libOplusAudioxAidl.so',
                 effect_root / 'soundfx/libOplusSpatializerAidl.so'):
        visit(path)
    exports = set().union(*(entry[0] for entry in visited.values()))
    unresolved = [(path.name, name) for path, (_, imports) in visited.items()
                  for name in sorted(imports - exports)]
    for path in visited:
        print(f'provider: {path}')
    for parent, name in missing:
        print(f'MISSING: {parent} -> {name}')
    for parent, name in unresolved:
        print(f'UNRESOLVED: {parent} -> {name}')
    print(f'{len(visited)} ELFs; {len(missing)} missing providers; '
          f'{len(unresolved)} unresolved strong/versioned symbols')
    print('This checks symbol availability only, not C++ layouts, symbol interposition, '
          'dlopen namespaces, CPU compatibility or on-device behavior.')
    return bool(missing or unresolved)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom-root', type=Path, required=True)
    parser.add_argument('--platform-image', type=Path, required=True,
                        help='explicit Android image directory providing platform/vendor DSOs')
    parser.add_argument('--reference-only', action='store_true',
                        help='required when platform-image is not a pagani build output')
    args = parser.parse_args()
    if args.platform_image.resolve().name != 'pagani' and not args.reference_only:
        parser.error('non-pagani image requires --reference-only; it cannot validate the target')
    if args.reference_only:
        print('REFERENCE ONLY: these providers do not validate the pagani target image.')
    effect_root = (REPO / 'proprietary/odm/lib64' if args.reference_only
                   else args.platform_image / 'odm/lib64')
    roots = [effect_root, effect_root / 'soundfx']
    if args.reference_only:
        roots += [args.rom_root / 'vendor/oneplus/pagani/proprietary/odm/lib64',
                  args.rom_root / 'vendor/oneplus/sm8750-common/proprietary/odm/lib64']
    roots += [args.platform_image / part for part in
              ('odm/lib64', 'vendor/lib64', 'system/lib64',
               'apex/com.android.runtime/lib64/bionic')]
    return int(audit(roots, effect_root))


if __name__ == '__main__':
    raise SystemExit(main())
