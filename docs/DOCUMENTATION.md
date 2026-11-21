# Maintaining documentation

Keep the root README/installation guide as entry points, component READMEs as setup overviews,
and detailed contracts under each component's `docs/`. Put user workflows in
[the Russian user guide](USER_GUIDE.ru.md). Link to a contract rather than copying its full table
into every guide. Record behavior present in source; label examples and planned work explicitly.

## Sources and generated output

| Topic | Maintained source | Output/check |
|---|---|---|
| Document API | `MobileApp/docs/api/document-{en,ru}.md` | `MobileApp/app/src/main/assets/help/`; `sync-help.mjs` escapes literal `${` for Notebook Kit |
| Web applications | `MobileApp/acode-web/` | Bundles under Android `assets/acode` and `assets/document`; source/output manifest |
| Shared diagrams | `docs/diagrams/*.dot` | Matching `.svg`; Graphviz |
| Room contracts | Entities/migrations in Android source | `MobileApp/app/schemas/`; regenerate through the Android build |
| Toolchain/dependencies | Gradle properties, wrapper, npm lockfile, Maven POM, Python requirements | Document the versions actually pinned |
| Releases | `.github/workflows/mobile-release.yml` and `MobileApp/scripts/release/` | [Release procedure](../MobileApp/docs/RELEASING.md) |

Third-party license texts, vendored documentation and historical inventories have their own
provenance. Keep their original terms/content. Dataset inventory snapshots are described in
[struct_info](../DetectionTrainProj/struct_info/README.md); do not treat them as runnable paths.

## Verification

Run from the repository root:

```sh
python3 scripts/check_documentation.py
python3 -m unittest discover -s tests -v
```

The checker validates first-party local Markdown links/anchors and exact EN/RU offline-help
synchronization. It skips fenced examples and vendored/generated documentation. It does not
fetch external links or prove that shell examples will work with arbitrary credentials/hardware.
Repository CI runs the checker and its regression tests.

For web source, dependencies or API/help changes, normally run from `MobileApp/`:

```sh
npm --prefix acode-web ci --no-audit --no-fund
npm --prefix acode-web test
npm --prefix acode-web run build
python3 scripts/check_web_assets.py
```

For a strictly API-text-only change, when the previous asset check passed and every existing
non-documentation input/bundle is unchanged, the smaller regeneration is:

```sh
node acode-web/scripts/sync-help.mjs
node acode-web/scripts/asset-manifest.mjs
python3 scripts/check_web_assets.py
```

Do not rewrite the manifest to conceal stale frontend bundles. Use the full build whenever
runtime code, dependencies or build scripts change. Generate each diagram, for example:

```sh
dot -Tsvg docs/diagrams/system-overview.dot -o docs/diagrams/system-overview.svg
```

A validation record must name the checked revision/date, commands and results. A source check,
a compiled APK and an actual device run are different evidence. Keep commands for further
checks in [BUILD_AND_TEST.md](BUILD_AND_TEST.md) and [Server testing](../Server/docs/TESTING.md).
