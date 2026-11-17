# Security and private data

Use GitHub private vulnerability reporting when enabled for the repository. Otherwise contact
the maintainer through the profile linked in README before publishing exploit details or private
reports. No dedicated security email address is asserted here.

Never include Firebase configuration, signing keys, passwords, decrypted report bytes, precise
locations or account tokens in a public issue/log attachment. The source exporter excludes local
credential files; that does not make arbitrary report attachments safe to publish.

The document renderer has no native editor bridge or direct app filesystem/VFS mutation API.
Its read-only capabilities are scoped to one unlocked report/runtime. Hiding a title/item affects
presentation, not access by another Document in the same report. Password locking revokes runtimes;
private draft/session state is memory-owned. WebView isolation does not impose a CPU/memory quota
on notebook JavaScript. See ARCHITECTURE.md for the implemented boundary and limitations.

Use protected release environment secrets for signed CI builds. Never sign PR/fork code with
production credentials or silently fall back to a debug signing identity for a release.
