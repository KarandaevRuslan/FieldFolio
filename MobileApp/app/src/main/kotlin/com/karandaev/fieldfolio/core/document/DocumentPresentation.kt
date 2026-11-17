package com.karandaev.fieldfolio.core.document

import com.karandaev.fieldfolio.core.names.ResourceIdentity

val DocumentNode.identity
  get() = ResourceIdentity(name, displayName, hideTitle, visible)
val DocumentNode.visualTitle
  get() = if (hideTitle) "" else displayLabel

fun ResourceIdentity.applyTo(node: DocumentNode): DocumentNode =
  normalized().let {
    node.copy(
      name = it.name,
      displayName = it.displayName,
      hideTitle = it.hideTitle,
      visible = it.visible,
    )
  }

/** A hidden folder suppresses its complete subtree in the viewer, even on a direct path. */
class DocumentPresentation(val tree: DocumentTree, val files: Map<String, ByteArray>) {
  private val meaningful = mutableMapOf<String, Boolean>()

  fun hasVisual(node: DocumentNode): Boolean =
    meaningful.getOrPut(node.id) {
      if (node.kind == BlockKind.FOLDER)
        node.visualTitle.isNotBlank() || children(node.id).isNotEmpty()
      else hasLeafVisual(node, files)
    }

  fun isVisible(node: DocumentNode): Boolean =
    node.visible && tree.ancestors(node.id).all { it.visible }

  fun canOpenFolder(id: String?): Boolean =
    id == null ||
      tree.byId[id]?.let { it.kind == BlockKind.FOLDER && isVisible(it) && hasVisual(it) } == true

  fun resolveViewerPath(path: String, from: String? = null): String? =
    tree.resolvePath(path, from, ::canOpenFolder)

  fun children(parent: String?): List<DocumentNode> =
    tree.children(parent).filter { isVisible(it) && hasVisual(it) }
}

/** Content inspection is deliberately independent of tree/name/zone validation during editing. */
fun hasLeafVisual(node: DocumentNode, files: Map<String, ByteArray>): Boolean =
  node.visualTitle.isNotBlank() ||
    when (node.kind) {
      BlockKind.TEXT,
      BlockKind.HEADING -> node.body.isNotBlank()
      BlockKind.DOCUMENT -> node.body.isNotBlank()
      BlockKind.TEXT_FILE -> files[node.asset]?.let(TextResourceCodec::hasVisibleText) == true
      BlockKind.IMAGE,
      BlockKind.AUDIO -> files[node.asset]?.isNotEmpty() == true
      BlockKind.MODEL_RESULT -> node.result != null || node.body.isNotBlank()
      BlockKind.LOCATION,
      BlockKind.WEATHER,
      BlockKind.CLOCK -> true
      BlockKind.FOLDER -> false
    }

/**
 * Unfinished form values are previewed without invoking persistence validation on each keystroke.
 */
fun ResourceIdentity.asDraft(node: DocumentNode): DocumentNode =
  node.copy(name = name, displayName = displayName, hideTitle = hideTitle, visible = visible)
