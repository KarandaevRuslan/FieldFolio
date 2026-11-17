# Contributing to Team Project

Start with [repository architecture](docs/ARCHITECTURE.md) and the relevant project guide.
Keep Android changes in `MobileApp/`, Java backend changes in `Server/`, and model-training
changes in `DetectionTrainProj/`. The root `.github/` holds CI and community templates.

- Explain behavior and add regression tests near the code changed; do not claim an unrun test passed.
- Keep API contracts, local report formats, Room schemas and backend security policies stable
  unless a migration is reviewed.
- For Android guidance, follow [`MobileApp/CONTRIBUTING.md`](MobileApp/CONTRIBUTING.md).
- For server setup and source boundaries, follow [`Server/README.md`](Server/README.md).
- Never include Firebase/service-account secrets, signed APK keys, private reports, user GPS
  history, local datasets, model training outputs or operational TLS certificates in a PR.
- `git status --short` and `git diff --check` should be clean of unintended artifacts.
- Document commands actually executed and limitations in every PR.
