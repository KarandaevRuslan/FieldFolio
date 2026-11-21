# Troubleshooting

| Symptom | Check | Action |
|---|---|---|
| Android Studio does not find the project | Opened repository root | Open `MobileApp/`, which contains `settings.gradle.kts` |
| SDK path fails on another machine | `MobileApp/local.properties` | Set a local SDK path or let Android Studio create the file |
| Gradle cannot start | JDK and wrapper cache | Use JDK 17; allow the pinned distribution and dependency downloads |
| Guest works but account sign-in fails | Firebase configuration and OAuth fingerprints | Add the correct `app/google-services.json` and project sign-in configuration |
| Release task reports missing signing inputs | `config/keystore.properties` or `FIELDFOLIO_*` | Use the paths and fields in [Releasing](../MobileApp/docs/RELEASING.md) |
| Asset integrity check fails | `scripts/check_web_assets.py` | Rebuild the web workspace; do not just replace hashes for changed JS |
| Document help differs from source | `sync-help.mjs --check` | Synchronize canonical EN/RU help and update the matching manifest |
| `/public/health` works but analysis fails | Auth token, worker `/health`, `/infer`, image constraints | Follow [API](../Server/docs/API.md) and [server tests](../Server/docs/TESTING.md) |
| Worker fails during import | Working directory and `best_models/best.pt` | Start in `Server/python-yolo/` after placing compatible weights |
| Phone cannot reach backend | Hostname/address | Use a reachable host; phone localhost is not your workstation |
| TLS fails on Android | Certificate host and trusted CA | Match SANs and configure local CA trust; see [TLS](../Server/docs/TLS.md) |
| Response is 429 | `Retry-After` and rate-limit headers | Wait for refill; inspect configured cost and UID/IP rule |
| VOC tool rejects `--root` | CLI flag names | Use `--source-root`, `--output-dir`; see [converter](../DetectionTrainProj/tools/README.md) |
| Test split missing | `--test-ratio` | It defaults to zero; create a nonzero split before `split=test` |
| pip cannot find a `+cu...` wheel | Python/platform and CUDA-specific index | Use the component's matching PyTorch index and a compatible interpreter |
| Metrics plot tool cannot find results | Hard-coded `results_path` | Edit that constant to the intended `results.csv` |

## Report save sequence

Saving in Acode returns source to the enclosing element form. Save the element, then save the
report. Discarding a form or locking the report can clear its private draft. See the
[user guide](USER_GUIDE.ru.md) and [Document API](../MobileApp/docs/api/document-en.md#workflow).

## Diagnosing a failure report

Include the command, component version, working directory and a redacted error. For Android,
include the affected element type and whether the problem survives reopening the report.
For inference, separate Java API response, Python worker response and input format. Omit tokens,
private report contents and account credentials from public logs.

## Converter installation

If `pip install -e ./tools` fails with multiple top-level packages, use the existing module
entry point from `DetectionTrainProj/tools/`: `python -m voc_yolo_builder --help`. The current
packaging metadata needs explicit discovery before editable installation can work. It is not
needed for direct module execution. Supply an absolute output directory when generating
`data.yaml`; a relative dataset path may be resolved against Ultralytics' configured dataset root.
