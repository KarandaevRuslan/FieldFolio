#!/usr/bin/env python3
"""Reject missing/stale web output without executing notebook source."""
from hashlib import sha256
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def check(root=ROOT):
    manifest = json.loads((root / 'app/src/main/assets/web-assets.json').read_text())
    if manifest.get('version') != 1:
        raise ValueError('Unsupported web asset manifest')
    expected = set()
    for part in ['src', 'scripts', 'public', 'document-public', 'vendor']:
        expected.update(p.relative_to(root).as_posix() for p in (root / 'acode-web' / part).rglob('*') if p.is_file())
    expected.update('acode-web/' + name for name in ['package.json', 'package-lock.json', 'vite.config.mjs', 'editor.html', 'document.html', 'document-host.html'])
    expected.update(p.relative_to(root).as_posix() for p in (root / 'docs/api').rglob('*') if p.is_file())
    if expected != {item['path'] for item in manifest['inputs']}:
        raise ValueError('Web source inventory changed; run npm ci && npm run build in acode-web')
    for item in manifest['inputs'] + manifest['assets']:
        path = (root / item['path']).resolve()
        if not path.is_relative_to(root.resolve()) or not path.is_file() or sha256(path.read_bytes()).hexdigest() != item['sha256']:
            raise ValueError('Web source/output is stale: ' + item['path'])
    for entry in ['acode/editor.html', 'document/document.html', 'document/document-host.html']:
        page = root / 'app/src/main/assets' / entry
        for ref in re.findall(r'(?:src|href)="(\./[^\"]+)"', page.read_text()):
            path = (page.parent / ref).resolve()
            if not path.is_relative_to((root / 'app/src/main/assets').resolve()) or not path.is_file():
                raise ValueError('Missing bundled resource: ' + ref)
    return len(manifest['assets'])


if __name__ == '__main__':
    try:
        print(f'Web assets: {check()} files verified')
    except (OSError, ValueError, KeyError) as error:
        sys.exit(str(error))
