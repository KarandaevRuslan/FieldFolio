package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.state.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.ViewerImageWindows
import kotlinx.coroutines.*

@Composable
fun TextBlockContent(node: DocumentNode, files: Map<String, ByteArray>) {
  node.effectiveHeadingLevel?.let { level ->
    if (!node.hideTitle)
      Text(
        node.visualTitle,
        style =
          when (level) {
            1 -> MaterialTheme.typography.headlineLarge
            2 -> MaterialTheme.typography.headlineMedium
            3 -> MaterialTheme.typography.headlineSmall
            else -> MaterialTheme.typography.titleLarge
          },
      )
  }
  val limit by rememberTextPageSize()
  val bytes = files[node.asset]
  var page by
    rememberViewerSession("text-preview", node.body, bytes, limit) {
      mutableStateOf<Result<String>?>(
        if (node.kind != BlockKind.TEXT_FILE) Result.success(textPage(node.body, 0, limit))
        else null
      )
    }
  LaunchedEffect(node.body, bytes, limit) {
    if (page == null)
      page =
        withContext(Dispatchers.Default) {
          runCatching {
            if (node.kind == BlockKind.TEXT_FILE)
              TextResourceCodec.page(requireNotNull(bytes), 0, limit).text
            else textPage(node.body, 0, limit)
          }
        }
  }
  var open by rememberViewerSession("text-block-reader", node.id) { mutableStateOf(false) }
  val text = page?.getOrNull()
  if (text != null && text.isNotBlank()) {
    SelectionContainer {
      Text(
        text,
        maxLines = 4,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = Modifier.testTag("collapsed-document-text").clickable { open = true },
      )
    }
    HintIconButton({ open = true }, tooltip = tr("view")) {
      Icon(Icons.Default.OpenInFull, tr("view"))
    }
  } else if (page?.isFailure == true) Text(tr("text_encoding_error"))
  if (open) {
    val host = LocalViewerImages.current
    val key = "${LocalViewerSession.current ?: node.id}:text-block-reader"
    if (host != null)
      SideEffect { host.showText(ViewerImageWindows.Text(key, node, files) { open = false }) }
    else ViewerSession(key) { TextReaderWindow(node, files) { open = false } }
  }
}

@Composable
fun TextReaderWindow(node: DocumentNode, files: Map<String, ByteArray>, dismiss: () -> Unit) {
  if (node.textOpenMode == TextOpenMode.ACODE) AcodeReadOnlyDialog(node, files, dismiss)
  else {
    val bytes = files[node.asset]
    var data by
      rememberViewerSession("text-reader-bytes", node.body, bytes) {
        mutableStateOf<ByteArray?>(bytes)
      }
    LaunchedEffect(bytes, node.body) {
      if (data == null) data = withContext(Dispatchers.Default) { node.body.toByteArray() }
    }
    data?.let { PagedTextDialog(it, node.displayLabel, dismiss) }
  }
}
