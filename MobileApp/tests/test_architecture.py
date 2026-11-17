"""Mutation-based tests prove the architecture checker rejects actual boundary violations."""
from pathlib import Path
import importlib.util
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('architecture', ROOT / 'scripts/architecture/check.py')
architecture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(architecture)


class ArchitectureTest(unittest.TestCase):
    def fixture(self, package, body):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        path = root / 'app/src/main/kotlin' / Path(*package.split('.')) / 'Example.kt'
        path.parent.mkdir(parents=True)
        path.write_text(f'package {package}\n{body}\n')
        return root

    def test_actual_repository_has_no_boundary_violations(self):
        self.assertEqual([], architecture.violations(ROOT))

    def test_core_to_feature_is_rejected(self):
        root = self.fixture(architecture.BASE + '.core.document', 'import com.karandaev.fieldfolio.feature.document.editor.Dialog')
        self.assertTrue(any('core depends' in reason for _, reason in architecture.violations(root)))

    def test_viewer_cannot_depend_on_editor(self):
        root = self.fixture(architecture.BASE + '.feature.document.viewer', 'import com.karandaev.fieldfolio.feature.document.editor.Dialog')
        self.assertTrue(any('viewer depends' in reason for _, reason in architecture.violations(root)))

    def test_fully_qualified_dependencies_cannot_bypass_import_rules(self):
        root = self.fixture(architecture.BASE + '.core.document', 'val root = com.karandaev.fieldfolio.app.MyApplication()')
        self.assertTrue(architecture.violations(root))

    def test_documentation_and_string_literals_do_not_create_false_edges(self):
        root = self.fixture(architecture.BASE + '.core.document', '// com.karandaev.fieldfolio.app.Root\nval example = "com.karandaev.fieldfolio.feature.Dialog"')
        self.assertEqual([], architecture.violations(root))

    def test_duplicate_declarations_and_wrong_paths_are_rejected(self):
        root = self.fixture(architecture.BASE + '.core.document', 'class Duplicate')
        path = root / 'app/src/main/kotlin/com/karandaev/fieldfolio/core/document/Other.kt'
        path.write_text(f'package {architecture.BASE}.core.document\nclass Duplicate\n')
        self.assertTrue(any('duplicate declaration' in reason for _, reason in architecture.violations(root)))
        path.write_text('package wrong.path\nclass Other\n')
        self.assertTrue(any('package/path mismatch' in reason for _, reason in architecture.violations(root)))

    def test_domain_must_not_import_room_implementation(self):
        root = self.fixture(architecture.BASE + '.core.domain.model', 'import com.karandaev.fieldfolio.core.data.local.AppDatabase')
        self.assertTrue(architecture.violations(root))

    def test_unowned_coroutines_are_rejected(self):
        root = self.fixture(architecture.BASE + '.feature.document.state', 'fun save() = GlobalScope.launch {}')
        self.assertTrue(any('unowned coroutine' in reason for _, reason in architecture.violations(root)))
