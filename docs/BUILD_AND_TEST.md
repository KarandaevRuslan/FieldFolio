# Build and test

Commands below start at the repository root unless a working directory is stated.
Use `python` instead of `python3` where that is the configured Python executable.

## Fast repository checks

```sh
python3 scripts/check_documentation.py
python3 scripts/check_monorepo.py
python3 -m unittest discover -s tests -v
```

The tracked-file scan in `check_monorepo.py` requires a Git index. Without one it checks the
required layout and explicitly skips tracked-file policy checks.

## Android and web

Run from `MobileApp/` with the [pinned toolchain](../MobileApp/docs/BUILDING.md):

```sh
python3 scripts/architecture/check.py
python3 scripts/check_repository.py
python3 -m unittest discover -s tests -v
python3 scripts/format_kotlin.py --check
npm --prefix acode-web ci --no-audit --no-fund
npm --prefix acode-web test
npm --prefix acode-web run build
python3 scripts/check_web_assets.py
python3 scripts/testing/prepare_sdks.py
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest -PofflineTestNetwork=true --no-daemon --max-workers=2
npm --prefix acode-web run test:document-runtime
```

The SDK bootstrap and dependency installation need network/cache access. The offline test
property restricts sockets during test execution; it does not make dependency resolution
offline. DOM integration uses the fixture produced by the Android test suite.
`assembleDebugAndroidTest` compiles the device test APK; `connectedDebugAndroidTest` requires
an attached device/emulator and actually runs it. See [Testing](../MobileApp/docs/TESTING.md).

## Java server

```sh
mvn -B -ntp -f Server/pom.xml verify
```

There is no Maven wrapper or `src/test` test suite in the current server tree. This command
compiles/packages it; runtime checks are described in [Server testing](../Server/docs/TESTING.md).

## Training tools

```sh
cd DetectionTrainProj/tools
python -m voc_yolo_builder --help
```

The training CI parses checked-in Python with `ast.parse` without installing GPU packages.
Dataset conversion, training and inference are separate checks with data, model and environment
prerequisites. See [Dataset preparation](../DetectionTrainProj/docs/DATASET.md).

## Documentation assets

```sh
python3 scripts/check_documentation.py
```

Document API edits also require help synchronization and a valid asset manifest. See
[Documentation maintenance](DOCUMENTATION.md). Regenerate diagrams with `dot -Tsvg input.dot -o output.svg`.
