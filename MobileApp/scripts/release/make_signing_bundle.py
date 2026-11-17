#!/usr/bin/env python3
"""Create the single GitHub Actions signing secret from MobileApp/config/keystore.properties.

The generated Base64 ZIP contains signing credentials. Keep it private, upload it
only to the GitHub environment secret, and delete the generated file afterward.
"""
from __future__ import annotations

import argparse
import base64
import io
import os
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
REQUIRED = ('storeFile', 'storePassword', 'keyAlias', 'keyPassword')


def parse_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding='utf-8-sig').splitlines():
        line = raw.strip()
        if not line or line.startswith(('#', '!')):
            continue
        if '=' not in line:
            raise ValueError('Expected key=value entries in keystore.properties')
        key, value = line.split('=', 1)
        values[key.strip()] = value.strip()
    missing = [name for name in REQUIRED if not values.get(name)]
    if missing:
        raise ValueError('Missing required signing property: ' + ', '.join(missing))
    return values


def expand_path(raw: str) -> Path:
    """Accept unquoted and quoted Windows/Unix paths, including env variables."""
    path = raw.strip()
    if len(path) > 1 and path[0] == path[-1] and path[0] in ('"', "'"):
        path = path[1:-1].strip()
    # Java .properties commonly uses doubled backslashes in Windows paths.
    if os.name == 'nt':
        path = path.replace('\\\\', '\\')
    return Path(os.path.expandvars(os.path.expanduser(path)))


def resolve_keystore(properties_path: Path, store_file: str, override: Path | None) -> Path:
    if override is not None:
        explicit = expand_path(str(override))
        # An explicitly supplied relative path is relative to the current shell.
        explicit = explicit if explicit.is_absolute() else Path.cwd() / explicit
        if not explicit.is_file():
            raise FileNotFoundError(f'Keystore supplied with --keystore does not exist: {explicit}')
        return explicit

    path = expand_path(store_file)
    if path.is_absolute():
        candidates = [path]
    else:
        # Gradle uses rootProject.file(storeFile), which is relative to MobileApp/.
        candidates = [ROOT / path]
        # Useful when the properties file itself lives under MobileApp/config/.
        if properties_path.parent != ROOT:
            candidates.append(properties_path.parent / path)

    for idx, candidate in enumerate(candidates):
        if candidate.is_file():
            if idx > 0:
                print('Note: storeFile was found relative to the properties file, not MobileApp/.')
                print('For local Gradle signing, storeFile is resolved relative to MobileApp/.')
            return candidate

    tried = '\n  '.join(str(candidate) for candidate in candidates)
    raise FileNotFoundError(
        f'Keystore file referenced by storeFile was not found. Looked at:\n  {tried}\n'
        'Check storeFile in keystore.properties (use forward slashes on Windows),\n'
        'or supply the real key path using --keystore "D:/path/to/release-key.jks".'
    )


def make_bundle(properties_path: Path, output_path: Path, keystore_override: Path | None = None) -> None:
    properties_path = properties_path.expanduser().resolve()
    props = parse_properties(properties_path)
    keystore = resolve_keystore(properties_path, props['storeFile'], keystore_override)
    normalized = '\n'.join((
        'storeFile=ci-signing/release-key.jks',
        f'storePassword={props["storePassword"]}',
        f'keyAlias={props["keyAlias"]}',
        f'keyPassword={props["keyPassword"]}',
        '',
    )).encode('utf-8')
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', compression=zipfile.ZIP_DEFLATED) as bundle:
        bundle.writestr('keystore.properties', normalized)
        bundle.write(keystore, 'release-key.jks')
    output_path = output_path.expanduser()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(base64.b64encode(buffer.getvalue()) + b'\n')
    try:
        output_path.chmod(0o600)
    except OSError:
        pass
    print(f'Signing bundle saved to: {output_path}')
    print('Sensitive file. Upload as FIELDFOLIO_SIGNING_BUNDLE_BASE64, then delete it.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--properties', type=Path, default=ROOT / 'config' / 'keystore.properties')
    parser.add_argument('--keystore', type=Path, default=None,
                        help='Optional explicit path to the local .jks/.p12 file (overrides storeFile)')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    make_bundle(args.properties, args.output, args.keystore)
