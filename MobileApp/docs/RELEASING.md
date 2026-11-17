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

## Signed GitHub Release from a version tag (automatic)

A pushed `v<versionName>` tag triggers **the signed `assembleRelease` build** and
creates a **draft GitHub Release**, with the APK and its SHA-256 checksum. This is
not a debug APK. The reusable mobile CI job still compiles Debug for tests; only
the signed Release APK is attached to the GitHub Release.

The job requires the GitHub environment `release` and two secrets:

| Secret | Purpose |
|---|---|
| `FIREBASE_CONFIG_BASE64` | Existing Base64 `google-services.json` |
| `FIELDFOLIO_SIGNING_BUNDLE_BASE64` | Base64 ZIP of the keystore and the four signing properties |

No separate password or alias GitHub secrets are used.

To create the signing bundle locally from `MobileApp/`:

```powershell
python .\scripts\release\make_signing_bundle.py --properties .\config\keystore.properties --output "$env:TEMP\fieldfolio-signing.b64"
```

Copy the **contents** of the temporary Base64 file into the environment secret
`FIELDFOLIO_SIGNING_BUNDLE_BASE64`. Remove the local Base64 file afterwards.
Keep the keystore private and backed up. The runner restores
`config/keystore.properties` and `ci-signing/release-key.jks` at build time.

**Before tagging**, make sure these four release scripts are committed:

```
scripts/release/check_tag.py
scripts/release/prepare_inputs.py
scripts/release/make_signing_bundle.py
scripts/release/create_keystore.ps1
```

An older `.gitignore` pattern `release/` accidentally ignored the
`scripts/release/` source directory. It has been corrected to `/release/`.
You can verify tracking with `git ls-files MobileApp/scripts/release` from the
Git repository root.

Update `gradle.properties` (`fieldfolio.versionName` and increasing
`fieldfolio.versionCode`), the version in `acode-web/package.json` and
`acode-web/package-lock.json`, and `CHANGELOG.md`. Commit and push these changes
**before** creating a new tag. For example, if the code is `2.2.1`:

```powershell
git push origin main
git tag -a v2.2.1 -m "Fieldfolio 2.2.1"
git push origin v2.2.1
```

The workflow validates that the tag matches `fieldfolio.versionName`, runs
mobile tests, builds a signed Release APK, verifies its signature, and uploads
`Fieldfolio-v2.2.1.apk` plus `SHA256SUMS` to **GitHub → Releases → Drafts**.
Review and publish the draft manually. Re-running the workflow can update an
existing draft, but never replaces files in a published release.

If you have **already pushed** an old `v2.2.0` tag at an older commit, pushing
new fixes to `main` does not alter that tag. Do not silently move existing
published tags: create a new version and tag after updating version metadata.
You can manually re-run the workflow against a tag only if that tag's commit
already includes the corrected scripts and workflow.

Repository CI no longer rejects the intentionally tracked calibration fixture
`DetectionTrainProj/calibration_image_sample_data_20x128x128x3_float32.npy`;
other generated `.npy` models/data remain forbidden by the repository policy.

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
