# Fieldfolio: repository guide

Start with the [main Fieldfolio README](../README.md) for the product overview, launcher icon,
and setup instructions.

This Git repository hosts three related but independently built workspaces:

| Directory | Purpose | Entry points |
|---|---|---|
| [`MobileApp/`](../MobileApp/) | Fieldfolio Android client and its WebView frontend | `app/`, `acode-web/`, `MobileApp/README.md` |
| [`Server/`](../Server/) | Spring Boot HTTP API, authentication and image validation | `pom.xml`, `src/main/java/.../App.java`, [`Server/README.md`](../Server/README.md) |
| [`DetectionTrainProj/`](../DetectionTrainProj/) | Local model training/experiments | `requirements.txt` and project tools; training sources were not provided for this documentation pass |

**GitHub Actions, issue templates and CODEOWNERS belong in the root `.github/`.**
Dependabot version-update PRs are disabled (no root `dependabot.yml`).
The Android Gradle project continues to live in `MobileApp/`; the Maven project lives in
`Server/`. Do not move `app/` or `pom.xml` into the Git root.

- [Whole-system architecture and diagrams](ARCHITECTURE.md)
- [Build and test commands](BUILD_AND_TEST.md)
- [CI / release workflows](WORKFLOWS.md)
- [Git hygiene, secrets and migration](REPOSITORY_HYGIENE.md)
- [Android architecture](../MobileApp/ARCHITECTURE.md)
- [Server architecture](../Server/docs/ARCHITECTURE.md)
- [Server configuration](../Server/docs/CONFIGURATION.md)

Fieldfolio is the product name; `Team Project/` may remain the local checkout folder name.
The repository infrastructure does not change app or server runtime behavior.
The [root README](../README.md) links to this guide and to the Android and server READMEs.
