package com.karandaev.fieldfolio.core.document

fun DocumentTree.path(id: String?): String =
  "/" +
    (id?.let { ancestors(it) + node(it) }.orEmpty()).joinToString("/") {
      android.net.Uri.encode(it.name)
    }

/**
 * Resolves canonical logical components, never Android filesystem paths. Intermediate nodes must be
 * folders. Viewers keep [foldersOnly] enabled and supply visibility checks; editors may opt into a
 * leaf destination without permitting traversal through that leaf.
 */
fun DocumentTree.resolvePath(
  path: String,
  from: String? = null,
  allowed: (String?) -> Boolean = { true },
  foldersOnly: Boolean = true,
): String? {
  var current: String? = if (path.startsWith('/')) null else from
  require(allowed(current)) { "Folder is not visible in the viewer" }
  val parts = path.split('/').filter { it.isNotEmpty() }
  for ((index, part) in parts.withIndex()) {
    require(current == null || node(current).kind == BlockKind.FOLDER) {
      "Cannot traverse through an element"
    }
    when (part) {
      "." -> Unit
      ".." -> current = current?.let { node(it).parentId }
      else -> {
        val name = android.net.Uri.decode(part)
        val matches = children(current).filter { it.name == name }
        require(matches.size == 1) { "Path does not exist or is ambiguous" }
        current = matches.single().id
      }
    }
    require(
      !foldersOnly && index == parts.lastIndex ||
        current == null ||
        node(current).kind == BlockKind.FOLDER
    ) {
      "Destination is not a folder"
    }
    require(allowed(current)) { "Folder is not visible in the viewer" }
  }
  return current
}
