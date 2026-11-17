@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.document.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.document.runtime.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.components.fields.PresentationActions
import com.karandaev.fieldfolio.core.ui.layout.ElementWorkspace
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.viewer.NotebookDocumentView
import com.karandaev.fieldfolio.feature.document.viewer.NotebookWindowHost
import com.karandaev.fieldfolio.feature.models.readPickedBytes
import kotlinx.coroutines.*

@Composable
fun NotebookDocumentProperties(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  dismiss: () -> Unit,
  save: (DocumentNode, String?) -> Unit,
) {
  var identity by rememberNodeIdentity(node)
  var body by rememberSession(node.id) { mutableStateOf(node.body) }
  var code by rememberSession(node.id) { mutableStateOf(false) }
  var preview by rememberSession(node.id) { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val draft = identity.asDraft(node).copy(body = body, syntaxExtension = "html")
  val close = rememberUnsavedClose(identity != node.identity || body != node.body, dismiss)
  val parent = LocalDocumentAccess.current
  val windows = rememberSession(node.id) { NotebookWindows() }
  val access =
    rememberSession(parent, draft, files) {
      documentDraftAccess(
        parent,
        if (identity.valid) draft else node.copy(body = body, syntaxExtension = "html"),
        files,
      )
    }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val app = rememberAppServices()
  val imported =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      app.reportLibrary.externalPicker = false
      if (uri != null)
        scope.launch {
          try {
            val source =
              withContext(Dispatchers.IO) {
                val bytes = readPickedBytes(context, uri, TextResourceCodec.EDIT_BYTES)
                try {
                  requireNotNull(TextResourceCodec.editable(bytes)).text.also {
                    require(it.length <= 1_000_000)
                  }
                } finally {
                  bytes.fill(0)
                }
              }
            body = source
          } catch (cancel: CancellationException) {
            throw cancel
          } catch (_: Exception) {
            error = context.getString(R.string.v20_document_import_failed)
          }
        }
    }
  CompositionLocalProvider(
    LocalDocumentAccess provides access,
    LocalNotebookWindows provides windows,
  ) {
    SafeAppDialog(close) {
      Surface(Modifier.fillMaxSize().testTag("document-properties")) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
          val actionWidth = (maxWidth - 228.dp).coerceAtLeast(48.dp)
          Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
              TopAppBar(
                title = { Text(stringResource(R.string.v20_document), maxLines = 1) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                  HintIconButton(close, tooltip = tr("close")) {
                    Icon(Icons.Default.Close, tr("close"))
                  }
                },
                actions = {
                  Row(Modifier.widthIn(max = actionWidth).horizontalScroll(rememberScrollState())) {
                    PresentationActions(identity, { identity = it })
                    HintIconButton(
                      {
                        app.reportLibrary.externalPicker = true
                        imported.launch(
                          arrayOf("text/html", "text/plain", "application/octet-stream")
                        )
                      },
                      tooltip = stringResource(R.string.v20_import_document),
                    ) {
                      Icon(Icons.Default.FileOpen, stringResource(R.string.v20_import_document))
                    }
                    HintIconButton({ code = true }, tooltip = tr("edit_text")) {
                      Icon(Icons.Default.Code, tr("edit_text"))
                    }
                    HintIconButton({ preview = true }, tooltip = tr("view")) {
                      Icon(Icons.Default.Preview, tr("view"))
                    }
                  }
                  HintIconButton(
                    {
                      save(identity.applyTo(node).copy(body = body, syntaxExtension = "html"), null)
                    },
                    enabled = identity.valid && body.length <= 1_000_000,
                    tooltip = tr("save"),
                  ) {
                    Icon(Icons.Default.Save, tr("save"))
                  }
                },
              )
            },
          ) { padding ->
            ElementWorkspace(
              Modifier.padding(padding),
              fields = {
                NodeIdentityFields(identity, { identity = it }, BlockKind.DOCUMENT)
                Text(
                  stringResource(R.string.v20_document_help),
                  style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedCard(Modifier.fillMaxWidth()) {
                  Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Row(
                      Modifier.fillMaxWidth(),
                      verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                      Text(
                        stringResource(R.string.v20_code),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                      )
                      HintIconButton({ code = true }, tooltip = tr("edit_text")) {
                        Icon(Icons.Default.Code, tr("edit_text"))
                      }
                    }
                    Text(
                      body.take(260),
                      maxLines = 5,
                      overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                      style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                      ".html · Observable Notebook Kit 2.9.1 · Nexus 0.1.20",
                      style = MaterialTheme.typography.labelSmall,
                    )
                  }
                }
              },
              preview = { modifier ->
                Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
                  Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                      if (!draft.hideTitle)
                        Text(draft.visualTitle, style = MaterialTheme.typography.titleMedium)
                      if (body.isBlank()) Text(stringResource(R.string.next_empty_preview))
                      else NotebookDocumentView(draft, files, active = !code && !preview)
                    }
                  }
                }
              },
            )
          }
        }
      }
    }
    if (code)
      AcodeBlockEditor(draft, files, { code = false }, allowRename = false) { next, _ ->
        body = next.body
        code = false
      }
    if (preview) {
      val audio = rememberSession { DocumentAudioSession() }
      DisposableEffect(audio) { onDispose { audio.close() } }
      DocumentElementPreviewDialog(draft, files, null, 0, audio, {}, { preview = false })
    }
    NotebookWindowHost(windows, access)
  }
  error?.let { message ->
    SafeAlertDialog(
      { error = null },
      title = { Text(tr("error")) },
      text = { Text(message) },
      confirmButton = { TextButton({ error = null }) { Text(tr("close")) } },
    )
  }
}
