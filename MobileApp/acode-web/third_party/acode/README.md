# Acode provenance and local adaptation

The embedded editor derives from the user-supplied Acode snapshot identifying upstream commit
`6962af1e805b8996e743df97cad4df3ecef687e5` (Acode-Foundation/Acode).
Preserved licenses: `LICENSE` (MIT) and `RobotoMono-OFL.txt` (font).

Adapted source is in `acode-web/src/acode/vendor/`. Service adapters are in
`acode-web/src/acode/host/`. The privileged entry point is `acode-web/src/editor/index.js`.
Fieldfolio embeds the editor, not the complete Cordova IDE, workspace or plugin manager.

Local adaptations cover selection/clipboard integration, single-pane hosting, read-only behavior,
lifecycle, syntax/completion, scrollbar corrections, logging and theme projection. The sandboxed
HTML reader reuses the vendor scrollbar without loading the privileged editor host. The actual
bundle graph is checked by `acode-web/tests/architecture-boundaries.test.mjs`.

Historical transplant inventories referencing importer/upstream trees absent from the supplied
project have been removed. Unmodified upstream hashes are not asserted for adapted files.
Current functional coverage lives in the Acode runtime tests.
