# Fieldfolio

Fieldfolio is an Android field-report workspace combining ordered documents, a virtual file
system, maps, media and local/server model results. The native interface uses Kotlin and
Jetpack Compose; source editing embeds Acode/CodeMirror; interactive Document elements use
Observable Notebook Kit and Nexus Core in an isolated WebView renderer.

Maintained by [KarandaevRuslan](https://github.com/KarandaevRuslan/).

The **Fieldfolio** Android client lives in `MobileApp/` in a shared Git repository.
GitHub workflows and issue templates live in the repository-root
[`.github/`](../.github/); automated Dependabot PRs are disabled.
See the [repository guide](../docs/README.md).

## Capabilities

- Organize reports and their ordered elements with folders, logical paths, copy/cut/paste/move,
  unique sibling names, optional display titles and viewer visibility.
- Keep editing and viewing separate; use adaptive layouts, collapsible report folders and
  retained reading/media/map state across rotation.
- Add text/source files, headings, images, audio, Location, Weather, Clock, model results and
  interactive Document elements. Edit text/notebook source with Acode syntax and completion support.
- Use local TFLite models and the server registry for detection, polygon/pixel segmentation,
  classification, regression and supported text/numeric adapters. Preserve portable result snapshots.
- Export/import report archives, protect reports/folders, export selected element subtrees with
  metadata and resources, and export standalone model results.
- Run Documents against a read-only view of their report VFS. Documents cannot mutate the VFS,
  access another report, call the native editor bridge or read app files directly.
- Use English/Russian localization and app-aware light/dark styling.

## Build a complete checkout

From `MobileApp/`: **JDK 17**, **Node 22.16.0**, Android SDK **36**, build-tools **35.0.0** and Python 3.10+
for repository/CI checks. The Gradle wrapper is pinned to **8.11.1** and AGP remains **8.9.0**.
The application ID remains `com.karandaev.fieldfolio`.

The source distribution includes current bundled web assets and the author-provided built-in
model. A normal debug APK can be built without npm or private credentials:

```sh
./gradlew :app:assembleDebug
```

Windows: use `gradlew.bat`. APK: `app/build/outputs/apk/debug/app-debug.apk`.
Without a local Firebase configuration, debug builds use explicit development-only options;
offline guest/report functionality is available. Real account/Google authentication needs your
own `app/google-services.json`. Signed release builds require that configuration and a signing key.
See [Building](docs/BUILDING.md) for setup, frontend regeneration and signing.

## Documentation

| Topic | Reference |
|---|---|
| Architecture and trust boundaries | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Component map and code entry points | [Architecture map](docs/architecture/MAP.md) |
| Design decisions | [Architecture decisions](docs/architecture/DECISIONS.md) |
| Build and configuration | [Building](docs/BUILDING.md) |
| APK builds on tags and signed drafts | [Releasing](docs/RELEASING.md) |
| Test commands and regression areas | [Testing](docs/TESTING.md) |
| Current validation evidence | [Validation](docs/VALIDATION.md) |
| Data ownership, archives and model adapters | [Data formats](docs/DATA_FORMATS.md), [models](docs/MODELS.md) |
| Document / JavaScript / VFS API | [English reference](docs/api/document-en.md) |
| Contribution and security reporting | [Contributing](CONTRIBUTING.md), [Security](SECURITY.md) |
| Licenses and bundled model | [Third-party notices](THIRD_PARTY_NOTICES.md), [Model provenance](docs/MODEL_PROVENANCE.md) |

## Releases

Push a `v<versionName>` tag matching `gradle.properties`. GitHub Actions runs verification,
builds a signed release APK using the protected `release` environment and creates a draft release
with its SHA-256 checksum. Review and publish the draft manually. Manual dispatch on a branch
runs verification only; on a tag it can run signing. See [Releasing](docs/RELEASING.md).

The Fieldfolio [MIT license](LICENSE.md) covers Fieldfolio-authored source; preserved third-party
licenses and model provenance are documented separately.
