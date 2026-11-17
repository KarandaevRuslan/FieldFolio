#!/usr/bin/env python3
"""Validate a release tag against the single Android version declaration."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SEMVER = r'(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?'


def properties(root=ROOT):
    return dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines()
                if '=' in line and not line.lstrip().startswith('#'))


def validate_tag(tag, version):
    if not re.fullmatch('v' + SEMVER, tag) or tag != 'v' + version:
        raise ValueError(f'Tag must be v{version}; update gradle.properties and the web package versions together.')


def main():
    props = properties()
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python3 scripts/release/check_tag.py v<versionName>')
    validate_tag(sys.argv[1], props['fieldfolio.versionName'])
    if int(props['fieldfolio.versionCode']) <= 0:
        raise ValueError('versionCode must be positive')
    print(f'Validated {sys.argv[1]} (versionCode {props["fieldfolio.versionCode"]})')


if __name__ == '__main__':
    main()
