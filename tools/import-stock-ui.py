#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Reproduce original pagani UI artwork/catalog from the pinned ColorOS 17 dump."""
from pathlib import Path
import argparse
import hashlib
import zipfile

REPO = Path(__file__).resolve().parent.parent
APKS = {
    'system_ext/priv-app/Settings/Settings.apk': '808818365d2beefdbf8bf2af7292b5b7b06ca97ed84ceb08e9d921a0a5dda1c8',
    'my_product/app/AudioEffectCenter/AudioEffectCenter.apk': '2186b02458829a89aeabf92c0b043d4a2bdd67534c04955a2cf29427e8954da9',
}

def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as fp:
        for chunk in iter(lambda: fp.read(1024 * 1024), b''): digest.update(chunk)
    return digest.hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dump', type=Path, nargs='?')
    parser.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    pins = {line.split(None, 1)[1]: line.split(None, 1)[0]
            for line in (REPO / 'stock-ui.sha256sum').read_text().splitlines()
            if line and not line.startswith('#')}
    if not args.verify_only:
        if args.dump is None: parser.error('provide a stock dump or --verify-only')
        for relative, digest in APKS.items():
            if sha(args.dump / relative) != digest: raise SystemExit('wrong stock APK: ' + relative)
        outputs = {}
        with zipfile.ZipFile(args.dump / list(APKS)[0]) as archive:
            for mode in ('intelligence', 'immersion', 'fidelity', 'custom'):
                name = f'oreality_mode_{mode}_renewal.webp'
                outputs['packages/ORealityControl/res/drawable-nodpi/' + name] = archive.read('res/drawable-xxhdpi-v4/' + name)
        with zipfile.ZipFile(args.dump / list(APKS)[1]) as archive:
            outputs['packages/ORealityControl/assets/multimedia_dolby_support_apps.xml'] = archive.read('assets/multimedia_dolby_support_apps.xml')
        for name, data in outputs.items():
            if hashlib.sha256(data).hexdigest() != pins[name]: raise SystemExit('asset mismatch: ' + name)
        for name, data in outputs.items():
            target = REPO / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(data)
    for name, digest in pins.items():
        if sha(REPO / name) != digest: raise SystemExit('asset mismatch: ' + name)
    print(f'Original stock UI/catalog pins verified: {len(pins)}')

if __name__ == '__main__': main()
