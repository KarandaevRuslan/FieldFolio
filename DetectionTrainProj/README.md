# Dataset preparation and YOLO training

This workspace contains the Pascal VOC→YOLO converter, training utilities, pinned Python
requirements, calibration data and example commands. Dataset images, full training runs and
server `.pt` weights are local inputs/outputs; inspect availability before running a command.

| Task | Entry point |
|---|---|
| Convert regional VOC annotations | [tools/README.md](tools/README.md), [dataset guide](docs/DATASET.md) |
| Set up training, validate, predict, export | [Training and export](docs/TRAINING_AND_EXPORT.md) |
| Copyable PowerShell examples | [Dataset](example_commands/build_dataset.txt), [train](example_commands/train.txt), [validate](example_commands/test.txt), [predict](example_commands/predict.txt), [export](example_commands/export.txt) |
| Configurable wrapper | `example_commands/yolo_multitool.py --help`; fill its path constants first |
| Historical dataset inventory | [struct_info](struct_info/README.md) |

Training examples run in `DetectionTrainProj/`; the dataset-conversion example runs in
`DetectionTrainProj/tools/`. Start the converter as `python -m voc_yolo_builder` there.
GPU training uses a separate environment from `Server/python-yolo/`: their pinned Torch/CUDA versions differ.

Outputs are not automatically deployed. The server loads `Server/python-yolo/best_models/best.pt`;
Android uses its catalog/asset contract. Preserve label order, preprocessing, tensor metadata,
licenses and a checksum when promoting an artifact. See [Android models](../MobileApp/docs/MODELS.md).
