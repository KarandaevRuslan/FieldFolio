# Fieldfolio Server

The backend combines a Java 17 / Spring Boot 3.3.2 API and a separate FastAPI YOLO worker.
Java handles Firebase authentication, profile/avatar storage, upload validation and rate
limits. The worker runs `best_models/best.pt` and returns detections.

| Process | Entry point | Default example address |
|---|---|---|
| Java API | `src/main/java/com/karandaev/roadCrackDetectionServer/App.java` | `https://localhost:8443` in the stripped template |
| Python worker | `python-yolo/app.py` | `http://127.0.0.1:8000` |

## Build

From `Server/`:

```sh
mvn -B -ntp verify
```

Use an installed Maven; this project has no Maven wrapper. There is currently no `src/test`
suite. Packaging does not initialize the runtime dependencies.

## Run

1. Prepare PostgreSQL and a Firebase Admin service account for the same project as the client.
2. Set up the [Python worker](python-yolo/README.md), including its weights and environment.
3. Configure [application settings](docs/CONFIGURATION.md). Choose external or Java-managed
   worker operation, and configure TLS/ClamAV as required.
4. Start Java with the selected local configuration, then check the [API requests](docs/API.md).

```sh
mvn spring-boot:run
```

This command reads the normal Spring configuration. A local example and explicit invocation
are documented in [CONFIGURATION.md](docs/CONFIGURATION.md).

- [Architecture](docs/ARCHITECTURE.md)
- [Configuration](docs/CONFIGURATION.md)
- [HTTP API](docs/API.md)
- [TLS and local certificates](docs/TLS.md)
- [Tests and smoke checks](docs/TESTING.md)
- [Repository guide](../docs/README.md)
