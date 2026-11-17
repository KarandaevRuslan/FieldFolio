# Team Project: repository guide

This Git repository hosts three related but independently built workspaces:

| Directory | Purpose | Entry points |
|---|---|---|
| [`MobileApp/`](../MobileApp/) | Fieldfolio Android client and its WebView frontend | `app/`, `acode-web/`, `MobileApp/README.md` |
| [`Server/`](../Server/) | Spring Boot HTTP API, authentication and image validation | `pom.xml`, `src/main/java/.../App.java`, [`Server/README.md`](../Server/README.md) |
| [`DetectionTrainProj/`](../DetectionTrainProj/) | Local model training/experiments | `requirements.txt` and project tools; training sources were not provided for this documentation pass |

**GitHub Actions, Dependabot, issue templates and CODEOWNERS belong in the root `.github/`**.
The Android Gradle project continues to live in `MobileApp/`; the Maven project lives in
`Server/`. Do not move `app/` or `pom.xml` into the Git root.

- [Whole-system architecture and diagrams](ARCHITECTURE.md)
- [Build and test commands](BUILD_AND_TEST.md)
- [CI / release workflows](WORKFLOWS.md)
- [Git hygiene, secrets and migration](REPOSITORY_HYGIENE.md)
- [Android architecture](../MobileApp/ARCHITECTURE.md)
- [Server architecture](../Server/docs/ARCHITECTURE.md)
- [Server configuration](../Server/docs/CONFIGURATION.md)

This is a monorepo infrastructure layer; it does not modify the app's runtime behavior or the
server's Java classes. The root `README.md` is intentionally left unchanged because its original
content was not supplied. Add a link from it to `docs/README.md` after reviewing it.
