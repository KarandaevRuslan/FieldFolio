"""Navigation and generated-help regressions for the documentation checker."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('check_documentation', Path(__file__).resolve().parents[1] / 'scripts/check_documentation.py')
docs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(docs)


class DocumentationTests(unittest.TestCase):
    def test_relative_image_and_explicit_anchor(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'docs').mkdir()
            page = root / 'docs/start.md'
            page.write_text('[Go](../README.md#api)\n![Figure](plot.svg)\n', encoding='utf-8')
            (root / 'README.md').write_text('<a id="api"></a>\n', encoding='utf-8')
            (root / 'docs/plot.svg').write_text('<svg/>', encoding='utf-8')
            self.assertEqual(docs.check_links(root, [page]), [])

    def test_reports_missing_target_and_anchor(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            page = root / 'README.md'
            page.write_text('# Setup\n[Bad](missing.md)\n[Bad anchor](#other)', encoding='utf-8')
            errors = docs.check_links(root, [page])
            self.assertEqual(len(errors), 2)
            self.assertIn('target missing', errors[0])
            self.assertIn('anchor missing', errors[1])

    def test_unicode_and_duplicate_heading_anchors(self):
        self.assertEqual(docs.anchors('# Настройка\n## Build and test\n## Build and test'),
                         {'настройка', 'build-and-test', 'build-and-test-1'})

    def test_fenced_examples_and_external_urls_are_not_local(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            page = root / 'README.md'
            page.write_text('```md\n[Example](missing.md)\n```\n[Web](https://example.org/a#b)\n[Mail](mailto:a@example.org)', encoding='utf-8')
            self.assertEqual(docs.check_links(root, [page]), [])

    def test_parent_escape_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            page = root / 'README.md'
            page.write_text('[Outside](../README.md)', encoding='utf-8')
            self.assertIn('escapes repository', docs.check_links(root, [page])[0])

    def test_reference_links_and_percent_encoded_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            page = root / 'README.md'
            page.write_text('[Guide][ref]\n[ref]: <a%20b.md#setup> "Title"', encoding='utf-8')
            (root / 'a b.md').write_text('# Setup', encoding='utf-8')
            self.assertEqual(docs.check_links(root, [page]), [])

    def test_help_drift_and_template_escape(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for language in ('en', 'ru'):
                source = root / f'MobileApp/docs/api/document-{language}.md'
                target = root / f'MobileApp/app/src/main/assets/help/document-{language}.md'
                source.parent.mkdir(parents=True, exist_ok=True)
                target.parent.mkdir(parents=True, exist_ok=True)
                source.write_text('Hello ${name}', encoding='utf-8')
                target.write_text('Hello \\${name}', encoding='utf-8')
            self.assertEqual(docs.check_help(root), [])
            target.write_text('stale', encoding='utf-8')
            self.assertEqual(len(docs.check_help(root)), 1)

    def test_missing_help_is_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(len(docs.check_help(Path(directory))), 2)


if __name__ == '__main__':
    unittest.main()
