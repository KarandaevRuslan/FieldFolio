# Testing

## Fast source checks

```sh
python3 scripts/architecture/check.py
python3 scripts/check_repository.py
python3 -m unittest discover -s tests -v
npm --prefix acode-web test
```

Architecture tests mutate synthetic source trees to prove that forbidden imports/FQ references,
wrong paths, duplicate declarations and unowned scopes fail. They also check the actual project.
Release tests validate tag/version handling, secret-file exclusions and manifest checks.

The JS suite executes the real Acode module graph, selection/clipboard/read-only behavior,
syntax/completion, scrolling, searches, document VFS capabilities and host/renderer transport.
Its module-graph test rejects a privileged editor host inside the sandboxed document bundle.

## Android regression tests without real services

```sh
python3 scripts/testing/prepare_sdks.py
./gradlew :app:testDebugUnitTest -PofflineTestNetwork=true --no-daemon --max-workers=2
```

The bootstrap downloads API 28/35 Robolectric instrumented SDKs with Maven SHA-512 verification.
The opt-in test agent blocks outgoing connections/DNS except loopback. It is a JDK 17 test tool,
never packaged into the application. Without the property, normal Gradle/Android Studio tests
use standard Robolectric dependency resolution and do not require this Python bootstrap.

The suite covers ordered VFS mutations and name conflicts, packet/crypto/Room compatibility,
all model output forms, crop/result projection, nested Acode Save, private saved-state behavior,
orientation, viewer windows, map camera state, audio ownership, scroll metrics and WebView security.
`DocumentMutationRegressionTest` adds deterministic multi-operation sequences and typed JSON
round trips across every report element kind.

Tests with native graphics/Compose state run in separate worker JVMs (`forkEvery = 1`) because
these Android runtimes have process-global state. This is slower than a pure JVM suite and avoids
mixing dispatchers/native runtime initialization between unrelated tests.

## Real Notebook Kit/Nexus integration

After the Android suite has produced `app/build/document-fixture.json`:

```sh
npm --prefix acode-web run test:document-runtime
```

These tests render actual notebook cells, use typed native fixture resources/FileAttachment and
verify Nexus reaction/disposal behavior. They are DOM tests, not an Android WebView emulator.
`npm run test:document-browser` is an additional Playwright harness when a compatible Chromium
installation is available; it is not claimed to have run unless recorded in validation evidence.

## Device checks

```sh
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

`RuntimeDeviceSmokeTest` checks the application graph, bundled TFLite header and real document
WebView file/content/storage settings. A compiled instrumentation APK is not evidence of a device
run. Before shipping, check actual phone/tablet gestures, portrait/landscape rotation, content
providers, camera/crop, audio continuation and large reports. Recorded checks are in
[VALIDATION.md](VALIDATION.md).
