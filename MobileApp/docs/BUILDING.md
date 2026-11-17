# Building Fieldfolio

## Toolchain

| Tool | Project requirement |
|---|---|
| Java | JDK 17, including javac |
| Gradle | 8.11.1 wrapper, distribution SHA-256 pinned |
| Android Gradle Plugin | 8.9.0 |
| Kotlin / Compose compiler | 2.1.20 |
| Android SDK | compile/target 36, minSdk 26 |
| Build tools | 35.0.0 |
| Node | 22.16.0 in CI; Vite requires a supported modern Node runtime |
| Python | 3.10+ for repository checks, test SDK bootstrap and source exports |

Open the repository root in Android Studio. Install the SDK platform/build tools with SDK Manager
and let the IDE create `local.properties`; it is deliberately absent from the source distribution.
Keep your existing Firebase configuration/signing files locally when replacing an older checkout.
The package name/application ID has not changed.

## Debug and offline guest use

The complete source archive includes active compiled web assets and the built-in model:

```sh
./gradlew :app:assembleDebug
```

On Windows use `gradlew.bat`. The debug APK is in `app/build/outputs/apk/debug/`.
No release key is needed. With no `app/google-services.json`, Gradle supplies clearly synthetic
local-development Firebase options so the app can initialize and use guest/offline reports.
These options are not a working account backend. For email/Google sign-in, put the real Firebase
Android configuration for `com.karandaev.fieldfolio` at `app/google-services.json`; the normal
Google Services plugin then generates the real resources. Configure its OAuth client/signing
certificate fingerprints as required by your Firebase project.

## Regenerate the frontend

Run after changes to web source, its pinned packages or API/help documentation:

```sh
npm --prefix acode-web ci --no-audit --no-fund
npm --prefix acode-web test
npm --prefix acode-web run build
python3 scripts/check_web_assets.py
```

The build regenerates both asset directories, syncs canonical English/Russian API docs into the
offline help assets and writes `app/src/main/assets/web-assets.json`. Stale historical hash-named
bundles are removed by Vite. Source archives include the current output so Android Studio does
not need Node for an unchanged frontend. Do not hand-edit the generated asset directories.

## Signed releases

Use a real Firebase configuration. Either copy `config/keystore.properties.example` to root
`keystore.properties` and replace its values locally, or set:

- `FIELDFOLIO_STORE_FILE` — absolute path to the signing keystore.
- `FIELDFOLIO_STORE_PASSWORD`.
- `FIELDFOLIO_KEY_ALIAS`.
- `FIELDFOLIO_KEY_PASSWORD`.

Then run `./gradlew :app:assembleRelease`. Release tasks fail if required inputs are missing;
they do not silently reuse a debug key. `assembleUnsignedRelease` is available for a release
configuration without APK signing, but still requires a real Firebase configuration.

`gradle.properties` is the single Android version source. Keep the npm package/lockfile versions
aligned when bumping the application version. Do not change the application ID or database
version as part of a routine release.

## Source distribution

After regeneration/checks, `python3 scripts/package_source.py /absolute/output/Fieldfolio-source.zip`
creates a complete checkout tree. It excludes credentials, local IDE/configuration state, SDKs,
node_modules, Gradle caches, build output, APKs and historical verification artifacts. It includes
the Gradle wrapper, source, current web output, model, schema, tests, licenses and documentation.
