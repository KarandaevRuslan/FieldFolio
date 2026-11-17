#!/usr/bin/env python3
"""Run the same verified ktfmt release locally and in CI; vendor JS is outside its scope."""
from hashlib import sha256
from pathlib import Path
import argparse
import os
import subprocess
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
VERSION = '0.55'
DIGEST = 'd13324023754112797d828e2f92117196190f7617d16ad1d1765e5be768cc5c2'
URL = f'https://repo.maven.apache.org/maven2/com/facebook/ktfmt/{VERSION}/ktfmt-{VERSION}-with-dependencies.jar'


def formatter():
    """Cache a formatter only after verifying its pinned SHA-256."""
    target = ROOT / '.tools' / f'ktfmt-{VERSION}.jar'
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists() or sha256(target.read_bytes()).hexdigest() != DIGEST:
        with urllib.request.urlopen(URL, timeout=120) as response:
            content = response.read()
        if sha256(content).hexdigest() != DIGEST:
            raise ValueError('ktfmt download checksum mismatch')
        with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as output:
            temporary = Path(output.name)
            output.write(content)
        temporary.replace(target)
    return target


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Fail if formatting would change sources')
    args = parser.parse_args()
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    files = sorted(path for path in (ROOT / 'app/src').rglob('*.kt'))
    options = ['--google-style']
    if args.check:
        options += ['--dry-run', '--set-exit-if-changed']
    with tempfile.TemporaryDirectory() as directory:
        arguments = Path(directory) / 'sources.txt'
        arguments.write_text('\n'.join(options + [str(path) for path in files]) + '\n')
        return subprocess.run([java, '-jar', str(formatter()), '@' + str(arguments)], cwd=ROOT).returncode


if __name__ == '__main__':
    raise SystemExit(main())
