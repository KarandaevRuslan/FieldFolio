# Server configuration and local development

**Do not commit `application.yml`, service-account JSON, TLS/private keys or database passwords.**
Use `src/main/resources/application_stripped.yml` as a *template* and copy it to
`src/main/resources/application.yml` on your own machine. Replace all `<...>` placeholders
and machine-specific paths. The stripped file is not a production-ready config.

## Required runtime dependencies

| Resource | Config key / expected use |
|---|---|
| PostgreSQL | `spring.datasource.*`; JPA profile storage |
| TLS keystore | `server.ssl.*`, default template uses a PKCS12 classpath keystore |
| Firebase Admin credentials | `firebase.service-account` |
| YOLO HTTP endpoint | `yolo.base-url`, `yolo.detect-path` and timeout |
| Python lifecycle (optional) | `yolo-process.enabled`, workdir, executable, health URL |
| ClamAV (optional per local policy) | `image-security.antivirus.*`, daemon or external service |
| Avatar storage | `avatar-storage.directory`, optional public base URL |

The sanitized template enables SSL, managed Python and ClamAV; each requires actual local
services, executables, paths and keys. You may disable managed Python or ClamAV in a *development*
configuration, but do not weaken production file validation without a security review.

```sh
cd Server
mvn -B -ntp verify
mvn spring-boot:run
```

Windows `start-server.bat` launches a new Windows Terminal tab running `mvn spring-boot:run`.
It is a local convenience command, not a service manager or production deployment script.

`mvn verify` in GitHub Actions does not initialize PostgreSQL/Firebase/ClamAV/YOLO and has no
server-side test classes in the uploaded archive. Add actual integration tests before using it
as a production-release gate.
