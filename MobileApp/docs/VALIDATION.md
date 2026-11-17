# Validation evidence

Validated on **2026-11-17** for Fieldfolio **2.2.0**. Commands used JDK 17, Gradle 8.11.1,
AGP 8.9.0 and Android SDK 36. No production Firebase configuration or signing key was used.

## Executed checks

| Check | Result |
|---|---|
| `python3 scripts/architecture/check.py` | Passed on the complete production source tree |
| `python3 -m unittest discover -s tests -v` | 15 passed: boundary mutation, actual source, tag/version, archive and web integrity contracts |
| `python3 scripts/check_repository.py` | Passed public-source inventory checks |
| `python3 scripts/format_kotlin.py --check` | Passed checksum-pinned ktfmt 0.55 Google-style check |
| `npm --prefix acode-web test` | 62 passed, no failures or skips |
| `npm --prefix acode-web run build` | Editor and Document bundles regenerated, canonical help synchronized |
| `python3 scripts/check_web_assets.py` | 38 generated asset files verified against source/output hashes |
| `:app:testDebugUnitTest -PofflineTestNetwork=true` | **414 passed across 63 suites**, no failures, errors or skips in the final complete run |
| `npm --prefix acode-web run test:document-runtime` | 3 passed against real Notebook Kit/Nexus and the native-generated report fixture |
| `:app:assembleDebug` | APK built without private configuration; supplied model and current web entry assets verified inside APK |
| `:app:assembleDebugAndroidTest` | Instrumentation APK compiled successfully |
| `:app:lintDebug` | Passed: **0 errors**, 44 warnings and 2 informational findings |
| `actionlint 1.7.7` | Both workflow files passed syntax/expression/action checks; optional shellcheck/pyflakes integrations were not enabled |
| Source ZIP | Per-file SHA-256 manifest, ZIP integrity and unpacked architecture/distribution/web checks verified |

The final Android command was:

```sh
./gradlew :app:testDebugUnitTest -PofflineTestNetwork=true \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug \
  --no-daemon --max-workers=2
```

The opt-in test network guard denied external sockets/DNS while allowing loopback. A deprecated
JDK 17 SecurityManager warning is expected from this test-only agent. The agent is not an app
dependency and does not enter the APK.

## Structural changes measured

Line counts include imports, comments and formatting; extraction is by responsibility, not a
claim that the total implementation disappears.

| File | Before | Refactored facade / coordinator |
|---|---:|---:|
| `AuthViewModel.kt` | 1,312 | 196 |
| `SettingsViewModel.kt` | 873 | 170 |
| `DocumentViewModel.kt` | 711 | 249 |
| `DetectViewModel.kt` | 647 | 106 |
| `DocumentRoute.kt` | 886 | 673 |

The route retains navigation/window coordination; report feed, toolbar, forms, media windows and
state operations are separate. The size rule excludes imports/comments and limits production
code to 650 nonempty code lines per file and the four state facades to 350 total lines.

The archive removes excluded duplicate Java-era source roots, unregistered historical screens,
old verification/patch material, stale web output, 50 unused string resources and unused raster
foreground variants. Reusable weather/clock widgets, adapted Acode sources, vendor notices and
the supplied TFLite model are retained. English and Russian string-key sets match after cleanup.

## Regression coverage added or strengthened

- Deterministic ordered-tree copy/move/rename sequences, immutable resource ownership, failed
  mutation rollback and typed JSON round trips for every report block kind.
- Synthetic forbidden dependency/path/duplicate/global-scope mutations, not only a passing check
  on the current repository.
- Actual Document dependency graph isolation from privileged Acode host modules.
- Current text private-draft saved-state contracts and the real staged model-result commit flow.
- Report header persistence/reentry synchronized with the asynchronously loaded preference.
- Both WebView hosts initialized on API 28/35 with storage boundaries asserted. Lint also caught
  and led to correction of the content-capture API guard in both hosts.
- Release versions/tags, credential exclusion, deterministic source ZIPs, wrapper permissions,
  and stale/tampered web output rejection.

## Remaining verification limits

This is not a zero-warning lint report. Remaining findings include KTX/Compose recommendations,
platform ExifInterface use, icon/locale/accessibility recommendations and the existing allowance
for user-configured HTTP analysis servers. Three AndroidX Navigation custom-check registries are
too old for this lint API and were skipped by lint; the other lint checks ran. No blanket baseline
or disabled-error rule was added to hide findings.

The instrumentation APK was **compiled, not run on a device/emulator**. Robolectric and DOM tests
do not prove physical touch/camera/content-provider behavior, real WebView rendering performance,
background audio on a phone, live map tiles/weather, Firebase login or production model/server
inference. Playwright browser tests were not run in this refactor validation.

The GitHub workflows were validated locally, **not executed on GitHub**. No repository was
created or pushed, no secrets were configured, and no signed release was built or published.
Follow [Releasing](RELEASING.md) to connect the source to the maintainer's repository and keys.
