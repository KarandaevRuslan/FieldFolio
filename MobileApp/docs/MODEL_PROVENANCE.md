# Built-in model

| Field | Value |
|---|---|
| Asset | `app/src/main/assets/models/road_damage_yolo_int8.tflite` |
| Size | 9,992,089 bytes |
| SHA-256 | `77d57d5220b5373c738630fe95f83f2cb7718e206399ab0379bfd39f89068b5b` |
| Catalog ID / name | `road-local` / Road damage · YOLO11s |
| Adapter | `DETECTION_NMS` |
| Label order | `D00`, `D10`, `D20`, `D40`, `D44`, `D50`, `Repair` |
| Catalog input | `[1, 640, 640, 3]`, `FLOAT32` |
| Catalog output | `[1, 300, 6]`, `FLOAT32` |

The seed definition is in `core/models/ModelCatalog.kt` under the application Kotlin package.
The `int8` filename describes the distributed export; it does not establish the interpreter's
input/output tensor types. Inspect the actual tensors before replacing the binary or changing
catalog metadata. Quantized weights can coexist with float external tensors.

No separate license or reproducible training-run record accompanies this binary. Do not infer
permission to redistribute weights from the MIT license on Fieldfolio-authored source. Dataset
rights, upstream model terms and weight provenance require their own records before distribution.
[Training and export](../../DetectionTrainProj/docs/TRAINING_AND_EXPORT.md) explains how to record
a new model; its example commands do not establish this binary's training history.

Verify the asset from `MobileApp/` with:

```sh
python -c "import hashlib,pathlib; p=pathlib.Path('app/src/main/assets/models/road_damage_yolo_int8.tflite'); print(p.stat().st_size, hashlib.sha256(p.read_bytes()).hexdigest())"
```
