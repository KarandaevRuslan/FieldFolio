# Security

Report vulnerabilities privately through GitHub private vulnerability reporting when enabled,
or contact the maintainer through [the project profile](https://github.com/KarandaevRuslan/).
Send a minimal reproducer, affected component/version and expected impact.

Keep signing/TLS keys, Firebase Admin credentials, database passwords, tokens, private reports,
location histories and inference inputs out of public issues and logs. Use redacted test data.

The Android Document renderer has read-only, report-scoped resource capabilities. Hidden titles
and visibility flags are presentation settings, not access controls. Locking revokes the active
runtime. See [mobile security](MobileApp/SECURITY.md).

The Java API authenticates protected routes and validates uploaded images. The Python worker
has a raw image endpoint without Firebase authentication; deploy it behind the Java API or an
appropriately restricted service boundary. `X-Forwarded-For` is read for anonymous rate-limit
keys, so a public proxy must remove untrusted client-supplied values before forwarding.
See [server configuration](Server/docs/CONFIGURATION.md) and [API](Server/docs/API.md).

The `release` environment holds production signing inputs. Do not expose those inputs to
untrusted pull-request code. Preserve third-party and model license terms when redistributing.
