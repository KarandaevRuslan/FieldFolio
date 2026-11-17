# Contributing

Start with [ARCHITECTURE.md](ARCHITECTURE.md) and the [component map](docs/architecture/MAP.md).
Use the pinned toolchain in [Building](docs/BUILDING.md).

- Keep the application ID and stored packet/database contracts stable unless a migration is part
  of the change. Preserve imported model snapshots, raw resources and explicit VFS membership.
- Put immutable mutation/validation invariants at the core boundary. UI validation is feedback,
  not the only enforcement point. Do not duplicate a tree operation in multiple forms.
- Keep Compose rendering, lifecycle-owned state and IO/inference responsibilities separate.
  Existing ViewModel member methods are screen-facing facades; internal operation groups share
  that owner/scope. Avoid global scopes and new static copies of private report state.
- The viewer must not import editor screens; generic syntax, metadata labels and UI helpers
  belong below the feature boundary. New report elements must handle archives and read-only VFS APIs.
- Document substantial functions/contracts with English KDoc/JSDoc. Explain ownership, threading,
  invariants and failure/cancellation behavior rather than restating names.
- Kotlin uses ktfmt 0.55 Google style (two spaces); Python uses four spaces. Vendor snapshots keep
  their upstream formatting. Run `python3 scripts/format_kotlin.py` and `--check` to verify formatting.
- Edit help in `docs/api`; regenerate offline assets with `npm --prefix acode-web run build`.
- Add meaningful regression tests for actual triggers, including final Save after nested Acode,
  rotation, lock/revocation and portrait/landscape layouts. Do not replace runtime libraries with
  mocks that merely mirror the implementation being tested.
- Run [Testing](docs/TESTING.md). Report checks actually run and physical-device limits in PRs.
  Keep credentials, local paths, SDKs, caches, generated APKs and test screenshots out of source commits.
