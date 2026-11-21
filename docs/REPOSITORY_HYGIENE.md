# Repository hygiene

Keep source, tests, dependency locks, approved fixtures and documentation in Git. Keep local
credentials, IDE state, build outputs and training runs out of the tracked source tree.

| Retain | Keep local or publish as a separate artifact |
|---|---|
| Root `.github/`, Gradle wrapper, source and lockfiles | `node_modules`, `.gradle`, `target`, `.venv`, `__pycache__` |
| Current WebView output and input/output manifest | APK/AAB files, release output and exported source ZIPs |
| Approved `road_damage_yolo_int8.tflite` asset | Other `.pt`, `.onnx`, `.tflite`, model/run outputs |
| Named calibration fixture allowed by policy | Datasets, arbitrary `.npy`/`.npz`, local experiment samples |
| Vendor Nexus `.tgz`, maintained Acode patches and test fixtures | Unrelated downloaded archives and patch bundles |
| Example configuration without credentials | Firebase JSON, real application.yml, signing/TLS keys, passwords |

`python3 scripts/check_monorepo.py` implements root tracked-file rules.
`MobileApp/scripts/check_repository.py` implements the mobile source-export inventory.
`.gitignore` prevents new accidental additions; it does not remove existing tracked files.

```sh
git status --short --ignored
git check-ignore -v -- MobileApp/Fieldfolio-source.zip
git ls-files MobileApp/scripts/release
```

If a generated file is already tracked, review it and use `git rm --cached -- <path>` to
stop tracking while preserving the local file. This does not erase repository history.
Revoke exposed credentials separately. Avoid bulk deletion commands when applying documentation.

The diagnostic inventories `scripts/items_list.txt` and the dataset listings under
`DetectionTrainProj/struct_info/` are captured listings, not current build or deployment contracts.
