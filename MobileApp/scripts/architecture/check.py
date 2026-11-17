#!/usr/bin/env python3
"""Enforce repository/package/layer boundaries without loading Android or a Gradle daemon."""
from pathlib import Path
import re
import sys

BASE = 'com.karandaev.fieldfolio'
ROOT = Path(__file__).resolve().parents[2]


def code_only(source):
    """Keep newlines/offsets, ignoring documentation and string literals for dependency checks."""
    pattern = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*'
    return re.sub(pattern, lambda match: re.sub(r'[^\n]', ' ', match.group()), source)


def violations(root):
    source = root / 'app/src/main/kotlin'
    problems = []
    classes = {}
    for path in sorted(source.rglob('*.kt')):
        rel = path.relative_to(root).as_posix()
        raw = path.read_text(encoding='utf-8')
        text = code_only(raw)
        package = re.search(r'^package ([\w.]+)', text, re.M)
        if not package:
            problems.append((rel, 'missing package'))
            continue
        pkg = package.group(1)
        expected = '/'.join(pkg.split('.'))
        if path.parent.relative_to(source).as_posix() != expected:
            problems.append((rel, 'package/path mismatch'))
        if '_' in pkg or '.presentation.' in pkg:
            problems.append((rel, 'legacy package naming'))
        dependencies = set(re.findall(re.escape(BASE) + r'\.[\w.]+', text)) - {pkg}
        if pkg.startswith(BASE + '.core') and any(dep.startswith((BASE + '.feature.', BASE + '.app.')) for dep in dependencies):
            problems.append((rel, 'core depends on app/feature'))
        if pkg.startswith(BASE + '.feature') and any(dep.startswith(BASE + '.app.') for dep in dependencies):
            problems.append((rel, 'feature depends on composition root'))
        if pkg.startswith(BASE + '.core.domain') and any('.core.ui.' in dep or '.core.data.' in dep for dep in dependencies):
            problems.append((rel, 'domain depends on UI/persistence implementation'))
        if pkg.startswith(BASE + '.feature.document.state') and any('.feature.document.viewer.' in dep or '.feature.document.editor.' in dep for dep in dependencies):
            problems.append((rel, 'document state depends on rendered UI'))
        if pkg.startswith(BASE + '.feature.document.viewer') and any('.feature.document.editor.' in dep for dep in dependencies):
            problems.append((rel, 'read-only viewer depends on editor'))
        if 'GlobalScope' in text:
            problems.append((rel, 'unowned coroutine scope'))
        if len([line for line in text.splitlines() if line.strip() and not line.startswith(("package ", "import "))]) > 650:
            problems.append((rel, 'production source exceeds 650 code lines'))
        if path.name in {'AuthViewModel.kt', 'SettingsViewModel.kt', 'DocumentViewModel.kt', 'DetectViewModel.kt'} and len(raw.splitlines()) > 350:
            problems.append((rel, 'state facade exceeds 350 lines'))
        for name in re.findall(r'^(?:(?:data|sealed|enum|internal|open|abstract)\s+)*(?:class|interface|object)\s+(\w+)', text, re.M):
            fq = pkg + '.' + name
            if fq in classes:
                problems.append((rel, f'duplicate declaration: {fq}'))
            classes[fq] = rel
    for legacy in root.glob('app/src/*/java'):
        if any(legacy.rglob('*.*')):
            problems.append((legacy.relative_to(root).as_posix(), 'duplicate Java-era source root'))
    return problems


def main():
    result = violations(ROOT)
    for path, reason in result:
        print(f'{path}: {reason}', file=sys.stderr)
    if not result:
        print('Architecture boundaries: OK')
    return bool(result)


if __name__ == '__main__':
    raise SystemExit(main())
