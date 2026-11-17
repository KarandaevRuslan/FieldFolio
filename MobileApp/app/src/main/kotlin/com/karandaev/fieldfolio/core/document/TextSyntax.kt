package com.karandaev.fieldfolio.core.document

/** Resolves syntax from an explicit hint first, then the logical name or stored resource. */
internal fun resourceSyntaxName(name: String, asset: String?): String {
  val supported =
    setOf(
      "html",
      "htm",
      "xhtml",
      "css",
      "scss",
      "less",
      "js",
      "mjs",
      "cjs",
      "jsx",
      "ts",
      "tsx",
      "json",
      "jsonc",
      "md",
      "markdown",
      "xml",
      "svg",
      "xsd",
      "xsl",
      "xslt",
    )
  return if (name.substringAfterLast('.', "").lowercase() in supported) name else asset ?: name
}

/** A syntax hint is independent of the logical resource name and its optional alias. */
fun textSyntaxName(node: DocumentNode): String =
  node.syntaxExtension?.let { "document.$it" } ?: resourceSyntaxName(node.name, node.asset)
