# Build and test from the monorepo

From the Git repository root (it may be named `Team Project/` locally; PowerShell
uses `\` and `gradlew.bat`):

```sh
# Android and bundled WebView frontend (JDK 17, Android SDK 36 / build-tools 35)
cd MobileApp
./gradlew :app:testDebugUnitTest :app:assembleDebug
npm --prefix acode-web ci --no-audit --no-fund
npm --prefix acode-web test
npm --prefix acode-web run build
python3 scripts/check_web_assets.py
cd ..

# Server (JDK 17, Maven). This compiles/packages without starting the external services.
mvn -f Server/pom.xml -B -ntp verify

# Repository policy checks (Python 3.10+)
python3 scripts/check_monorepo.py
python3 -m unittest discover -s tests -v
```

The server's runtime needs a **local, secret-bearing** `application.yml`, a database,
Firebase Admin credentials, optional managed ClamAV and a separately deployed/configured
YOLO service. See [`Server/docs/CONFIGURATION.md`](../Server/docs/CONFIGURATION.md). Do not
commit that runtime configuration. `mvn verify` currently does **not** run Server unit tests,
because the supplied Server archive contains no `src/test` sources or test dependencies.

`DetectionTrainProj/` was not uploaded. CI only syntax-checks tracked `.py` files in that
subtree; training, downloads, GPU execution and model quality are not validated by that check.
