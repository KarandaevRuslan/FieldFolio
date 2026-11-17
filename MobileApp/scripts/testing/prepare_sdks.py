#!/usr/bin/env python3
"""Download the two Robolectric 4.14.1 SDKs before enabling the test network guard."""
from concurrent.futures import ThreadPoolExecutor
from hashlib import sha512
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
VERSIONS = ('9-robolectric-4913185-2-i7', '15-robolectric-12650502-i7')


def prepare(version):
    name = f'android-all-instrumented-{version}.jar'
    target = ROOT / 'build/robolectric-sdks' / name
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.is_file():
        return name
    base = f'https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/{version}/{name}'
    expected = urllib.request.urlopen(base + '.sha512', timeout=60).read().decode().split()[0]
    temp = target.with_suffix('.download')
    try:
        urllib.request.urlretrieve(base, temp)
        if sha512(temp.read_bytes()).hexdigest() != expected:
            raise RuntimeError(f'SDK checksum mismatch: {name}')
        temp.replace(target)
    finally:
        temp.unlink(missing_ok=True)
    return name


if __name__ == '__main__':
    with ThreadPoolExecutor(max_workers=2) as pool:
        for name in pool.map(prepare, VERSIONS):
            print(f'Ready: {name}')
