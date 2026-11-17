#!/usr/bin/env python3
"""Audit tracked sources, or source candidates in a ZIP checkout without a Git index."""
from pathlib import Path
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
IGNORED = {'.git', '.gradle', '.kotlin', '.idea', '.vscode', 'node_modules', 'build', '.test-cache', '__pycache__', '.tools', 'coverage'}
PRIVATE = {'google-services.json', 'keystore.properties', 'local.properties', '.env'}
BAD = {'.apk', '.aab', '.apks', '.jks', '.keystore', '.p12', '.pem', '.key', '.log', '.pyc', '.hprof', '.db'}


def source_files(root=ROOT):
    """Yield public source candidates without walking dependency/cache trees or following links."""
    root = root.resolve()
    for directory, directories, files in os.walk(root, followlinks=False):
        directories[:] = sorted(name for name in directories if name not in IGNORED
                                 and not (Path(directory) == root and name == 'release')
                                 and not (Path(directory) / name).is_symlink())
        for name in sorted(files):
            path = Path(directory) / name
            if path.is_symlink():
                continue
            relative = path.relative_to(root)
            if name in PRIVATE or (name.startswith('.env') and name != '.env.example'):
                continue
            if path.suffix.lower() in BAD or name.endswith('.iml'):
                continue
            if relative.parts[0].startswith(('verification-', 'previews-')):
                continue
            if name.startswith('Fieldfolio-') and path.suffix.lower() == '.zip':
                continue
            yield relative


def issues(paths):
    errors = []
    for name in paths:
        path = Path(name)
        if (any(part in IGNORED for part in path.parts) or path.parts[:1] == ('release',) or path.name in PRIVATE
                or path.suffix.lower() in BAD or path.name.endswith('.iml')
                or (path.name.startswith('.env') and path.name != '.env.example')):
            errors.append(name)
        if path.parts and path.parts[0].startswith(('verification-', 'previews-')):
            errors.append(name)
    return sorted(set(errors))


def main():
    if (ROOT / '.git').exists():
        paths = subprocess.check_output(['git', '-C', str(ROOT), 'ls-files', '-z']).decode().split('\0')
        paths = [path for path in paths if path]
    else:
        paths = [path.as_posix() for path in source_files()]
    bad = issues(paths)
    for path in bad:
        print('Not a public source input: ' + path, file=sys.stderr)
    if bad:
        return 1
    for name in ['README.md', 'ARCHITECTURE.md', 'LICENSE.md', 'THIRD_PARTY_NOTICES.md', 'gradle/wrapper/gradle-wrapper.jar', 'app/src/main/assets/models/road_damage_yolo_int8.tflite']:
        if not (ROOT / name).is_file():
            raise SystemExit('Missing source distribution input: ' + name)
    print(f'Repository hygiene: {len(paths)} source files checked')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
