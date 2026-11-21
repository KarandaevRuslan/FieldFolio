# YOLO inference worker

`app.py` exposes a FastAPI service used by the Java API. Start it with this directory as the
working directory. The module loads `./best_models/best.pt` immediately, so weights must be
present and compatible before starting Uvicorn.

## Environment and start

Use a dedicated virtual environment. The checked-in requirements capture a CUDA 12.8 stack
with PyTorch 2.9.0, torchvision 0.24.0 and Ultralytics 8.3.248. This differs from the training
workspace. Use a compatible Python/platform; CUDA wheels need the matching PyTorch index.

```sh
python -m venv .venv
# Activate .venv using the command for your shell.
python -m pip install -r requirements.txt --extra-index-url https://download.pytorch.org/whl/cu128
python -m uvicorn app:app --host 127.0.0.1 --port 8000
```

For a different CPU/GPU environment, create a separately tested dependency set instead of
assuming the CUDA lockfile applies unchanged. See [PyTorch installation versions](https://pytorch.org/get-started/previous-versions/).

## Endpoints

| Route | Request | Response |
|---|---|---|
| `GET /health` | None | `{"status":"ok"}` |
| `POST /infer` | Raw image bytes in the body | `{"detections":[...]}` |

The worker converts the input to RGB and runs `model.predict` with `imgsz=768`, `conf=0.25`,
`iou=0.45`, `max_det=300`, `agnostic_nms=False`. Each detection contains `clazz`, `confidence`
and `bbox` with normalized `xMin`, `yMin`, `xMax`, `yMax` values. Labels come from the weights.

```sh
curl -H 'Content-Type: image/jpeg' --data-binary @site.jpg http://127.0.0.1:8000/infer
```

This endpoint has no Firebase guard or Java image-validation pipeline. Restrict its network
exposure; clients normally use [the Java API](../docs/API.md). Keep one worker initially:
additional Uvicorn workers each load a model and consume their own memory.

Java-managed startup is configured through `yolo-process.*`. Use the absolute working directory,
the virtual environment's interpreter and the same health address. Do not start a managed and a
manual instance on the same port.
