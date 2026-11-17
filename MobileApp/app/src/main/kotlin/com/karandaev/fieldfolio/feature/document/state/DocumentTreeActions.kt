package com.karandaev.fieldfolio.feature.document.state

import androidx.compose.runtime.*
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import kotlinx.coroutines.*

internal fun DocumentViewModel.performNavigate(target: String?) {
  if (target != null) require(tree.node(target).kind == BlockKind.FOLDER)
  if (target == folder) return
  history = history.take(historyIndex + 1) + listOf(target)
  historyIndex = history.lastIndex
  folder = target
}

internal fun DocumentViewModel.performGoBack() {
  if (canGoBack) {
    historyIndex--
    folder = history[historyIndex]?.takeIf { it in tree.byId }
  }
}

internal fun DocumentViewModel.performGoForward() {
  if (canGoForward) {
    historyIndex++
    folder = history[historyIndex]?.takeIf { it in tree.byId }
  }
}

internal fun DocumentViewModel.performMoveToIndex(id: String, index: Int) = action {
  val node = tree.node(id)
  document = tree.transfer(listOf(id), node.parentId, index)
  dirty = true
}

internal fun DocumentViewModel.performUpdate(node: DocumentNode) = action {
  document = tree.update(node)
  dirty = true
}

internal fun DocumentViewModel.performAdd(kind: BlockKind, name: String): DocumentNode =
  com.karandaev.fieldfolio.core.names.NameResolutionEvents.capture {
    val node =
      DocumentNode(
        name = name,
        kind = kind,
        body =
          if (kind == BlockKind.DOCUMENT)
            com.karandaev.fieldfolio.core.document.runtime.DefaultNotebookDocument
          else "",
        syntaxExtension = if (kind == BlockKind.DOCUMENT) "html" else null,
        zoneId = if (kind == BlockKind.CLOCK) java.time.ZoneId.systemDefault().id else null,
        deviceLocation = kind in setOf(BlockKind.LOCATION, BlockKind.WEATHER, BlockKind.CLOCK),
      )
    document = tree.insert(node, folder)
    dirty = true
    document.nodes.first { it.id == node.id }
  }

internal fun DocumentViewModel.performRemove(id: String) = action {
  document = tree.delete(listOf(id))
  dirty = true
}

internal fun DocumentViewModel.performReorder(id: String, delta: Int) = action {
  val n = tree.node(id)
  val siblings = tree.children(n.parentId)
  val i = siblings.indexOfFirst { it.id == id }
  val target = (i + delta).coerceIn(0, siblings.lastIndex)
  if (i != target) {
    document = tree.transfer(listOf(id), n.parentId, target)
    dirty = true
  }
}

internal fun DocumentViewModel.performTransfer(ids: List<String>, target: String?, copy: Boolean) =
  action {
    document = tree.transfer(ids, target, copy = copy)
    dirty = true
  }

internal fun DocumentViewModel.performPaste() {
  clipboard?.let { (ids, copy) ->
    transfer(ids, folder, copy)
    if (!copy && error == null) clipboard = null
  }
}
