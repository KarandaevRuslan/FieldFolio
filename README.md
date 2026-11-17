<h1 align="center">
  <img src="MobileApp/app/src/main/ic_launcher-playstore.png" width="168" alt="Fieldfolio Android app icon">
  <br>
  Fieldfolio
</h1>

<p align="center">
  An Android workspace for field reports, maps, weather, interactive documents, and image analysis.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android-3DDC84" alt="Android">
  <img src="https://img.shields.io/badge/language-Kotlin-7F52FF" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/backend-Spring%20Boot-6DB33F" alt="Spring Boot">
  <img src="https://img.shields.io/badge/ML-TFLite%20%2B%20YOLO-FF6F00" alt="TFLite and YOLO">
</p>

<p align="center">
  <a href="MobileApp/README.md">Android app</a> ·
  <a href="Server/README.md">Server</a> ·
  <a href="docs/README.md">Documentation</a> ·
  <a href="docs/ARCHITECTURE.md">Architecture diagrams</a> ·
  <a href="docs/BUILD_AND_TEST.md">Build &amp; test</a>
</p>

## What is Fieldfolio?

**Fieldfolio** is primarily an Android application for collecting and organizing field data.
Reports can combine structured notes, photos, audio, coordinates, local weather, clocks,
model outputs, and interactive **Document** blocks. Reports are stored locally and can be
exported or imported. The optional backend supports authenticated services and remote image
inference; local report editing does not depend on an always-available server.

The repository also contains a Spring Boot backend and a separate Python model-training
workspace. These are **independently built projects**, not modules of the Android Gradle build.

## Highlights

- **Structured reports:** ordered elements, nested folders, media, reusable document resources,
  import/export, and optional report/folder protection.
- **Maps and context:** OpenStreetMap maps, location selection, current/last-known positions,
  weather at a selected point, and clocks with selectable time zones.
- **Interactive documents:** embedded Acode/CodeMirror editing and a WebView-based Document
  renderer with a scoped, read-only report virtual file system (VFS).
- **Image analysis:** a bundled TensorFlow Lite model for on-device inference and support for
  remote inference through the server, where configured.
- **User experience:** Jetpack Compose, adaptive layouts, English/Russian localization,
  and local/guest workflows. Firebase-backed account features require project credentials.

For the complete feature list, see the [Android README](MobileApp/README.md).

## Repository layout

```text
.
├── MobileApp/           # Fieldfolio Android app and acode-web frontend
│   ├── app/
│   ├── acode-web/
│   ├── branding/
│   └── docs/
├── Server/              # Spring Boot REST API and server-side integration
│   ├── src/
│   ├── pom.xml
│   └── docs/
├── DetectionTrainProj/  # Python/YOLO training and experimentation
├── docs/                # Shared documentation and architecture diagrams
├── .github/workflows/   # CI and optional APK release automation
└── .gitignore           # Repository-wide exclusions for local/generated files
```

| Component | Start here | Build system |
|---|---|---|
| **Android app** | [MobileApp/README.md](MobileApp/README.md) · [Architecture](MobileApp/ARCHITECTURE.md) | Gradle, Kotlin, Compose; Vite for bundled WebView assets |
| **Server** | [Server/README.md](Server/README.md) · [Server architecture](Server/docs/ARCHITECTURE.md) | Maven, Java 17, Spring Boot |
| **Model training** | [DetectionTrainProj/](DetectionTrainProj/) · [requirements.txt](DetectionTrainProj/requirements.txt) | Python, YOLO; training is separate from CI |
| **Repository documentation** | [docs/README.md](docs/README.md) | Markdown and SVG diagrams |

## System architecture

![Fieldfolio system architecture: Android client, local data and inference, Firebase authentication, and optional Spring Boot / YOLO backend](docs/diagrams/system-overview.svg)

More detailed diagrams and component boundaries:
[system architecture](docs/ARCHITECTURE.md),
[Android architecture](MobileApp/ARCHITECTURE.md),
[Android component map](MobileApp/docs/architecture/MAP.md), and
[server architecture](Server/docs/ARCHITECTURE.md).

## Getting started

### Android (main application)

