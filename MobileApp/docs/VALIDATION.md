# Documentation verification record

Documentation and its checks were reviewed on **2026-11-22** against the current source tree
(Android `fieldfolio.versionName=2.2.0`, versionCode 16). There is no Git revision identifier
in this checkout. This record covers the documentation update and the checks listed below.

## Executed checks

| Working directory | Command/check | Result |
|---|---|---|
| Repository root | `python3 scripts/check_documentation.py` | Local Markdown links/anchors and EN/RU offline-help synchronization passed |
| Repository root | `python3 scripts/check_monorepo.py` | Required layout passed; tracked-file scan skipped because no Git index exists |
| Repository root | `python3 -m unittest discover -s tests -v` | 12 tests passed: 4 repository policies and 8 documentation regressions |
| `MobileApp/` | `python3 -m unittest discover -s tests -v` | 19 tests passed |
| `MobileApp/` | `python3 scripts/architecture/check.py` | Passed |
| `MobileApp/` | `python3 scripts/check_repository.py` | Passed |
| `MobileApp/` | `python3 scripts/check_web_assets.py` | 38 generated asset files verified |
| `MobileApp/` | `node acode-web/scripts/sync-help.mjs --check` | EN/RU help matches canonical references |
| Training tools | Module `--help` and synthetic VOC conversion | Executed; split membership, labels and generated YAML checked |
| Training/worker Python | `ast.parse` of source files | Passed without importing GPU packages |
| Shared diagrams | Graphviz SVG regeneration | Three diagrams generated and visually checked |
| Patch | Apply to a clean source copy and compare SHA-256 values | Passed for all changed/new files |

Only canonical API text and generated help/manifest changed among web-build inputs/outputs;
runtime JS, packages, lockfile and compiled bundles are unchanged. The baseline asset manifest
passed before the help-only regeneration. No frontend rebuild was needed for this text update.

## Further validation

Android Gradle builds, JVM/Compose tests, npm runtime tests, Maven compilation, GPU training,
TFLite execution, live Firebase/PostgreSQL/ClamAV/API checks and device instrumentation were
not run for this documentation update. Their commands and prerequisites are in
[TESTING.md](TESTING.md) and [the shared test guide](../../docs/BUILD_AND_TEST.md).

The converter's editable installation was checked separately and failed on setuptools package
discovery. The documented direct module command works without installing that package.
Source/asset checks do not establish runtime behavior, model accuracy or production readiness.
