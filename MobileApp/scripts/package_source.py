#!/usr/bin/env python3
"""Create a complete, credential-free Fieldfolio source ZIP with per-file checksums."""
from hashlib import sha256
from pathlib import Path
import argparse
import os
import tempfile
import zipfile

from check_repository import ROOT, issues, source_files
from check_web_assets import check as check_web_assets


def package(root, destination):
    """Write atomically; reproducible entries preserve executable wrapper permissions."""
    root, destination = root.resolve(), destination.resolve()
    if destination.is_relative_to(root):
        raise ValueError('Write the source archive outside the source tree')
    files = sorted(path for path in source_files(root) if path.as_posix() != 'SHA256SUMS')
    bad = issues([path.as_posix() for path in files])
    if bad:
        raise ValueError('Not public source inputs: ' + ', '.join(bad))
    destination.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(dir=destination.parent, suffix='.zip')
    os.close(descriptor)
    temporary = Path(name)
    sums = []
    try:
        with zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
            for path in files:
                content = (root / path).read_bytes()
                entry = zipfile.ZipInfo('Fieldfolio/' + path.as_posix(), date_time=(2026, 1, 1, 0, 0, 0))
                entry.create_system = 3
                executable = path.as_posix() == 'gradlew'
                entry.external_attr = (0o100755 if executable else 0o100644) << 16
                entry.compress_type = zipfile.ZIP_DEFLATED
                output.writestr(entry, content)
                sums.append(sha256(content).hexdigest() + '  ' + path.as_posix())
            manifest = zipfile.ZipInfo('Fieldfolio/SHA256SUMS', date_time=(2026, 1, 1, 0, 0, 0))
            manifest.create_system = 3
            manifest.external_attr = 0o100644 << 16
            manifest.compress_type = zipfile.ZIP_DEFLATED
            output.writestr(manifest, '\n'.join(sums) + '\n')
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return len(files)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    check_web_assets()
    for path in ['gradle/wrapper/gradle-wrapper.jar', 'app/src/main/assets/models/road_damage_yolo_int8.tflite',
                 'docs/architecture/MAP.md', 'docs/VALIDATION.md', 'LICENSE.md']:
        if not (ROOT / path).is_file():
            raise ValueError('Missing source distribution input: ' + path)
    count = package(ROOT, args.destination)
    print(f'{count} source files: {args.destination.resolve()}')


if __name__ == '__main__':
    main()
