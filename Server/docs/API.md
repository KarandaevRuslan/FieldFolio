# HTTP API

Configure the Java base URL in the Android settings. The examples use the local development
profile on `http://127.0.0.1:8080`. Use your configured HTTPS base URL for a TLS deployment.
Examples show POSIX shell syntax; PowerShell users can call `curl.exe` and adjust variables.

## Java routes

| Method and path | Authentication | Request | Response |
|---|---|---|---|
| `GET /public/health` | Public | None | `status`, ISO timestamp |
| `POST /v1/detect` | Firebase, verified email | Multipart part `file` | `imageWidth`, `imageHeight`, `imageFormat`, `detections` |
| `GET /v1/me` | Firebase, verified email | None | `uid`, `name`, `email`, `emailVerified`, `roles` |
| `GET /v1/profile/avatar` | Firebase, verified email | None | `avatarUrl` or null |
| `POST /v1/profile/avatar` | Firebase, verified email | Multipart part `file` | New `avatarUrl` |
| `DELETE /v1/profile/avatar` | Firebase, verified email | None | Null `avatarUrl` |
| `GET /public/avatars/{uid}/{filename}` | Public | None | Stored avatar bytes |

Protected calls send `Authorization: Bearer <Firebase ID token>`. This is an ID token,
not a Firebase API key or Admin service-account file. Security filters can produce responses
before MVC exception handling; do not assume every authentication error has the same JSON body.

```sh
curl http://127.0.0.1:8080/public/health
curl -H "Authorization: Bearer $FIREBASE_ID_TOKEN" -F "file=@site.jpg" http://127.0.0.1:8080/v1/detect
curl -H "Authorization: Bearer $FIREBASE_ID_TOKEN" http://127.0.0.1:8080/v1/me
```

## Detection representation

Example shape, with illustrative values:

```json
{
  "imageWidth": 640,
  "imageHeight": 480,
  "imageFormat": "PNG",
  "detections": [
    {
      "clazz": "D00",
      "confidence": 0.91,
      "bbox": {"xMin": 0.1, "yMin": 0.2, "xMax": 0.4, "yMax": 0.6}
    }
  ]
}
```

Labels come from the loaded model. Bounding boxes are normalized to 0–1 relative to the
processed image. Java dimensions describe the normalized image. Pixel coordinates are obtained
by multiplying x by width and y by height. An empty detections array is distinct from a failed
request. The worker endpoint receives raw bytes, not the Java multipart request.

## Errors and limits

| Status | Source and behavior |
|---|---|
| 400 / image-specific status | Invalid image/request; MVC image handler uses `invalid_image` |
| 401 / 403 | Authentication or authorization failure; filter and MVC behavior can differ |
| 413 | Multipart or image byte/geometry limits; multipart handler uses `file_too_large` |
| 429 | Rate-limit filter; `rate_limited` and `Retry-After` |
| 502 | Worker call, timeout or decoding failure; `yolo_unavailable` |
| 500 | File I/O or unexpected error; `io_error` or `internal_error` in MVC handler |

MVC errors have `error` and `message` fields. The rate-limit filter adds `X-RateLimit-Rule`,
`X-RateLimit-Key` (`uid` or `ip`) and `X-RateLimit-Remaining`. Bucket values are tokens, not
necessarily request counts: the stripped example charges 5 tokens per detect request.
Authenticated requests use UID buckets; anonymous requests use IP buckets. The code does
not enforce both keys simultaneously for the same request.

`/public/health` is reachability information, not a dependency readiness test. Test the
worker and a protected image request separately using [TESTING.md](TESTING.md).
