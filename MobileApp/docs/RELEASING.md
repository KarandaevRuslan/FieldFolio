# GitHub Actions and APK releases

The workflows are stored in the **monorepo root** `.github/workflows/` and are ready to commit to a repository owned by
[KarandaevRuslan](https://github.com/KarandaevRuslan/). Preparing them does not create a repository,
push commits/tags, configure secrets or run GitHub Actions remotely.

## CI

`../../.github/workflows/mobile-ci.yml` runs on MobileApp changes to main, relevant pull requests, manual requests and reusable calls. It checks source
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

`../../.github/workflows/mobile-release.yml` calls CI, rejects a mismatching/invalid tag, and uploads an Actions artifact named
`Fieldfolio-v2.2.0-debug` containing the debug-signed APK and `SHA256SUMS`. Download it from the
workflow run. This is an installable test artifact; debug signatures are not production signing
identities and are not guaranteed to remain stable between hosted-runner builds. No public
GitHub Release is created by a tag push alone.

## Optional signed draft (one signing secret)

The Android Gradle build still reads **`MobileApp/config/keystore.properties`**, using the same
`storeFile`, `storePassword`, `keyAlias`, and `keyPassword` keys as local builds. GitHub-hosted
runners cannot read your local file, so the release workflow reconstructs it securely from one
Base64 ZIP secret. **No separate password/alias GitHub secrets are needed.**

Create the protected GitHub environment named `release` (if not created already). It needs only:

| Secret | Value |
|---|---|
| `FIREBASE_CONFIG_BASE64` | Base64-encoded `google-services.json` (keep your existing value) |
| `FIELDFOLIO_SIGNING_BUNDLE_BASE64` | One Base64 ZIP containing your signing key and the four signing properties |

To generate the second secret, on your own Windows machine run from `MobileApp/`:

```powershell
python .\scripts\release\make_signing_bundle.py --properties .\config\keystore.properties --output "$env:TEMP\fieldfolio-signing.b64"
```

`storeFile` in your local properties must point to an existing `.jks` file. Absolute paths
and paths relative to `MobileApp/` are supported. The script does not modify your original
properties or key; it bundles both and substitutes a portable `storeFile` path inside the
archive for the Linux CI runner.

Go to **GitHub → Settings → Environments → release → Environment secrets** and create
`FIELDFOLIO_SIGNING_BUNDLE_BASE64` by pasting the **entire contents** of the temporary
`fieldfolio-signing.b64` file. Do not paste its filename. Keep the already configured
`FIREBASE_CONFIG_BASE64` unchanged. Remove the temporary file after adding the secret:

```powershell
Remove-Item "$env:TEMP\fieldfolio-signing.b64"
```

Do not commit, send, or attach the Base64 value: it contains your signing key and passwords.
In CI, the workflow writes `MobileApp/config/keystore.properties` and
`MobileApp/ci-signing/release-key.jks` only on the ephemeral runner, then removes them.
The existing `app/build.gradle.kts` reads those files without any signing changes.

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

## Create a new local signing identity (only if the old key is lost / never used)

From `MobileApp/`, first fill in `config/keystore.properties` with your desired
`storePassword`, `keyAlias`, and `keyPassword`. Then run:

```powershell
.\scripts\release\create_keystore.ps1
.\gradlew.bat :app:assembleRelease
```

The first command creates `config/release.keystore` using those exact credentials and
updates only `storeFile` in `config/keystore.properties`. It refuses to overwrite an
existing keystore. The signing identity cannot be recovered from the properties file
alone. Back up the keystore separately and securely.
