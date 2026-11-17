"""Regression tests for tracked source hygiene. No network or build tools required."""
from pathlib import Path
import importlib.util
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/check_monorepo.py'
spec = importlib.util.spec_from_file_location('check_monorepo', SCRIPT)
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class TrackedFilesTest(unittest.TestCase):
    def test_automatic_dependabot_prs_disabled(self):
        root = SCRIPT.parents[1]
        self.assertFalse((root / '.github/dependabot.yml').exists())
        self.assertFalse((root / 'MobileApp/.github/dependabot.yml').exists())

    def test_generated_and_secret_inputs_rejected(self):
        for path in (
            'MobileApp/Fieldfolio-2.2.0-full-source.zip',
            'MobileApp/patch.patch',
            'Server/src/main/resources/application.yml',
            'Server/src/main/resources/keystore.p12',
            'Server/target/classes/Secret.class',
            'Server/python-yolo/.venv/pyvenv.cfg',
            'DetectionTrainProj/runs/train/results.csv',
            'DetectionTrainProj/best_models/model.pt',
            'DetectionTrainProj/exports/experimental.tflite',
            'MobileApp/app/src/main/assets/models/unapproved.tflite',
            'MobileApp/.github/workflows/ci.yml',
        ):
            with self.subTest(path=path):
                self.assertTrue(check.forbidden_path(path))

    def test_calibration_sample_is_allowed_but_other_numpy_artifacts_are_not(self):
        self.assertFalse(check.forbidden_path(
            'DetectionTrainProj/calibration_image_sample_data_20x128x128x3_float32.npy'
        ))
        self.assertTrue(check.forbidden_path('DetectionTrainProj/exports/new_sample.npy'))

    def test_sources_and_required_vendor_archive_allowed(self):
        for path in (
            'Server/pom.xml',
            'Server/src/main/resources/application_stripped.yml',
            'MobileApp/gradle/wrapper/gradle-wrapper.jar',
            'MobileApp/acode-web/vendor/nexus-0.1.20.tgz',
            'MobileApp/acode-web/third_party/acode/patches/src__cm__commandRegistry.js.patch',
            'MobileApp/app/src/main/assets/models/road_damage_yolo_int8.tflite',
            'MobileApp/docs/architecture/MAP.md',
            'MobileApp/tests/fixtures/export.zip',
            '.github/workflows/mobile-ci.yml',
        ):
            with self.subTest(path=path):
                self.assertFalse(check.forbidden_path(path))


if __name__ == '__main__':
    unittest.main()
