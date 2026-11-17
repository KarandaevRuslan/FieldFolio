package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll
import com.karandaev.fieldfolio.core.ui.state.rememberViewerSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.tr
import kotlinx.coroutines.*

/** One reading surface, page count and jump interaction for inline text and encoded resources. */
@Composable
fun PagedTextDialog(bytes: ByteArray, title: String, dismiss: () -> Unit) {
  val limit by rememberTextPageSize()
  var index by rememberViewerSession("reader-page:$title") { mutableIntStateOf(0) }
  var jump by rememberViewerSession("reader-jump:$title") { mutableStateOf(false) }
  val countState =
    produceState<Int?>(null, bytes, limit) {
      value =
        withContext(Dispatchers.Default) {
          runCatching { TextResourceCodec.pageCount(bytes, limit) }.getOrNull()
        }
    }
  val count = countState.value
  val current = if (count == null) index else index.coerceIn(0, (count - 1).coerceAtLeast(0))
  val loaded =
    produceState<Result<TextResourceCodec.Page>?>(null, bytes, current, limit) {
      value = null
      value =
        withContext(Dispatchers.Default) {
          runCatching { TextResourceCodec.page(bytes, current, limit) }
        }
    }
  val page = loaded.value
  SafeAppDialog(dismiss) {
    Surface(Modifier.fillMaxSize()) {
      Column {
        Row(
          Modifier.fillMaxWidth(),
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
          HintIconButton(dismiss, tooltip = tr("close")) { Icon(Icons.Default.Close, tr("close")) }
          Text(title, Modifier.weight(1f), maxLines = 1)
        }
        val scroll =
          rememberViewerSession("page-scroll:$title") { androidx.compose.foundation.ScrollState(0) }
        var previousPage by
          rememberViewerSession("page-scroll-key:$title") { mutableStateOf<Pair<Int, Int>?>(null) }
        LaunchedEffect(current, limit) {
          val next = current to limit
          if (previousPage != next) {
            scroll.scrollTo(0)
            previousPage = next
          }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(16.dp)) {
          page?.fold(
            onSuccess = {
              SelectionContainer { Text(it.text, Modifier.testTag("document-text-page")) }
            },
            onFailure = { Text(tr("text_encoding_error")) },
          ) ?: Text(tr("loading"))
        }
        Row(
          Modifier.fillMaxWidth().padding(8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
          HintIconButton(
            { index = current - 1 },
            enabled = current > 0 && page != null,
            tooltip = tr("previous_page"),
          ) {
            Icon(Icons.Default.NavigateBefore, tr("previous_page"))
          }
          TextButton(
            { jump = true },
            enabled = count != null,
            modifier = Modifier.testTag("document-text-page-count"),
          ) {
            Text("${current+1} / ${count?:"…"}")
          }
          HintIconButton(
            { index = current + 1 },
            enabled = page?.getOrNull()?.hasNext == true,
            tooltip = tr("next_page"),
          ) {
            Icon(Icons.Default.NavigateNext, tr("next_page"))
          }
        }
      }
    }
  }
  if (jump && count != null)
    PageJumpDialog(current + 1, count, { jump = false }) {
      index = it - 1
      jump = false
    }
}
