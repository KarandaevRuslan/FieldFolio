#!/usr/bin/env python3
"""Format Kotlin sources using pinned ktfmt 0.55; identical locally and in CI."""

import argparse
from hashlib import sha256
import os
import re
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
VERSION = '0.55'
DIGEST = 'd13324023754112797d828e2f92117196190f7617d16ad1d1765e5be768cc5c2'
ARTIFACT = f'com/facebook/ktfmt/{VERSION}/ktfmt-{VERSION}-with-dependencies.jar'
URLS = (
    # The GitHub release is an alternative to Maven Central when the latter returns 403.
    f'https://github.com/Kotlin/ktfmt/releases/download/v{VERSION}/ktfmt-{VERSION}-with-dependencies.jar',
    f'https://repo1.maven.org/maven2/{ARTIFACT}',
    f'https://repo.maven.apache.org/maven2/{ARTIFACT}',
)


def matches_checksum(path: Path) -> bool:
    """Check whether a JAR matches the known Maven Central artifact."""
    if not path.is_file():
        return False
    digest = sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest() == DIGEST


def java_executable() -> str:
    return str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'


def validate_supplied_jar(path: Path) -> Path:
    """Accept the pinned binary or a user-selected JAR reporting the pinned version.

    A user-provided JAR may have a different checksum from the Maven artifact
    (for example, a release asset built independently). We still reject wrong
    versions and non-runnable JAR files. Remote downloads remain checksum-pinned.
    """
    provided = path.expanduser().resolve()
    if not provided.is_file():
        raise RuntimeError(
            f'JAR FILE NOT FOUND: {provided}\n'
            'The --jar option points to a file you must already have downloaded.\n'
            'In PowerShell check: Get-ChildItem .\\scripts\\ktfmt*.jar\n'
            'Or pass the real absolute path, e.g. --jar "C:/Users/you/Downloads/ktfmt-0.55-with-dependencies.jar"'
        )
    if matches_checksum(provided):
        return provided

    actual = sha256(provided.read_bytes()).hexdigest()
    try:
        result = subprocess.run(
            [java_executable(), '-jar', str(provided), '--version'],
            text=True, capture_output=True, check=False, timeout=30,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise RuntimeError(f'Cannot run local JAR to check ktfmt version: {provided}\n{exc}') from exc
    version_output = (result.stdout + '\n' + result.stderr).strip()
    if result.returncode != 0 or not re.search(r'(?<![\d.])0\.55(?![\d.])', version_output):
        raise RuntimeError(
            f'LOCAL JAR IS NOT VERIFIED KTFMT 0.55: {provided}\n'
            f'Actual SHA-256: {actual}\n'
            f'Java --version output (exit {result.returncode}): {version_output or "(empty)"}\n'
            'Use a runnable ktfmt 0.55 -with-dependencies.jar (not 0.65 or the plain library JAR).'
        )
    print(
        f'Note: local ktfmt 0.55 JAR differs from pinned Maven SHA-256 '
        f'(actual {actual}); --version confirmed 0.55. Continuing with explicit --jar.',
        file=sys.stderr,
    )
    return provided


def formatter(local_jar: Path | None = None) -> Path:
    """Validate an explicit local JAR or fetch a checksum-verified official JAR."""
    target = ROOT / '.tools' / f'ktfmt-{VERSION}.jar'
    if local_jar is not None:
        return validate_supplied_jar(local_jar)

    if matches_checksum(target):
        return target

    target.parent.mkdir(parents=True, exist_ok=True)
    failures = []
    for url in URLS:
        temporary = None
        try:
            request = urllib.request.Request(url, headers={
                'User-Agent': 'Fieldfolio-ktfmt/0.55',
                'Accept': 'application/java-archive,application/octet-stream,*/*',
            })
            # Stream to disk rather than holding the full fat jar in memory.
            with urllib.request.urlopen(request, timeout=120) as response:
                with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as output:
                    temporary = Path(output.name)
                    for chunk in iter(lambda: response.read(1024 * 1024), b''):
                        output.write(chunk)
            if not matches_checksum(temporary):
                raise ValueError('downloaded artifact SHA-256 does not match pinned version')
            temporary.replace(target)
            return target
        except (OSError, ValueError, urllib.error.URLError) as exc:
            failures.append(f'  {url}: {exc}')
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)

    raise RuntimeError(
        'Could not download verified ktfmt 0.55 from GitHub Releases or Maven Central:\n'
        + '\n'.join(failures)
        + '\n\nWorkaround: download ktfmt-0.55-with-dependencies.jar from '
          'the official GitHub release v0.55, then run:\n'
          '  python scripts/format_kotlin.py --jar "C:/path/to/ktfmt-0.55-with-dependencies.jar"\n'
          'A manually supplied JAR is checked using the pinned SHA-256 or its --version output.\n'
          f'Pinned Maven SHA-256: {DIGEST}'
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Fail if formatting would change sources')
    parser.add_argument('--jar', type=Path, help='Use a locally downloaded ktfmt 0.55 jar')
    args = parser.parse_args()
    java = java_executable()
    files = sorted(path for path in (ROOT / 'app/src').rglob('*.kt'))
    options = ['--google-style']
    if args.check:
        options += ['--dry-run', '--set-exit-if-changed']
    try:
        tool = formatter(args.jar)
    except RuntimeError as exc:
        print(exc, file=sys.stderr)
        return 2
    with tempfile.TemporaryDirectory() as directory:
        arguments = Path(directory) / 'sources.txt'
        arguments.write_text('\n'.join(options + [str(path) for path in files]) + '\n', encoding='utf-8')
        return subprocess.run([java, '-jar', str(tool), '@' + str(arguments)], cwd=ROOT).returncode


if __name__ == '__main__':
    raise SystemExit(main())
