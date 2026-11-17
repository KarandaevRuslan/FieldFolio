# GitHub Actions and releases

GitHub reads workflows only from `.github/workflows/` at the **Git repository root**.
The old `MobileApp/.github/` must be removed after the files are migrated.

| Workflow | Trigger | Checks |
|---|---|---|
| `mobile-ci.yml` | Android path changes, manual, or reusable call | Java/Android, Python source tests, Vite/web tests, lint, APK |
| `server-ci.yml` | Server path changes or manual | JDK 17, Maven `verify`, build JAR artifact |
| `training-static.yml` | Training path changes or manual | Syntax of tracked Python sources only; **not** training/inference |
| `repository-ci.yml` | Shared GitHub/docs/policy changes or manual | Path layout, tracked artifacts/secrets and policy tests |
| `mobile-release.yml` | `v*` tags or manual | Reusable Mobile CI, tagged debug APK; optionally signed **draft** |

## Tag and release behavior

Android continues to use `v<versionName>` (example: `v2.2.0`), validated against
`MobileApp/gradle.properties` by `MobileApp/scripts/release/check_tag.py`.
**Do not** use `v*` tags for Server; a suggested convention is `server-v<version>`.
This package does not add automatic server deployment, Docker builds, APK publishing,
or model-training jobs. Tag pushes create a debug-signed **Actions artifact**, not a production
GitHub Release. A signed release requires a manual run on a version tag and the protected
`release` environment with secrets described in `MobileApp/docs/RELEASING.md`.

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
5. Keep the [root README](../README.md) and this workflow guide in sync when commands change.

## External services

Mobile CI does not need real Firebase keys for debug guest builds. Server CI only compiles;
no PostgreSQL, ClamAV daemon or Python inference server is started. To validate real server
behavior, create a separate integration workflow with isolated credentials and services.
Never let pull requests from forks access production signing or service credentials.
