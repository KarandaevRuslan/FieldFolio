# Bundled model provenance

`app/src/main/assets/models/road_damage_yolo_int8.tflite` restores the binary supplied by the
project author in this conversation. It was absent from the latest trimmed MobileApp source ZIP.

SHA-256: `77d57d5220b5373c738630fe95f83f2cb7718e206399ab0379bfd39f89068b5b`.
Size: 9992089 bytes.

The existing catalog retains the adapter, label order and tensor metadata. This refactor does
not retrain, convert or rewrite the weights. No separate model license was supplied; the root
MIT grant is not asserted for these weights.
