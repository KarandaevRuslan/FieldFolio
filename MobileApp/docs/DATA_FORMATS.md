# Report data and portable formats

Paths in this guide refer to `app/src/main/kotlin/com/karandaev/fieldfolio/` unless stated.
Serialization contracts are defined in source; do not hand-edit private database/object files.

## Storage ownership

| Layer | Contract and source |
|---|---|
| Room | Schema 9, exported snapshots in `app/schemas/`; migrations 7→8 add library storage and 8→9 add display names |
| Library | `core/library/ReportLibrary.kt` and related operations coordinate access, transactions and object storage |
| Report packet | `core/library/ReportPacket.kt`: ZIP with `report.json` and `resources/...` |
| Ordered document | `core/document/ReportDocument.kt` and `DocumentTree.kt`: stable node IDs, parent IDs, sibling order and typed content |
| Local model catalog | `core/models/ModelCatalog.kt`: private `model_catalog/catalog.json` and separate `.tflite` binaries |

Logical names determine VFS paths; display names are optional reader captions. A document tree
permits at most 1,000 nodes, requires unique sibling names under the shared naming rules and
rejects cycles. Moving/renaming a node changes its path while preserving its identity. A hidden
node remains available to Documents in the same unlocked report.

Element kinds are `FOLDER`, `HEADING`, `TEXT`, `DOCUMENT`, `TEXT_FILE`, `IMAGE`, `AUDIO`,
`LOCATION`, `WEATHER`, `CLOCK` and `MODEL_RESULT`. Referenced bytes belong to the packet;
copy/import operations must preserve or remap resource references together with metadata.

## Exports

| Export | Representation | Protection and portability |
|---|---|---|
| Library/report selection | `geoinspect-library` manifest version 1, `manifest.json`, `objects/<id>.bin`; report snapshots use `geoinspect-report` | Preserves library hierarchy/protection metadata; an optional archive password encrypts the outer stream |
| Selected document subtrees | Plain ZIP from `DocumentElementArchive` with selected nodes, ancestor structure, resources and cached widget/audio metadata | Explicit plaintext snapshot; report/folder passwords do not encrypt this export |
| Standalone model result | Plain ZIP: `format.json` (`GeoInspect.ModelResult`, version 1), `result.json`, `resources/result/...` | Contains the result and available image/mask resources; no weights or endpoint credentials |

Report snapshots accept format versions 1–2. Missing resource references must be declared in
`missingResources`; arbitrary filesystem paths are not portable resource references. Model
results retain model/label/output snapshots so they can still be viewed after a model is removed.
An imported result does not install or rerun a model.

## Size and path validation

| Boundary | Limit in current code |
|---|---|
| Library ZIP total | 128 MiB, including expanded-size checks on import |
| Stored report object | 64 MiB |
| Default ZIP entry/resource read | 48 MiB |
| Report JSON | 8 MiB |
| Packet resource set | At most 500 files and 48 MiB combined |
| Standalone result resources | 128 MiB combined |

The limits are independent; the first exceeded boundary rejects the operation. ZIP paths reject
absolute paths, backslashes, colons, control characters, empty/dot components and duplicate entries.
These constraints are implemented in `LibraryZip`, `ReportPackets` and the export/import handlers.

## Passwords and drafts

`LibraryCrypto` uses AES-GCM with PBKDF2-HMAC-SHA256 (600,000 iterations for new protection).
Keep library protection and outer archive encryption distinct. A plain exported file can contain
sensitive text, images or coordinates even if the original report was protected.

`DocumentViewModel` owns active drafts/resource bytes. Runtime access and draft state are revoked
on locking; report content is not placed in Android saved-state bundles. Save inside Acode first,
then save the element form and the report. [User guide](../../docs/USER_GUIDE.ru.md) describes that flow.
