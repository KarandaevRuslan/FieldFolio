# GitHub Actions and releases

GitHub reads workflows only from `.github/workflows/` at the **Git repository root**.
The old `MobileApp/.github/` must be removed after the files are migrated.

| Workflow | Trigger | Checks |
|---|---|---|
| `mobile-ci.yml` | Android path changes, manual, or reusable call | Java/Android, Python source tests, Vite/web tests, lint, APK |
| `server-ci.yml` | Server path changes or manual | JDK 17, Maven `verify`, build JAR artifact |
| `training-static.yml` | Training path changes or manual | Syntax of tracked Python sources only; **not** training/inference |
| `repository-ci.yml` | Shared GitHub/docs/policy changes or manual | Path layout, tracked artifacts/secrets and policy tests |
| `mobile-release.yml` | `v*` tags or manual | Reusable Mobile CI, signed Release APK and **draft** GitHub Release |

## Tag and release behavior

Android continues to use `v<versionName>` (example: `v2.2.0`), validated against
`MobileApp/gradle.properties` by `MobileApp/scripts/release/check_tag.py`.
**Do not** use `v*` tags for Server; a suggested convention is `server-v<version>`.
This package does not add automatic server deployment, Docker builds, public APK publishing,
or model-training jobs. Pushing a matching version tag automatically runs the signed Release APK job and
creates a **draft GitHub Release** (APK and `SHA256SUMS`), after mobile CI succeeds.
The reusable CI also builds Debug APKs for tests, but the release contains only
the signed Release APK. The `release` environment must contain
`FIREBASE_CONFIG_BASE64` and `FIELDFOLIO_SIGNING_BUNDLE_BASE64`; see
`MobileApp/docs/RELEASING.md` for signing-bundle preparation. Published releases
are never overwritten by a rerun.

Workflows are path-filtered so a Server-only PR does not run the expensive Android suite.
A PR changing both projects triggers both suites. Branch protection should require relevant
jobs rather than unconditionally requiring a workflow that may be skipped due to path filters;
otherwise some clean PRs cannot be merged.

## Migration checklist

1. Move `.github` **to the root** using the supplied patch.
2. Configure GitHub Actions permissions (read-only by default); create `release` environment
   and secrets only when signed releases are needed.
3. Review `CODEOWNERS` and the default branch name (`main`). Dependabot version-update
   PRs are disabled by removing `.github/dependabot.yml`. Existing PRs require manual closure;
   if GitHub still opens security-update PRs, turn off **Dependabot security updates** in the
   repository's GitHub security settings separately.
4. Use Actions -> select `Repository policy and docs`, `MobileApp CI`, and `Server CI` to
   run the first workflows manually after pushing changes.
5. Add a link from your existing root `README.md` to `docs/README.md`.

## External services

Mobile CI does not need real Firebase keys for debug guest builds. Server CI only compiles;
no PostgreSQL, ClamAV daemon or Python inference server is started. To validate real server
behavior, create a separate integration workflow with isolated credentials and services.
Never let pull requests from forks access production signing or service credentials.
