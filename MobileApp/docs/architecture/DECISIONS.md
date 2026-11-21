# Architecture decisions

## 1. Preserve identity and stored data

**Decision:** keep application ID `com.karandaev.fieldfolio`, Room version 9, archive formats,
logical/display-name semantics and explicit migration paths.

**Reason:** updates must preserve access to existing reports. The exported Room schema supports
migration review, and saved result snapshots remain independent of an installed imported model.

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

## 7. Verify before signing tagged releases

**Decision:** a matching `v<versionName>` tag runs verification, then signs a release APK with
protected environment inputs and creates a draft GitHub Release. Publishing remains manual.
Manual branch dispatch skips signing; manual tag dispatch can run it.

**Reason:** CI debug artifacts remain useful without private inputs. A release must use the
intended signing identity and real Firebase configuration; missing inputs fail the release job.
Published release assets cannot be overwritten by this workflow. See [Releasing](../RELEASING.md).

## 8. Test actual contracts and report limits

**Decision:** keep native regression tests, real Notebook Kit/Nexus DOM integration, architectural
mutation tests, asset/distribution tests and compiled device smoke tests.

**Reason:** source-string assertions alone cannot prove runtime behavior. Conversely, a passing
Robolectric/DOM test cannot prove real-device gestures, WebView behavior, camera providers or
remote CI/signing. [Validation](../VALIDATION.md) records what actually ran and what did not.
