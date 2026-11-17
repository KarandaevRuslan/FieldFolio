# Fieldfolio release signing — config/ paths

Copy the **contents** of this archive to the **Team Project repository root**.
It changes existing files but does not include your private keystore.properties or keystore.

From MobileApp/ (Windows PowerShell):

```powershell
.\scripts\release\create_keystore.ps1
.\gradlew.bat :app:assembleRelease
python .\scripts\release\make_signing_bundle.py --properties .\config\keystore.properties --output "$env:TEMP\fieldfolio-signing.b64"
```

The create script uses the existing `storePassword`, `keyAlias`, and `keyPassword`
from `config/keystore.properties` and writes `config/release.keystore`.
It updates `storeFile` to `config/release.keystore`. **Do not run this if you previously
published a release signed with a different keystore.** Find the original key instead.

The GitHub Actions secret names remain unchanged. Re-upload the signing bundle if
it previously contained a different key.
