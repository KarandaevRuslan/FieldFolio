# Repository security policy

Please use GitHub private vulnerability reporting if available. Otherwise contact the
maintainer through the profile linked in `MobileApp/README.md` before sharing exploit details.
No unverified security email address is published.

Never disclose signing keys, Firebase Admin service-account JSON, `google-services.json`,
PostgreSQL credentials, TLS private keys, private reports, location histories or inference
input data in issues or CI logs. Keep release secrets in a protected `release` environment.

The server handles Firebase-authenticated API calls, uploaded-image validation, optional
ClamAV scans, profile persistence, and external inference. This repository's CI compiles
those components but **does not** assert production deployment safety or integration coverage.
See [server configuration](Server/docs/CONFIGURATION.md) and
[mobile security](MobileApp/SECURITY.md).
