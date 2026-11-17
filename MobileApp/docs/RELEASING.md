# GitHub Actions and APK releases

The workflows are stored in the **monorepo root** `.github/workflows/` and are ready to commit to a repository owned by
[KarandaevRuslan](https://github.com/KarandaevRuslan/). Preparing them does not create a repository,
push commits/tags, configure secrets or run GitHub Actions remotely.

## CI

`../.github/workflows/mobile-ci.yml` runs on MobileApp changes to main, relevant pull requests, manual requests and reusable calls. It checks source
boundaries/hygiene, repository tests, the real web runtime, frontend rebuilds, isolated Android
regressions, lint, debug/instrumentation APK compilation, Room schema drift and Notebook Kit/Nexus
integration. It uploads check reports and a debug APK. PR verification does not require secrets.
Actions are pinned to commit IDs. Review and update their versions manually; automatic
Dependabot pull requests are disabled for this repository.

## APK from a new tag

1. Update `fieldfolio.versionName` and the positive `fieldfolio.versionCode` in `gradle.properties`.
2. Align `acode-web/package.json` and its root lockfile package version; update CHANGELOG.md.
3. Regenerate web assets, verify the source and commit the complete change.
4. Push a matching tag, for example `v2.2.0` for source version `2.2.0`.

`../.github/workflows/mobile-release.yml` calls CI, rejects a mismatching/invalid tag, and uploads an Actions artifact named
`Fieldfolio-v2.2.0-debug` containing the debug-signed APK and `SHA256SUMS`. Download it from the
workflow run. This is an installable test artifact; debug signatures are not production signing
identities and are not guaranteed to remain stable between hosted-runner builds. No public
GitHub Release is created by a tag push alone.

## Optional signed draft

Create a protected GitHub environment named `release` and configure these secrets there:

| Secret | Value |
|---|---|
| `FIREBASE_CONFIG_BASE64` | Base64 of the real Fieldfolio google-services.json |
| `FIELDFOLIO_KEYSTORE_BASE64` | Base64 of the production/upload keystore |
| `FIELDFOLIO_STORE_PASSWORD` | Keystore password |
| `FIELDFOLIO_KEY_ALIAS` | Signing alias |
| `FIELDFOLIO_KEY_PASSWORD` | Key password |

Select the version tag when manually running the release workflow and enable `signed`. After
verification, the signed job materializes private inputs without printing them, keeps the key in
RUNNER_TEMP, builds and verifies the signed APK, and creates a **draft** release with checksums.
The cleanup step removes local credentials even when the job fails. Publishing the draft remains
a separate maintainer action.

Use the same signing identity for app upgrades. App ID `com.karandaev.fieldfolio`, database schema
9, logical-name/archive contracts and Firebase project ownership must remain compatible.

GitHub event/filter reference: https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow

From `Team Project/`, the Android Gradle project is `MobileApp/`; do not run Gradle
from the Git root. Android version tags retain the `v*` namespace; use a separate prefix
for server tags.
