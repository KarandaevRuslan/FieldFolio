package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.karandaev.fieldfolio.core.document.TextResourceCodec
import com.karandaev.fieldfolio.core.document.rememberTextPageSize
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.state.LocalViewerSession
import com.karandaev.fieldfolio.core.ui.state.ViewerSession
import com.karandaev.fieldfolio.core.ui.state.rememberViewerSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TextFilePreview(bytes: ByteArray, title: String) {
  val limit by rememberTextPageSize()
  var open by rememberViewerSession("file-reader-open:$title") { mutableStateOf(false) }
  val preview by
    produceState<Result<TextResourceCodec.Page>?>(null, bytes, limit) {
      value =
        withContext(Dispatchers.Default) { runCatching { TextResourceCodec.page(bytes, 0, limit) } }
    }
  preview?.fold(
    onSuccess = {
      SelectionContainer {
        Text(it.text, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
      }
    },
    onFailure = { Text(tr("text_encoding_error")) },
  ) ?: Text(tr("loading"))
  if (preview?.isSuccess == true)
    HintIconButton({ open = true }, tooltip = tr("view")) {
      Icon(Icons.Default.OpenInFull, tr("view"))
    }
  if (open)
    ViewerSession("${LocalViewerSession.current ?: "file"}:file-reader:$title") {
      PagedTextDialog(bytes, title) { open = false }
    }
}
