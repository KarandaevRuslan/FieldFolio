#!/usr/bin/env python3
"""Check first-party Markdown links/anchors and canonical offline help, without network access."""
from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {'node_modules', 'vendor', 'third_party', 'build', 'target', '.git', '.venv'}


def prose(text: str) -> str:
    """Remove fenced examples; their sample hrefs are not documentation navigation."""
    result = []
    fence = None
    for line in text.splitlines():
        match = re.match(r'^\s*(`{3,}|~{3,})', line)
        if match:
            marker = match.group(1)
            if fence is None:
                fence = marker
            elif marker[0] == fence[0] and len(marker) >= len(fence):
                fence = None
            result.append('')
        elif fence is None:
            result.append(line)
    return '\n'.join(result)


def anchors(text: str) -> set[str]:
    body = prose(text)
    found = set(re.findall(r'''\b(?:id|name)=["']([^"']+)["']''', body))
    counts: dict[str, int] = {}
    for line in body.splitlines():
        heading = re.match(r'^ {0,3}#{1,6}\s+(.+?)(?:\s+#+)?$', line)
        if not heading:
            continue
        title = re.sub(r'<[^>]*>', '', heading.group(1))
        title = re.sub(r'!?\[([^]]+)\]\([^)]*\)', r'\1', title)
        slug = re.sub(r'[^\w\- ]', '', title.lower()).replace(' ', '-')
        count = counts.get(slug, 0)
        counts[slug] = count + 1
        found.add(slug + (f'-{count}' if count else ''))
    return found


def destinations(text: str) -> list[str]:
    body = prose(text)
    # Ignore inline code before interpreting Markdown links.
    body = re.sub(r'`+[^`\n]*`+', '', body)
    inline = re.findall(r'''!?\[[^\]\n]*\]\((<[^>]+>|[^\s)]+)(?:\s+["'][^)]*)?\)''', body)
    references = re.findall(r'^ {0,3}\[[^]]+\]:\s*(<[^>]+>|\S+)', body, re.M)
    html = re.findall(r'''\b(?:href|src)=["']([^"']+)["']''', body)
    return [value.strip('<>') for value in inline + references + html]


def markdown_files(root: Path) -> list[Path]:
    result = []
    for path in root.rglob('*.md'):
        parts = path.relative_to(root).parts
        if any(part in EXCLUDED for part in parts):
            continue
        if parts[:6] == ('MobileApp', 'app', 'src', 'main', 'assets', 'help'):
            continue  # Checked against the canonical references separately.
        result.append(path)
    return sorted(result)


def check_links(root: Path, files: list[Path]) -> list[str]:
    root = root.resolve()
    errors = []
    cache: dict[Path, set[str]] = {}
    for path in files:
        for href in destinations(path.read_text(encoding='utf-8-sig')):
            link = urlsplit(href)
            if link.scheme or link.netloc:
                continue
            target = (root / unquote(link.path).lstrip('/') if link.path.startswith('/')
                      else path.parent / unquote(link.path)) if link.path else path
            target = target.resolve()
            label = f'{path.relative_to(root)}: {href}'
            if not target.is_relative_to(root):
                errors.append(label + ' escapes repository')
            elif not target.exists():
                errors.append(label + ' target missing')
            elif link.fragment and target.suffix.lower() == '.md':
                if target not in cache:
                    cache[target] = anchors(target.read_text(encoding='utf-8-sig'))
                if unquote(link.fragment) not in cache[target]:
                    errors.append(label + ' anchor missing')
    return errors


def check_help(root: Path) -> list[str]:
    errors = []
    mobile = root / 'MobileApp'
    for language in ('en', 'ru'):
        source = mobile / f'docs/api/document-{language}.md'
        target = mobile / f'app/src/main/assets/help/document-{language}.md'
        if not source.is_file() or not target.is_file():
            errors.append(f'Missing canonical/offline Document help: {language}')
        elif target.read_text(encoding='utf-8') != source.read_text(encoding='utf-8').replace('${', '\\${'):
            errors.append(f'Offline help differs from canonical reference: {language}; run sync-help.mjs')
    return errors


def main() -> int:
    files = markdown_files(ROOT)
    errors = check_links(ROOT, files) + check_help(ROOT)
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print(f'Documentation: {len(files)} Markdown files, local links/anchors and EN/RU offline help OK')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