Requirements: **JDK 17**, **Android SDK 36**, and **build-tools 35.0.0**. Open
[`MobileApp/`](MobileApp/) in Android Studio, or build from a terminal:

```sh
cd MobileApp
./gradlew :app:assembleDebug
```

On Windows PowerShell:

```powershell
cd MobileApp
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `MobileApp/app/build/outputs/apk/debug/app-debug.apk`.
The repository includes prebuilt WebView assets and an
[on-device TFLite model](MobileApp/app/src/main/assets/models/road_damage_yolo_int8.tflite).
Real Firebase sign-in requires your own **ignored**
`MobileApp/app/google-services.json`; do not commit it. Guest/offline development builds
have separate configuration. For source/frontend regeneration and signing, see
[Android building instructions](MobileApp/docs/BUILDING.md).

### Server (optional)

The server uses **Java 17 and Maven**. From the repository root:

```sh
mvn -f Server/pom.xml -B -ntp verify
```

This checks compilation and packaging, **not** a running integration with Firebase,
PostgreSQL, ClamAV, or YOLO. The supplied server project has no Maven wrapper, so use
an installed `mvn`, not `./mvnw`.

To configure and run it, use the [server README](Server/README.md) and
[server configuration guide](Server/docs/CONFIGURATION.md). Keep credentials, TLS keys,
model weights and local application configuration out of Git.

### Model training and optional assets

`DetectionTrainProj/` contains a separate Python/YOLO training workspace. See its
[requirements](DetectionTrainProj/requirements.txt) and tools; CI does not train models.
The original repository README also provided optional external assets:

- [YOLO `best.pt` weights](https://drive.google.com/file/d/1yV3CfxLJxuja3cF0HZq4OhF55mBILtA7/view?usp=sharing)
- [ClamAV runtime/data bundle](https://drive.google.com/file/d/1kS-SzdwvBaiqPAExD1i0zBCbkaFx8DX2/view?usp=sharing)

These downloads are **not required to build the Android APK**. Configure server-side
inference and antivirus explicitly; external download availability is not verified here.

## Documentation and project files

| Topic | Links |
|---|---|
| Repository overview | [Docs index](docs/README.md) · [Architecture & diagrams](docs/ARCHITECTURE.md) |
| Build, tests and CI | [Build & test](docs/BUILD_AND_TEST.md) · [Workflows](docs/WORKFLOWS.md) · [Android testing](MobileApp/docs/TESTING.md) |
| Android internals | [MobileApp README](MobileApp/README.md) · [Architecture](MobileApp/ARCHITECTURE.md) · [Design decisions](MobileApp/docs/architecture/DECISIONS.md) |
| Interactive Document API | [VFS / Document API (EN)](MobileApp/docs/api/document-en.md) · [RU](MobileApp/docs/api/document-ru.md) |
| Server | [Server README](Server/README.md) · [Architecture](Server/docs/ARCHITECTURE.md) · [Configuration](Server/docs/CONFIGURATION.md) |
| Releases | [Android releasing](MobileApp/docs/RELEASING.md) · [GitHub workflows](.github/workflows/) |
| Repository policies | [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [Git hygiene](docs/REPOSITORY_HYGIENE.md) |
| Licensing and provenance | [MIT license](MobileApp/LICENSE.md) · [Third-party notices](MobileApp/THIRD_PARTY_NOTICES.md) · [Model provenance](MobileApp/docs/MODEL_PROVENANCE.md) |

The image above is the **actual Android launcher icon** checked into the project;
its [editable SVG source](MobileApp/branding/fieldfolio.svg) is also available. The old
README's screenshot paths were removed because those screenshot files are not included
in the repository snapshot.

## Contributing and security

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request and
[SECURITY.md](SECURITY.md) before reporting a vulnerability. Shared GitHub Actions and
issue templates live in the root [`.github/`](.github/). Dependabot version-update pull
requests are disabled; dependency updates are reviewed manually.

Fieldfolio-authored code is covered by the [MIT license](MobileApp/LICENSE.md).
Third-party software and bundled-model terms are recorded in the
[notices](MobileApp/THIRD_PARTY_NOTICES.md) and
[model provenance](MobileApp/docs/MODEL_PROVENANCE.md).
