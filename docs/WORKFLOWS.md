# GitHub Actions and releases

GitHub reads `.github/workflows/` at the repository root. Android, server and training
checks remain separate because their toolchains and runtime dependencies differ.

| Workflow | Events | Work performed |
|---|---|---|
| `mobile-ci.yml` | Relevant main pushes, pull requests, manual and reusable calls | Source checks, web tests/build, Android tests/lint, debug and device-test APK compilation, schema comparison, DOM integration |
| `server-ci.yml` | Relevant main pushes, pull requests and manual | JDK 17, Maven verify, JAR artifact |
| `training-static.yml` | Relevant main pushes, pull requests and manual | Parse tracked Python sources without GPU dependencies |
| `repository-ci.yml` | Shared policy/docs changes and manual | Documentation, repository layout and policy tests |
| `mobile-release.yml` | `v*` tags and manual | Call mobile CI; on tag refs build signed APK and create/update a draft release |

Path-filtered workflows may be skipped when their component is untouched. Configure branch
protection so a skipped component workflow does not block unrelated changes.

## Android tags

`MobileApp/gradle.properties` supplies `fieldfolio.versionName` and `fieldfolio.versionCode`.
`check_tag.py` requires the tag to match `v<versionName>`. Update npm package/lockfile versions
with the Android version. Use a separate tag namespace such as `server-v...` for server work.

The signed job runs when `github.ref_type == 'tag'`. A manual workflow on a branch runs
verification but skips that signed job; selecting a tag also satisfies the condition.
The signed job uses the protected `release` environment and:

| Secret | Value |
|---|---|
| `FIREBASE_CONFIG_BASE64` | Base64 contents of the real Android `google-services.json` |
| `FIELDFOLIO_SIGNING_BUNDLE_BASE64` | Bundle produced by `make_signing_bundle.py` |

After verification, it builds `assembleRelease`, checks the signature and attaches the APK
and `SHA256SUMS` to a draft GitHub Release. A rerun may replace draft assets. Published release
assets are protected by an explicit refusal in the workflow. Publication of the draft is manual.
See [Releasing](../MobileApp/docs/RELEASING.md) for local key and bundle preparation.

## Runtime coverage

Server CI does not start PostgreSQL, Firebase, ClamAV or the YOLO worker. Training CI does not
train a model. Mobile CI compiles an instrumentation APK but does not attach a physical device.
Use the component testing guides for these runtime checks.

Actions are commit-pinned. The repository contains no Dependabot version-update configuration;
GitHub security-update settings are managed separately. Review action and dependency updates
through normal code review.
