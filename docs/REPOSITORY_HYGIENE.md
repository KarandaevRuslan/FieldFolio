# Git hygiene and sensitive files

The root `.gitignore` controls **all three subprojects**. Nested
`MobileApp/.gitignore` and `Server/.gitignore` add local project-specific rules.
Patterns now cover downloaded archives (`*.zip`, `*.7z`, `*.rar`, tarballs), patch files,
IDEs, Gradle/Maven/Python/Node caches, generated ML artifacts, TLS/signing material,
Firebase credentials, YOLO outputs and local runtime data.

Important exceptions: `MobileApp/acode-web/vendor/*.tgz` is a required bundled dependency;
`MobileApp/acode-web/third_party/acode/patches/*.patch` contains maintained source patches,
and checked-in ZIP test fixtures under test resource/fixture directories are allowed.
`.gitignore` does **not** remove already tracked files.

```powershell
# Run at Team Project root to inspect ignored/untracked data without deleting anything:
git status --short --ignored

git check-ignore -v -- MobileApp/Fieldfolio-source.zip
# If a generated archive is already tracked, remove it from the index only:
git rm --cached -- MobileApp/Fieldfolio-source.zip
```

Review `git ls-files` for any previously committed credentials, archives, datasets or
certificates; `git rm --cached` stops future tracking but does **not** erase past Git history.
If secrets were committed, revoke/rotate them and coordinate a history cleanup separately.
Do not run `git clean -fdx`, `git reset --hard`, or mass `git rm` to install this package.

The root `README.md` is not replaced because the original root README was not included in
the user-provided materials. The root documentation landing page is `docs/README.md`.
