# Fieldfolio Server

Java 17 / Spring Boot 3.3.2 / Maven HTTP backend. Source entry:
`src/main/java/com/karandaev/roadCrackDetectionServer/App.java`.

## Main features

- `GET /public/health`: public readiness-shaped response (not proof that the database/YOLO is healthy).
- `POST /v1/detect`: multipart `file` upload; image safety checks, normalization and
  HTTP forwarding to the configured YOLO inference service (`/infer` by default).
- `GET /v1/me`: authenticated Firebase principal information.
- `GET/POST/DELETE /v1/profile/avatar`: authenticated avatar management.
- `/v1/**` requires a Firebase-authenticated, email-verified user; rate limiting is
  configured in Spring Security. The server may use PostgreSQL for profile persistence.

## Build

```sh
mvn -B -ntp verify
# Requires local credentials and dependent services to actually run:
mvn spring-boot:run
```

`Server.zip` did not contain a Maven wrapper, tests, an operational `application.yml`,
or the `python-yolo` runtime. Consequently `verify` checks compilation and packaging, **not**
end-to-end inference or authentication. CI intentionally does not launch Spring Boot.

- [Server architecture](docs/ARCHITECTURE.md)
- [Local configuration, services and security](docs/CONFIGURATION.md)
- [Monorepo overview](../docs/README.md)
