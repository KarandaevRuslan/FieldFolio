# Changelog

## 2.2.0

- Replace excluded Java-era trees and historical patch/output folders with one canonical source layout.
- Separate authentication, settings, detection and report state operations while retaining active screen contracts.
- Introduce AppServices, remove feature dependencies on the application implementation, and move shared helpers below features.
- Group report editor, viewer, state, route and toolbar responsibilities; extract feed rendering and media/scroll components.
- Remove unregistered legacy report screens and duplicate unused analysis paths; preserve reusable clock/weather widgets.
- Retain application ID, Room schema 9, report/archive compatibility and all current element/model adapters.
- Export the current Room schema, restore the author-provided built-in model, and regenerate active web output only.
- Add English architecture/build/release/security/contribution documentation and executable architecture/regression checks.
- Add tag-triggered debug APK artifacts and an optional protected manual signed-release draft workflow.
