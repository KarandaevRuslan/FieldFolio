# Training, evaluation and export

Commands start in `DetectionTrainProj/`. Use an isolated environment and the required dataset,
weights and GPU drivers. Training and server inference have distinct pinned dependency sets.

## Environment

The training requirements pin Torch 2.5.1+cu121, torchvision 0.20.1+cu121, torchaudio
2.5.1+cu121 and Ultralytics 8.4.52. Use a compatible Python version (for example 3.11) and
matching PyTorch wheels. Example for that CUDA build:

```sh
python -m venv .venv
```

Activate `.venv` with your shell, then:

```sh
python -m pip install --upgrade pip
python -m pip install -r requirements.txt --extra-index-url https://download.pytorch.org/whl/cu121
python -c "import torch; print(torch.__version__, torch.cuda.is_available())"
```

The server worker pins Torch 2.9.0+cu128 and has its own requirements; do not install both into
one environment. A CPU-only setup needs a separately resolved dependency set instead of
assuming these CUDA pins will change automatically. PyTorch's [official version table](https://pytorch.org/get-started/previous-versions/)
describes matching wheels. The requirements files use UTF-16 with a BOM; preserve that encoding
when editing them or explicitly convert the whole file to UTF-8.

## Train and evaluate

First generate and inspect [the dataset](DATASET.md) from the `tools/` working directory;
return to `DetectionTrainProj/` for the commands below. The following are starting parameters,
not reported experimental results:

```sh
yolo detect train model=yolo11s.pt data=./converted_to_yolo_datasets/run_001/data.yaml imgsz=768 epochs=100 patience=20 batch=8 device=0 project=./runs name=road_damage_001 exist_ok=False
yolo detect val model=./runs/road_damage_001/weights/best.pt data=./converted_to_yolo_datasets/run_001/data.yaml split=val imgsz=768 device=0
yolo detect predict model=./runs/road_damage_001/weights/best.pt source=./sample_images imgsz=768 conf=0.25 iou=0.45 device=0 project=./runs name=predict_001 exist_ok=False
```

Supply `yolo11s.pt` locally or allow its upstream download. `device=0` requires a CUDA device;
select a supported device and batch for the actual machine. Use `split=test` only when the
converter produced that split. Resume an interrupted run from its `weights/last.pt` with
`yolo detect train model=./runs/road_damage_001/weights/last.pt resume=True`.

Keep the dataset/split map, command/configuration, dependency versions, hardware, `results.csv`,
validation metrics and representative predictions with each run. Choose deployment thresholds
using held-out evaluation. The examples do not establish the bundled Android model's accuracy.

## TFLite export

```sh
yolo export model=./runs/road_damage_001/weights/best.pt format=tflite imgsz=640 int8=True data=./converted_to_yolo_datasets/run_001/data.yaml fraction=0.1 nms=True
```

Ultralytics export can require additional TensorFlow/conversion dependencies; those are not
fully pinned by the training requirements. Resolve and record an export environment separately.
Use representative calibration images. The tracked NumPy calibration fixture is a separate
artifact; this command calibrates from `data.yaml` and does not automatically consume that file.

Inspect the produced `.tflite` tensors and labels. INT8 conversion does not imply that the
external input/output types are INT8. Android's built-in adapter expects NMS rows with six
values; verify shape, column order, box normalization and label IDs against its decoder.
Validate the exported graph on known inputs and compare results with the source model before
copying it into Android. Record SHA-256, size, run ID, export command and applicable model/data terms.

## Deployment and utility scripts

| Artifact/tool | Action |
|---|---|
| Server `.pt` | Copy the approved detector to `Server/python-yolo/best_models/best.pt`; restart/probe the worker |
| Android `.tflite` | Import through the catalog or deliberately replace the bundled asset and matching catalog metadata; verify [model provenance](../../MobileApp/docs/MODEL_PROVENANCE.md) |
| `tools/strip_model.py` | `python tools/strip_model.py ./runs/road_damage_001/weights/best.pt`; creates a unique `_stripped` copy, leaves original |
| `tools/make_plots_of_metrics_for_run.py` | Set `results_path`, install pandas if needed, then run; writes training, validation and learning-rate PNGs |
| `example_commands/yolo_multitool.py` | Fill required path constants and make the `tools` package directory importable for build-dataset (for example via PYTHONPATH); `--help` lists build-dataset/predict/validate/train/resume-training; no export action |

The plot utility imports pandas in addition to training dependencies and opens plot windows.
Its fixed CSV columns are detection metrics; adapt it if a different task/version changes columns.
Load only trusted `.pt` files: these are executable serialization inputs, not inert JSON.
No repository workflow automatically deploys trained/exported models.
