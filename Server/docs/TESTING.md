# Server tests and smoke checks

## Build check

From `Server/`, run `mvn -B -ntp verify`. The current POM has no test dependency and the source
tree has no `src/test` suite. Add tests with the implementation when expanding coverage.

## Worker smoke check

Start the worker from `python-yolo/` after installing its environment and placing weights.

```sh
curl http://127.0.0.1:8000/health
curl -H 'Content-Type: image/jpeg' --data-binary @site.jpg http://127.0.0.1:8000/infer
```

The model loads during import. A missing/incompatible `best_models/best.pt` prevents startup.
Check that the response has `detections`, finite confidences and normalized boxes.

## Java integration

Prepare PostgreSQL, Firebase credentials and the configured worker/scanner/TLS resources.
Start the Java API with the selected profile. Use the [API examples](API.md).

| Case | Expected observation |
|---|---|
| Public health | Java response includes status and timestamp |
| Valid authenticated detect | Image metadata and detections returned |
| Missing/invalid token | Protected route rejects access |
| Unverified Firebase email | Protected route rejects authorization |
| Renamed non-image bytes | Content signature is rejected |
| File over the configured limit | Request rejected before inference |
| Dimensions over a side/pixel limit | Image rejected before full processing |
| Worker unavailable or timed out | Controlled failed request, not a successful empty result |
| Repeated charged requests | 429 and Retry-After after configured bucket depletion |
| Avatar create/get/reset | Profile metadata and file behavior stay consistent |

Use synthetic images/accounts for negative tests. Verify both direct worker access restrictions
and proxy behavior. Native tests on Android cover the client contract separately from this live
integration. A Java health response alone does not exercise authentication, database or model execution.
