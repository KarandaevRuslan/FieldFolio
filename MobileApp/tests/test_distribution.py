"""Release and archive contracts are exercised against real metadata and tampered inputs."""
from hashlib import sha256
from pathlib import Path
from unittest.mock import patch
import importlib.util
import json
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from check_repository import issues, source_files
from check_web_assets import check as check_web_assets
from package_source import package

spec = importlib.util.spec_from_file_location('release_tag', ROOT / 'scripts/release/check_tag.py')
release_tag = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release_tag)


class ReleaseContractTest(unittest.TestCase):
    def test_android_web_and_lockfile_versions_agree(self):
        version = release_tag.properties()['fieldfolio.versionName']
        package_json = json.loads((ROOT / 'acode-web/package.json').read_text())
        lock = json.loads((ROOT / 'acode-web/package-lock.json').read_text())
        self.assertEqual(version, package_json['version'])
        self.assertEqual(version, lock['version'])
        self.assertEqual(version, lock['packages']['']['version'])
        self.assertGreater(int(release_tag.properties()['fieldfolio.versionCode']), 0)
        release_tag.validate_tag('v' + version, version)

    def test_wrong_version_and_malformed_tags_are_rejected(self):
        for tag in ['v2.2.1', '2.2.0', 'v02.2.0', 'v2.2', 'v2.2.0/other', 'v2.2.0\n']:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release_tag.validate_tag(tag, '2.2.0')

    def test_private_or_generated_tracked_inputs_are_rejected(self):
        paths = ['app/google-services.json', 'local.properties', '.env.production',
                 'keys/signing.jks', 'app/build/outputs/apk/debug/app.apk',
                 'acode-web/node_modules/vendor/index.js', 'verification-old/data.json']
        self.assertEqual(sorted(paths), issues(paths))
        self.assertEqual([], issues(['config/keystore.properties.example', '.env.example',
                                    'app/schemas/database/9.json', '.github/workflows/ci.yml']))


class SourceArchiveTest(unittest.TestCase):
    def test_archive_is_complete_reproducible_and_excludes_private_machine_state(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            root = base / 'source'
            inputs = {'README.md': b'readme', 'gradlew': b'#!/bin/sh\n',
                      'app/src/main/assets/model.tflite': b'TFL3',
                      '.github/workflows/ci.yml': b'name: CI',
                      'scripts/release/check_tag.py': b'print("release source")',
                      'release/SHA256SUMS': b'GENERATED OUTPUT',
                      'app/google-services.json': b'PRIVATE', '.env.production': b'PRIVATE',
                      'app/build/output.txt': b'LOCAL', 'acode-web/node_modules/a.js': b'LOCAL',
                      'local.properties': b'LOCAL', 'keys/key.jks': b'PRIVATE',
                      'SHA256SUMS': b'old manifest'}
            for name, content in inputs.items():
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(content)
            first, second = base / 'one.zip', base / 'two.zip'
            self.assertEqual(5, package(root, first))
            package(root, second)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            with zipfile.ZipFile(first) as archive:
                names = archive.namelist()
                self.assertEqual(6, len(names))
                self.assertNotIn('Fieldfolio/local.properties', names)
                self.assertNotIn('Fieldfolio/release/SHA256SUMS', names)
                self.assertIn('Fieldfolio/scripts/release/check_tag.py', names)
                self.assertEqual(0o755, archive.getinfo('Fieldfolio/gradlew').external_attr >> 16 & 0o777)
                for line in archive.read('Fieldfolio/SHA256SUMS').decode().splitlines():
                    digest, name = line.split('  ', 1)
                    self.assertEqual(digest, sha256(archive.read('Fieldfolio/' + name)).hexdigest())

    def test_output_inside_source_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(ValueError):
                package(root, root / 'source.zip')


class BundledWebContractTest(unittest.TestCase):
    def test_real_web_inputs_and_bundles_match(self):
        self.assertGreater(check_web_assets(ROOT), 0)

    def test_tampered_bundle_is_rejected(self):
        target = ROOT / 'app/src/main/assets/acode/editor.html'
        read_bytes = Path.read_bytes

        def tamper(path):
            return b'tampered' if path == target else read_bytes(path)

        with patch.object(Path, 'read_bytes', tamper), self.assertRaisesRegex(ValueError, 'stale'):
            check_web_assets(ROOT)
