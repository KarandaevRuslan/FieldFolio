# Dataset preparation

Run converter commands from `DetectionTrainProj/tools/` as described in
[tools/README.md](../tools/README.md).

## Source layout and conversion

Each regional directory contains `train/images/<stem>.jpg` (also jpeg/png/bmp/webp) and
`train/annotations/xmls/<stem>.xml`. Discovery pairs files with matching stems and ignores
unpaired images. The XML supplies Pascal VOC classes, image dimensions and bounding boxes;
Pillow can supply missing dimensions when the optional dependency is installed.

```sh
python -m voc_yolo_builder --source-root ../source_dataset --output-dir "$PWD/../converted_to_yolo_datasets/run_001" --sample-fraction 1 --max-output-images 40000 --validation-ratio 0.1 --test-ratio 0.1 --sampling-strategy balanced --target-min-images-per-class 1200 --min-source-images-per-class 800 --random-seed 42
```

Use an absolute output path: `data.yaml` writes the path as supplied, while Ultralytics can
resolve a relative dataset `path` against its own dataset directory.

Use a fresh destination. Existing files are not purged, so reusing a directory can leave stale
images/labels from a previous selection. Hard links save space when supported but share image
bytes with the source; treat the source as immutable or use normal copying.

## Class and split contracts

Classes below the source-image threshold are removed before selection; the remaining names
form the generated class-index map. Balanced sampling tries to improve rare-class coverage
within the fraction/image limits; it does not guarantee its per-class target. Labels are YOLO
rows `class_id x_center y_center width height` normalized to image dimensions. A negative
example can have an empty label file.

Outputs are `images/train`, `images/val` and optionally `images/test`, corresponding `labels`
directories and `data.yaml`. Its `path` reflects the destination used at generation time; update
that path when moving the dataset. `names` is authoritative for this dataset and may differ from
a previously exported model's labels. A test split exists only when selected test images exist.

The tool splits individual image/XML pairs. It does not group related video frames, road
segments, subjects or regions. For a geographically independent evaluation, design those
partitions separately and check for duplicates/leakage before interpreting metrics.

## Acceptance checks

1. Inspect discovery counts and all warnings; verify the intended regions were found.
2. Compare `data.yaml` labels with the annotation classes and planned model outputs.
3. Check image/label stem pairing, class IDs and normalized box geometry; inspect sample overlays.
4. Confirm train/val/test membership and detect duplicate content across splits.
5. Record source dataset/version/license, command, Python/tool versions, seed and output checksums.

The seed controls implemented sampling, but filesystem enumeration/order and source contents
also affect the result. Retain the generated split membership when exact reproduction matters.
The historical [inventory files](../struct_info/README.md) do not replace a current input manifest.
