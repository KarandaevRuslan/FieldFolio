# Server configuration

Run commands in `Server/`. Spring does not automatically load the filename
`application_stripped.yml`; it is a reference template. Use a local `application.yml` or
an explicit external configuration. Example:

```sh
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=file:./config/application-local.example.yml"
```

[The example](../config/application-local.example.yml) uses environment placeholders for
secrets. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `FIREBASE_SERVICE_ACCOUNT` first.
The latter is a Spring resource URI such as `file:/absolute/path/firebase-admin.json`.
Use your shell's environment support; do not put real secrets into the example file.

## Runtime resources

| Property | Meaning and required setup |
|---|---|
| `spring.datasource.url/username/password` | PostgreSQL connection for JPA profiles |
| `spring.jpa.hibernate.ddl-auto` | Template uses `update`; choose the deployment schema/migration policy explicitly |
| `server.port`, `server.ssl.*` | Listener and TLS configuration |
| `firebase.service-account` | Readable Firebase Admin credentials resource |
| `yolo.base-url`, `detect-path`, `timeout-ms` | Worker URL, raw-image endpoint and request timeout |
| `yolo-process.enabled` | Start worker from Java when true |
| `yolo-process.workdir/python-exe/args/health-url` | Working directory, Python command and startup probe |
| `image-security.antivirus.enabled` | Select actual scanner or disabled scanner path |
| `image-security.antivirus.start-clamd` | Start clamd using configured executable/config |
| `avatar-storage.directory/public-base-url` | Writable avatar root and externally visible URL prefix |
| `rate-limit.*` | Rule costs, UID/IP capacities and refill periods |

Relative paths are resolved against the process working directory. The worker's model path
is relative to its own working directory. For Java-managed inference, set `workdir` to the
absolute `Server/python-yolo/` directory and `python-exe` to that environment's interpreter.
The local example uses an already running worker and disables Java process management.

## Image limits

Defaults are 10 MiB, width/height at most 4096, and at most 20 megapixels. Both side and
pixel-count conditions apply. Allowed formats are JPEG, PNG and WEBP; decoder support must
also be present. The normal output format is PNG. Keep PNG normalization for avatar handling,
which writes `.png` filenames. Multipart request-size limits include container overhead, so
an upload close to the byte limit can be rejected before the image service sees it.

## Deployment settings

The stripped template enables SSL and managed Python and requests ClamAV. Its `start-cla-md`
key is misspelled: the Java property is `startClamd`, whose kebab-case key is `start-clamd`.
Use the corrected spelling in your local configuration to enable process startup. Supply those resources
or select a local configuration with external services. The development example uses plain
HTTP on localhost and a disabled scanner; it is a local development profile, not a public host
configuration. Configure [TLS](TLS.md) and an actual scanner for the intended deployment.

Set `avatar-storage.public-base-url` when a proxy changes the public host/scheme. For anonymous
rate limiting, the code reads the first `X-Forwarded-For` value. A trusted reverse proxy must
replace that header and prevent direct bypass. See [API behavior](API.md).
