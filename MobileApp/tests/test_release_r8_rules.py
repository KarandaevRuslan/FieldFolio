"""Guard against R8 renaming Fieldfolio enum constants used by Moshi.

This test checks configuration, NOT a running minified Android APK.
"""

from pathlib import Path
import unittest


RULES = Path(__file__).resolve().parents[1] / "app" / "proguard-rules.pro"


class ReleaseR8RulesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rules = RULES.read_text(encoding="utf-8")
        cls.enabled = "\n".join(
            line for line in cls.rules.splitlines() if not line.lstrip().startswith("#")
        )

    def test_all_app_enums_are_kept_for_moshi_reflection(self):
        self.assertRegex(
            self.enabled,
            r"(?m)^-keep\s+enum\s+com\.karandaev\.fieldfolio\.\*\*\s*\{\s*\*;\s*\}",
        )

    def test_moshi_codegen_adapters_are_retained(self):
        self.assertIn("-keep @com.squareup.moshi.JsonClass class * { *; }", self.enabled)
        self.assertIn("-keep class <1>JsonAdapter { *; }", self.enabled)

    def test_no_old_application_package(self):
        self.assertNotIn("-keep class com.karandaev.roadCrackDetector", self.enabled)

    def test_minification_not_disabled(self):
        self.assertNotIn("-dontobfuscate", self.enabled)
        self.assertNotIn("-dontshrink", self.enabled)
        self.assertNotIn("-dontoptimize", self.enabled)


if __name__ == "__main__":
    unittest.main()
