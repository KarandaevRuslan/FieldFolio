#!/usr/bin/env python3
"""Non-destructive policy checks for the Fieldfolio monorepo."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = (
    'MobileApp/build.gradle.kts', 'MobileApp/gradlew', 'MobileApp/acode-web/package.json',
    'Server/pom.xml', 'Server/README.md', 'docs/README.md', 'docs/ARCHITECTURE.md',
    '.github/workflows/mobile-ci.yml', '.github/workflows/server-ci.yml',
    '.github/workflows/mobile-release.yml', '.github/workflows/repository-ci.yml',
    'MobileApp/scripts/release/check_tag.py',
    'MobileApp/scripts/release/prepare_inputs.py',
    'MobileApp/scripts/release/make_signing_bundle.py',
    'MobileApp/scripts/release/create_keystore.ps1',
)
FORBIDDEN_NAMES = {'google-services.json', 'firebase-service-account.json',
                   'application.yml', 'local.properties', 'keystore.p12', '.env'}
FORBIDDEN_SUFFIXES = {'.patch', '.apk', '.aab', '.p12', '.pfx', '.jks', '.keystore', '.pem',
                      '.key', '.pt', '.pth', '.tflite', '.npy', '.npz', '.onnx', '.rar', '.7z'}
BUNDLED_TFLITE = 'MobileApp/app/src/main/assets/models/road_damage_yolo_int8.tflite'
# An existing small, intentional calibration sample is tracked with the training source.
TRACKED_CALIBRATION_SAMPLE = 'DetectionTrainProj/calibration_image_sample_data_20x128x128x3_float32.npy' 
ARCHIVE_SUFFIXES = {'.zip', '.tar', '.gz', '.tgz'}


def forbidden_path(name: str) -> bool:
    """Reject secrets, generated artifacts and exported archives from the Git index."""
    path = Path(name.replace('\\', '/'))
    parts = path.parts
    if not parts:
        return False
    if any(p in {'.gradle', '.idea', '__pycache__', 'node_modules', '.venv', 'target'}
           for p in parts):
        # Maven wrapper and tracked test fixtures are exceptions below.
        if '/.mvn/wrapper/' not in '/' + path.as_posix():
            return True
    if path.name in FORBIDDEN_NAMES or (path.name.startswith('.env.') and path.name != '.env.example'):
        return True
    if path.suffix.lower() == '.patch' and path.as_posix().startswith('MobileApp/acode-web/third_party/acode/patches/'):
        return False
    if path.as_posix() in {BUNDLED_TFLITE, TRACKED_CALIBRATION_SAMPLE}:
        return False
    if path.suffix.lower() in FORBIDDEN_SUFFIXES:
        return True
    if path.suffix.lower() in ARCHIVE_SUFFIXES:
        if path.as_posix().startswith('MobileApp/acode-web/vendor/') and path.suffix == '.tgz':
            return False
        if '/src/test/resources/' in '/' + path.as_posix() or '/tests/fixtures/' in '/' + path.as_posix():
            return False
        return True
    if path.as_posix().startswith('MobileApp/.github/'):
        return True
    if path.as_posix().startswith('DetectionTrainProj/') and any(
            p in {'runs', 'best_models', 'pretrained_models', 'source_dataset', 'test_samples'}
            for p in parts):
        return True
    return False


def main() -> int:
    missing = [p for p in REQUIRED if not (ROOT / p).is_file()]
    for p in missing:
        print('Missing required source: ' + p, file=sys.stderr)
    if (ROOT / 'MobileApp/.github').exists():
        print('Obsolete MobileApp/.github: move metadata to the root', file=sys.stderr)
        missing.append('MobileApp/.github')
    git = subprocess.run(['git', '-C', str(ROOT), 'ls-files', '-z'], capture_output=True)
    if git.returncode == 0:
        for raw in filter(None, git.stdout.split(b'\0')):
            name = raw.decode('utf-8', errors='replace')
            if forbidden_path(name):
                print('Forbidden tracked path: ' + name, file=sys.stderr)
                missing.append(name)
    else:
        print('No Git index found; skipped tracked-file scan.', file=sys.stderr)
    if missing:
        return 1
    print('Monorepo layout and tracked-file policy: OK')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
