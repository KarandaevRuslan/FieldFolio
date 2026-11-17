@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.*
import com.karandaev.fieldfolio.acode.settings.EditorPreferences
import com.karandaev.fieldfolio.acode.web.NotebookEditorWebView
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.document.textSyntaxName
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog
import com.karandaev.fieldfolio.core.ui.state.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.tr
import kotlinx.coroutines.*

@Composable
fun AcodeReadOnlyDialog(node: DocumentNode, files: Map<String, ByteArray>, dismiss: () -> Unit) {
  val source by
    produceState<String?>(null, node.id, files[node.asset], node.body) {
      value =
        withContext(Dispatchers.Default) {
          if (node.kind == BlockKind.TEXT_FILE)
            files[node.asset]?.let { TextResourceCodec.editable(it)?.text }
          else node.body
        }
    }
  var anchor by rememberViewerSession("readonly-anchor") { mutableIntStateOf(0) }
  var head by rememberViewerSession("readonly-head") { mutableIntStateOf(0) }
  var scroll by rememberViewerSession("readonly-scroll") { mutableIntStateOf(0) }
  var search by rememberViewerSession("readonly-search") { mutableStateOf("") }
  val context = LocalContext.current
  val prefs = remember { EditorPreferences(context) }
  var web by remember { mutableStateOf<android.webkit.WebView?>(null) }
  SafeAppDialog(dismiss) {
    Scaffold(
      Modifier.testTag("acode-readonly-window"),
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(node.displayLabel, maxLines = 1)
              Text(
                "." + textSyntaxName(node).substringAfterLast('.', "txt"),
                style = MaterialTheme.typography.labelSmall,
              )
            }
          },
          windowInsets = WindowInsets(0, 0, 0, 0),
          navigationIcon = {
            HintIconButton(dismiss, tooltip = tr("close")) {
              Icon(Icons.Default.Close, tr("close"))
            }
          },
          actions = {
            HintIconButton(
              { web?.evaluateJavascript("window.EditorApi?.search()", null) },
              tooltip =
                androidx.compose.ui.res.stringResource(
                  com.karandaev.fieldfolio.R.string.v17_search_text
                ),
            ) {
              Icon(
                Icons.Default.Search,
                androidx.compose.ui.res.stringResource(
                  com.karandaev.fieldfolio.R.string.v17_search_text
                ),
              )
            }
          },
        )
      },
    ) { pad ->
      val text = source
      if (text == null)
        Box(Modifier.padding(pad)) {
          Text(tr("text_readonly"))
          files[node.asset]?.let { TextFilePreview(it, node.displayLabel) }
        }
      else
        NotebookEditorWebView(
          id = "reader-${node.id}",
          initialSource = text,
          dark = MaterialTheme.colorScheme.background.luminance() < .5f,
          language = LocalConfiguration.current.locales[0].language,
          editorPreferences = prefs,
          onChanged = {},
          selectionAnchor = anchor,
          selectionHead = head,
          editorScrollTop = scroll,
          onCursorSelection = { a, h, s ->
            anchor = a
            head = h
            scroll = s
          },
          searchStateJson = search,
          onSearchStateChange = { search = it },
          fullscreen = false,
          onFullscreenChange = {},
          modifier = Modifier.fillMaxSize().padding(pad),
          fileName = textSyntaxName(node),
          resourceFile = true,
          readOnly = true,
          onWebView = { web = it },
        )
    }
  }
}
