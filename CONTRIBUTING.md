# Contributing to Fieldfolio

Start with [architecture](docs/ARCHITECTURE.md) and the component guide. Keep Android changes
in `MobileApp/`, backend changes in `Server/`, and training tools in `DetectionTrainProj/`.

Before a change, identify the affected contract: report format, Room migration, logical names,
model input/output, HTTP endpoint or build/release behavior. Update the corresponding guide
along with the implementation. [Documentation maintenance](docs/DOCUMENTATION.md) lists the
canonical files and generated outputs.

- Preserve app identity and stored data unless the change includes a migration.
- Put regression tests near the changed behavior; record commands and actual results in the PR.
- Use [Android contribution guidance](MobileApp/CONTRIBUTING.md) for package and lifecycle rules.
- Use [server tests](Server/docs/TESTING.md) for API integration and service prerequisites.
- Keep datasets, model training outputs, private reports and credentials out of commits.
- Review `git diff --check`, documentation checks and the relevant component checks.

CI and issue templates live in the root `.github/`. Refer to [WORKFLOWS.md](docs/WORKFLOWS.md)
for path filters, tag releases and environment secrets.
