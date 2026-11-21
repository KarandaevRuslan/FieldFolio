# APK releases

Workflows live in the repository-root `.github/workflows/`. Run the local commands below in
`MobileApp/`, except Git commands, which may run at the repository root.

## Verification and signing

`mobile-ci.yml` verifies source policy, Python/web regressions, frontend assets, Android unit
tests, lint and debug/device-test APK compilation. Its debug artifact is a CI build.
`mobile-release.yml` calls that verification workflow, then builds a signed release APK for a
tag ref and creates or updates a **draft** GitHub Release. Publishing the draft is manual.

| Trigger | Behavior |
|---|---|
| Push `v*` tag | Verify, check tag/version match, sign APK, create/update draft |
| Manual dispatch on a tag | Same signed job, using the selected tag's code/configuration |
| Manual dispatch on a branch | Verification runs; signed job is skipped |

Configure the GitHub environment `release` with these secrets:

| Secret | Contents |
|---|---|
| `FIREBASE_CONFIG_BASE64` | Base64 of the real `app/google-services.json` |
| `FIELDFOLIO_SIGNING_BUNDLE_BASE64` | Base64 ZIP containing the keystore and four signing properties |

Create the bundle from an existing local signing configuration:

```powershell
python .\scripts\release\make_signing_bundle.py --properties .\config\keystore.properties --output "$env:TEMP\fieldfolio-signing.b64"
```

Copy the file contents into the secret, then remove the temporary Base64 file. The runner
restores `config/keystore.properties` and `ci-signing/release-key.jks`. Keep the original key
and its credentials backed up privately; the bundle is as sensitive as the key itself.

## Version and tag

Update `fieldfolio.versionName` and the increasing `fieldfolio.versionCode` in
`gradle.properties`; align `acode-web/package.json` and its lockfile, and update `CHANGELOG.md`.
Commit the version, source, generated assets and workflow helpers before tagging. Substitute
the actual version in this example:

```sh
git push origin main
git tag -a v2.2.1 -m "Fieldfolio 2.2.1"
git push origin v2.2.1
```

The tag must exactly match `v` plus `fieldfolio.versionName`. The workflow verifies the APK
signature with `apksigner` and attaches `Fieldfolio-v<version>.apk` and `SHA256SUMS` to the draft.
Review the draft and its artifacts before publishing. Re-runs can update an existing draft;
they refuse to overwrite a published release. A later branch commit does not update an old tag.

## Local signed build

Use `config/keystore.properties` with the four properties described in [Building](BUILDING.md),
or their environment equivalents, plus a real Firebase Android configuration:

```sh
./gradlew :app:assembleRelease
```

For an app already distributed to users, retain its signing identity. If creating the first
identity, copy `config/keystore.properties.example` to `config/keystore.properties`, set passwords,
alias and matching `owner`/`cn`, then run on PowerShell:

```powershell
.\scripts\release\create_keystore.ps1
```

This creates `config/release.keystore` and updates `storeFile`. It refuses to overwrite an
existing key. Signing properties alone cannot recover a lost keystore.
