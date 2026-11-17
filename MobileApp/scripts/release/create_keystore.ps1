# Run from MobileApp/: .\scripts\release\create_keystore.ps1
# Uses signing credentials, owner (certificate subject DN), and cn (Common Name)
# already saved in config/keystore.properties.
# Changing these values NEVER alters an existing certificate.
# Refuses to overwrite an existing keystore; preserves application signing identity.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$propertiesFile = Join-Path $root 'config/keystore.properties'
$keyFile = Join-Path $root 'config/release.keystore'

if (-not (Test-Path -LiteralPath $propertiesFile -PathType Leaf)) {
    throw 'Missing MobileApp/config/keystore.properties'
}
$lines = [System.IO.File]::ReadAllLines($propertiesFile)
$values = @{}
foreach ($line in $lines) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#') -or $trimmed.StartsWith('!')) { continue }
    $separator = $line.IndexOf('=')
    if ($separator -lt 1) { continue }
    $values[$line.Substring(0, $separator).Trim()] = $line.Substring($separator + 1).Trim()
}
foreach ($name in @('storeFile', 'storePassword', 'keyAlias', 'keyPassword')) {
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name]) -or
        $values[$name] -eq 'REPLACE_LOCALLY') {
        throw "Set $name in config/keystore.properties before running this script."
    }
}
if ($values['storePassword'].Length -lt 6 -or $values['keyPassword'].Length -lt 6) {
    throw 'Keytool requires passwords of at least 6 characters.'
}
# Owner in keytool output is the full X.509 Subject DN, not the keystore alias.
# CN is part of that DN. Require both properties to agree before creating a NEW key.
foreach ($name in @('owner', 'cn')) {
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        throw "Set $name in config/keystore.properties before creating a new certificate."
    }
}
$ownerDn = $values['owner'].Trim()
$commonName = $values['cn'].Trim()
if ($ownerDn -notmatch '(?:^|,\s*)CN\s*=\s*([^,]+)') {
    throw 'owner must be an X.509 subject DN, for example: owner=CN=Ruslan Karandaev'
}
if ($Matches[1].Trim() -cne $commonName) {
    throw 'owner CN and cn disagree. Use owner=CN=Ruslan Karandaev and cn=Ruslan Karandaev.'
}

$oldReference = $values['storeFile'] -replace '\\', '/'
$oldKey = if ([System.IO.Path]::IsPathRooted($oldReference)) { $oldReference } else { Join-Path $root $oldReference }
if ((Test-Path -LiteralPath $oldKey -PathType Leaf) -and
    ([System.IO.Path]::GetFullPath($oldKey) -ne [System.IO.Path]::GetFullPath($keyFile))) {
    throw 'An existing release keystore was found at storeFile. Keep using that key for app updates; do not create a different one.'
}
if (Test-Path -LiteralPath $keyFile) {
    throw 'config/release.keystore already exists. Refusing to overwrite the signing key.'
}

Get-Command keytool -ErrorAction Stop | Out-Null
$env:FIELDFOLIO_CREATE_STORE_PASS = $values['storePassword']
$env:FIELDFOLIO_CREATE_KEY_PASS = $values['keyPassword']
try {
    # JKS supports different storePassword and keyPassword; PKCS12 may not.
    & keytool -genkeypair -keystore $keyFile -storetype JKS `
        -alias $values['keyAlias'] -keyalg RSA -keysize 3072 `
        -validity 10000 -dname $ownerDn `
        -storepass:env FIELDFOLIO_CREATE_STORE_PASS `
        -keypass:env FIELDFOLIO_CREATE_KEY_PASS -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed; keystore.properties was not changed.' }
} finally {
    Remove-Item Env:FIELDFOLIO_CREATE_STORE_PASS -ErrorAction SilentlyContinue
    Remove-Item Env:FIELDFOLIO_CREATE_KEY_PASS -ErrorAction SilentlyContinue
}

$updated = @($lines | ForEach-Object {
    if ($_ -match '^\s*storeFile\s*=') { 'storeFile=config/release.keystore' } else { $_ }
})
[System.IO.File]::WriteAllLines($propertiesFile, [string[]]$updated, (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'Created: MobileApp/config/release.keystore'
Write-Host 'Updated: MobileApp/config/keystore.properties (storeFile only)'
Write-Warning 'Back up this keystore securely. Losing it can prevent updates to existing installations.'

# Note: to inspect the subject of an EXISTING key:
# keytool -list -v -keystore .\config\release.keystore
