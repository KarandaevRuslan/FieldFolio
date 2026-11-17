# Fieldfolio architecture

The repository is a **monorepo, not a single build**. Android is built with Gradle; the
Spring Boot API with Maven; the training workspace has its own Python requirements.

## Runtime overview

```mermaid
flowchart LR
    User[Android user] --> App[Fieldfolio Android / Jetpack Compose]
    App --> Local[Local reports / Room / file storage]
    App --> Web[Sandboxed Document WebView]
    App --> Firebase[Firebase authentication]
    App -->|HTTPS + Firebase ID token| API[Spring Boot Server]
    API --> Auth[Firebase Admin ID token validation]
    API --> Guard[Rate limits + image validation]
    Guard --> AV[ClamAV when enabled]
    Guard --> YOLO[Python YOLO HTTP /infer]
    API --> DB[(PostgreSQL profile records)]
    API --> Avatars[(Avatar file storage)]
    Training[DetectionTrainProj] -.->|Exported models; integration is deployment-specific| YOLO
```

The client supports both on-device TFLite models and optional server-side detection. The
interactive Document renderer has a scoped, read-only report VFS; it does not gain access
to server credentials or the native editor bridge. Backend authentication and profile storage
are separate from local report persistence.

## Repository layout

```mermaid
flowchart TB
    Repo[Fieldfolio monorepo / Git root]
    Repo --> Mobile[MobileApp: Gradle + Acode / Vite]
    Repo --> Server[Server: Maven + Spring Boot]
    Repo --> Training[DetectionTrainProj: Python training]
    Repo --> Workflows[.github: CI, issues, release]
    Repo --> Docs[docs: shared architecture and policies]
    Mobile --> ClientTests[Android, JVM, JS and Python checks]
    Server --> MavenTests[Maven verify; add JUnit tests when available]
    Training --> TrainingSyntax[Tracked Python source syntax check]
```

## Key source paths

- Android service graph: `MobileApp/app/src/main/kotlin/com/karandaev/fieldfolio/app/MyApplication.kt`.
- Android Document VFS and WebView: `MobileApp/app/src/main/kotlin/com/karandaev/fieldfolio/core/document/`.
- Web editor and notebook runtime: `MobileApp/acode-web/src/editor/` and `src/document/`.
- Spring Boot entry: `Server/src/main/java/com/karandaev/roadCrackDetectionServer/App.java`.
- HTTP endpoints: `Server/src/main/java/com/karandaev/roadCrackDetectionServer/web/` and `profile/web/`.
- Server image security: `Server/src/main/java/com/karandaev/roadCrackDetectionServer/image/`.
- Server inference adapter: `Server/src/main/java/com/karandaev/roadCrackDetectionServer/yolo/`.

## Boundaries and limitations

- `Server.zip` contains Spring Boot source, but not the `Server/python-yolo` implementation,
  PostgreSQL deployment, signing certificates, or a runnable production config.
- The training workspace was described by its directory tree only; we do **not** claim its
  models are directly deployable to the server without a separate export/configuration step.
- A passing Android build and `mvn verify` do not test the end-to-end TLS, Firebase, ClamAV,
  database and YOLO pipeline. Deploy those dependencies in a separate integration environment.
