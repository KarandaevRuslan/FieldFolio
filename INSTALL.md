# Installation and local setup

Choose the component you need. Android local reporting does not require the Java API or
Python training environment.

| Component | Requirements | Working directory | Main command |
|---|---|---|---|
| Android debug | JDK 17, SDK 36, build-tools 35.0.0 | `MobileApp/` | `./gradlew :app:assembleDebug` |
| Web development | Node from `.node-version`, npm lockfile | `MobileApp/acode-web/` | `npm ci`, then `npm test` and `npm run build` |
| Java API | JDK 17, Maven; PostgreSQL and Firebase for runtime | `Server/` | `mvn -B -ntp verify` |
| Inference worker | Its own Python environment and `best.pt` | `Server/python-yolo/` | `python -m uvicorn app:app --host 127.0.0.1 --port 8000` |
| Dataset converter | Python 3.9+; Pillow for missing XML dimensions | `DetectionTrainProj/tools/` | `python -m voc_yolo_builder --help` |
| Training | Separate environment matching its requirements and GPU setup | `DetectionTrainProj/` | See [training](DetectionTrainProj/README.md) |

## Android

Open `MobileApp/` in Android Studio. Set the SDK location through the IDE or a local
`MobileApp/local.properties`. The Gradle wrapper downloads its pinned distribution on first
use; dependencies also need to be available locally or downloadable.

```sh
cd MobileApp
./gradlew :app:assembleDebug
```

On Windows use `gradlew.bat`. For real sign-in, add `app/google-services.json`. For a signed
release, configure `config/keystore.properties` or the supported environment variables.
Follow [Building](MobileApp/docs/BUILDING.md) and [Releasing](MobileApp/docs/RELEASING.md).

## Server and worker

Set up the worker first using [its guide](Server/python-yolo/README.md). Configure the Java
server using [CONFIGURATION.md](Server/docs/CONFIGURATION.md); an executable local example
is provided as [application-local.example.yml](Server/config/application-local.example.yml).
The example expects environment-provided database credentials and a Firebase service-account
file. Keep real configurations outside version control.

For a phone, `localhost` addresses the phone itself. Use a backend host reachable from the
device and a certificate valid for that host. See [TLS](Server/docs/TLS.md).

## Check the checkout

From the repository root:

```sh
python3 scripts/check_documentation.py
python3 scripts/check_monorepo.py
python3 -m unittest discover -s tests -v
```

[Build and test](docs/BUILD_AND_TEST.md) lists component-specific checks and their prerequisites.
