# Pascal VOC → YOLO builder

Python 3.9+; no required third-party dependencies. Run from **`DetectionTrainProj/tools/`**:

```sh
python -m voc_yolo_builder --help
python -m voc_yolo_builder --source-root ../source_dataset --output-dir "$PWD/../converted_to_yolo_datasets/run_001" --sample-fraction 1 --validation-ratio 0.1 --test-ratio 0.1
```

The absolute output path makes the generated `data.yaml` independent of Ultralytics' dataset
search directory. Pillow is optional for missing/invalid XML dimensions: install it separately
with `python -m pip install Pillow` when that fallback is required.

Use the module entry point above. The current `pyproject.toml` has no explicit package-discovery
configuration; default setuptools installation fails on the two top-level package directories
(`voc_yolo_builder`, `other_tools`). Installing the project with `pip install -e` needs a packaging
configuration change first. It is not required for running the converter from this directory.

Use a new empty output directory for each conversion: the writer does not clean old output.

## CLI

| Argument | Default | Meaning |
|---|---|---|
| `--source-root` | Required | Parent of `<region>/train/images` and `<region>/train/annotations/xmls` |
| `--output-dir` | Required | Destination with `images`, `labels` and `data.yaml` |
| `--max-output-images` | 20000 | Upper bound on selected images |
| `--sample-fraction` | 0.25 | Fraction of eligible pairs to select, in (0,1] |
| `--validation-ratio` | 0.10 | Validation fraction, greater than zero |
| `--test-ratio` | 0 | Optional test fraction; validation + test must be less than 1 |
| `--random-seed` | 42 | Sampling/shuffle seed |
| `--sampling-strategy` | balanced | `balanced` or `random` |
| `--target-min-images-per-class` | 1200 | Best-effort balanced selection target |
| `--min-source-images-per-class` | 800 | Drop classes with fewer source images |
| `--use-hardlinks` | Off | Try hard links for images; copying is the fallback |

The documented ranges describe valid configuration. `BuildConfig.validate()` defines the
checks, but the current CLI/builder does not call it; check values before starting.
The class filter runs before sampling. A tiny dataset needs a smaller
`--min-source-images-per-class`; otherwise all classes can be excluded. Counts are images
containing a class, not box counts. Inspect the final class map in `data.yaml`.

See [Dataset preparation](../docs/DATASET.md) for validation and reproducibility limits.
`strip_model.py` accepts a positional `.pt` path and writes a separate `_stripped` file.
`make_plots_of_metrics_for_run.py` uses an editable `results_path` constant and requires pandas;
it has no CLI path option. Details are in [Training and export](../docs/TRAINING_AND_EXPORT.md).
