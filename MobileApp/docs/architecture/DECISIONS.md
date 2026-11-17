# Architecture decisions

## 1. Preserve identity and stored data

**Decision:** keep application ID `com.karandaev.fieldfolio`, Room version 9, archive formats,
logical/display-name semantics and existing migration paths during structural cleanup.

**Reason:** a package cleanup must not install as a different app, abandon stored reports or
make an archive depend on an installed imported model. The exported Room schema supports future
reviews. No database or packet migration is introduced solely for this refactor.

## 2. Enforce packages before multiplying Gradle modules

**Decision:** retain one Android module and separate responsibilities by package, with executable
dependency rules. `AppServices` is the explicit interface to application-owned dependencies.

**Reason:** moving tightly integrated report/media/model flows directly into many Gradle modules
would combine a build-system migration with behavioral changes. The current boundary checks
prevent reverse dependencies and mismatched package paths now. A future multi-module split can
move established boundaries deliberately; this version does not claim binary module isolation.

## 3. One lifecycle owner, cohesive operation groups

**Decision:** split large ViewModel implementations into small public facades and internal
operation files sharing the existing state and coroutine scope. Split route/feed/toolbar/form
rendering by responsibility.

**Reason:** introducing an extra owner for each extracted function would duplicate drafts,
cancellation and saved-state policy. Extraction preserves active screen-facing member contracts.
Unused historical entry points are removed instead of retained as alternative pipelines.

**Consequence:** internal extensions can access owner state within their package. They are not
independent pure use cases; invariant-rich tree/name/geometry transformations remain in core.

## 4. Common mutations, separate editor and viewer

**Decision:** use immutable `DocumentTree` transformations and common resource/name contracts.
Keep forms inside editor and render-only feed/windows inside viewer. Reuse the same element
presentation rather than adding a second preview implementation.

**Reason:** name conflicts, copy/move ownership, final-save behavior and visibility cannot be
reliably enforced independently in every form. Viewer-to-editor imports are rejected.

## 5. Separate trusted editing from executable documents

**Decision:** preserve the native editor bridge only in the trusted Acode host; run Document code
in its separate constrained renderer with per-session read-only report capabilities.

**Reason:** JavaScript in a report must not mutate the VFS, read arbitrary app storage or acquire
native model/credential access. Moving files must not merge the two module graphs. Graph tests,
transport tests and native WebView tests protect this boundary. A hostile notebook can still
consume renderer CPU/memory; isolation is not a runtime quota.

## 6. Include reproducible web output and the existing model

**Decision:** ship current web bundles and the supplied built-in TFLite asset in source releases,
alongside source/vendor packages and provenance notices. Canonical help lives in `docs/api` and
is generated into offline help during the web build.

**Reason:** an unchanged Android checkout should build without first installing Node. SHA-256
input/output manifests detect stale assets. Model provenance and upstream licenses remain
explicit rather than assigning all third-party content the root source license.

## 7. Tag builds are useful without production credentials

**Decision:** a matching `v<versionName>` tag runs CI and produces a debug-signed APK artifact.
A signed production draft release requires a separate manual run and configured protected
environment secrets. Release tasks reject missing real Firebase/signing inputs.

**Reason:** the public source must build without private keys, while a release must not silently
reuse a debug identity. The CI credential helper never logs secret contents or stores keys in
the checkout. No repository publication or secret setup is performed by the source export.

## 8. Test actual contracts and report limits

**Decision:** keep native regression tests, real Notebook Kit/Nexus DOM integration, architectural
mutation tests, asset/distribution tests and compiled device smoke tests.

**Reason:** source-string assertions alone cannot prove runtime behavior. Conversely, a passing
Robolectric/DOM test cannot prove real-device gestures, WebView behavior, camera providers or
remote CI/signing. [Validation](../VALIDATION.md) records what actually ran and what did not.
