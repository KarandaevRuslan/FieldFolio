# Models and result adapters

Model code lives in `app/src/main/kotlin/com/karandaev/fieldfolio/core/models/` and the
`core/inspectionapi/` integration. Models execute on an explicitly chosen image/numeric input;
result viewing is independent from running inference again.

## Execution sources

| Source | Setup | Execution boundary |
|---|---|---|
| Built-in TFLite | Bundled `road-local` asset | Device interpreter; no server required |
| Imported TFLite | Import a compatible binary and configure its adapter/labels | Private model catalog, separate from reports |
| Server adapter | Base URL, working network/TLS and Firebase session | Java `/v1/detect`; Java forwards normalized bytes to Python |

The built-in detector uses `DETECTION_NMS`, input `[1,640,640,3]` and output `[1,300,6]` with
catalog labels `D00,D10,D20,D40,D44,D50,Repair`. See [asset provenance](MODEL_PROVENANCE.md).
An arbitrary `.tflite` file is not necessarily compatible with that adapter.

## Adapter selection

Inspect `LocalModel.kt`, `CatalogModelRunner.kt` and the dedicated runner implementations before
registering a new model. The catalog distinguishes post-NMS detection from raw YOLO output,
segmentation, classification, regression and supported numeric/text outputs. Adapter selection
must match the actual tensors and postprocessing; a task name alone is insufficient.

The image runner handles NHWC/NCHW and FLOAT32/UINT8/INT8 image tensors with its configured
normalization and quantization. Current image dimensions are bounded at 2,048 per side; output
allocation is bounded at 64 MiB. These checks reject incompatible or excessive tensors rather
than turning every imported graph into a supported model.

For each model, record the binary SHA-256, tensor shapes/types, channel order, normalization,
quantization scale/zero point, class order, coordinate convention and adapter. Check a known
image, an empty detection case, crop/rotation and threshold behavior. Verify the actual output
against the chosen decoder before adding it to the catalog.

## Results and image geometry

`ModelResultData` stores portable boxes, layers/masks, scores, numeric values or text as relevant
to its output. Results include model metadata snapshots and resource references; they do not
require the original imported weights for display. Crop/rotation and result projection must use
the same image geometry as inference. Do not interpret normalized server boxes as pixel values.

The Java server currently returns detection boxes only. Richer local result types do not imply
that `/v1/detect` implements segmentation, classification or text generation.

## Import/export and deployment

Catalog metadata and binaries are separate from report packets. Removing a catalog entry must
not invalidate saved results. A standalone result ZIP contains snapshots and available resources,
not executable weights or account credentials; it is unencrypted. See [Data formats](DATA_FORMATS.md).

[Training/export](../../DetectionTrainProj/docs/TRAINING_AND_EXPORT.md) produces artifacts for
manual inspection and deployment. No workflow automatically copies new weights into Android
or the worker. Server `.pt` and Android `.tflite` artifacts have separate dependencies and checks.
