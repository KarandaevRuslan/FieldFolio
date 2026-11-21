# TLS and local certificates

The Java template uses HTTPS on port 8443. The certificate must include the DNS name/IP that
the client uses. The Android client must trust its issuing CA; accepting a server in a desktop
browser does not configure app trust automatically.

## Generate a local certificate

From `Server/`, install `mkcert` and OpenSSL, then inspect available flags:

```sh
python tools/make_keystore.py --help
python tools/make_keystore.py --outdir ssl --keystore keystore.p12 --name springboot --extra localhost,127.0.0.1
```

Add the reachable device-facing host/IP to `--extra`. The tool detects a local IPv4 address
and normally runs `mkcert -install`; use `--noinstall` only if the CA setup is already handled.
The tool writes private material into its output directory. Keep those files local.

Use a matching PKCS12 path, password and alias in `server.ssl.*`. A file resource such as
`file:/absolute/path/keystore.p12` avoids copying the keystore into source resources.
The script's default alias is `spring`; the command above explicitly selects `springboot`
to match the stripped configuration.

## Android CA resource

`tools/copy_mkcert_ca.py` copies the local mkcert public root CA to
`Server/tools/rootCA.pem` (next to the script). It does not update Android automatically. To
configure a development app, inspect its raw CA resource and network-security configuration,
then configure a development-only CA resource. The current main
`MobileApp/app/src/main/res/xml/network_security_config.xml` trusts system CAs only; it does
not reference a bundled raw certificate or automatically trust user-installed CAs.

For a development build, copy the public certificate to
`MobileApp/app/src/debug/res/raw/dev_root_ca.pem` and provide a debug resource override at
`MobileApp/app/src/debug/res/xml/network_security_config.xml`. Preserve the needed base policy
and add trust scoped to the actual backend host, for example:

```xml
<network-security-config>
  <base-config cleartextTrafficPermitted="true">
    <trust-anchors><certificates src="system" /></trust-anchors>
  </base-config>
  <domain-config>
    <domain includeSubdomains="false">backend.example.test</domain>
    <trust-anchors>
      <certificates src="system" />
      <certificates src="@raw/dev_root_ca" />
    </trust-anchors>
  </domain-config>
</network-security-config>
```

Replace the host with the certificate's reachable SAN name. Never copy the CA private key
into the app. Keep the local CA resource/override out of production release configuration.

## Remote deployment

Use a certificate and host configuration appropriate to the public endpoint. When terminating
TLS at a reverse proxy, configure forwarded public URLs for avatars and enforce the trusted
proxy boundary. The independent Python worker normally remains on an internal HTTP interface.
Do not solve a certificate error by disabling hostname verification in the app.
