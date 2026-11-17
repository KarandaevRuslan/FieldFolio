@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.document.editor

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.*
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karandaev.fieldfolio.acode.settings.EditorPreferences
import com.karandaev.fieldfolio.acode.web.NotebookEditorWebView
import com.karandaev.fieldfolio.acode.web.readEditorSource
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.document.textSyntaxName
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.components.fields.PresentationActions
import com.karandaev.fieldfolio.core.ui.lifecycle.findActivity
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.viewer.NotebookHelpDialog
import com.karandaev.fieldfolio.feature.document.viewer.PagedTextDialog
import com.karandaev.fieldfolio.feature.document.viewer.TextFilePreview

/** In-memory only: private source is never written to SavedStateHandle or a Bundle. */
class AcodeDraftViewModel : ViewModel() {
  var active = false
    private set

  var original = ""
    private set

  var originalName = ""
    private set

  var text by mutableStateOf("")
  var name by mutableStateOf("")
  var anchor by mutableIntStateOf(0)
  var head by mutableIntStateOf(0)
  var scroll by mutableIntStateOf(0)
  var search by mutableStateOf("")
  var fullscreen by mutableStateOf(false)

  fun open(source: String, label: String) {
    if (!active) {
      original = source
      originalName = label
      text = source
      name = label
      active = true
    }
  }

  fun finish() {
    active = false
    original = ""
    originalName = ""
    text = ""
    name = ""
    anchor = 0
    head = 0
    scroll = 0
    search = ""
    fullscreen = false
  }

  override fun onCleared() {
    finish()
  }
}

@Composable
fun AcodeBlockEditor(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  dismiss: () -> Unit,
  save: (DocumentNode, String?) -> Unit,
) = AcodeBlockEditor(node, files, dismiss, allowRename = true, save = save)

/** Pocket Notebook's complete Acode runtime with a final native/JS snapshot on explicit save. */
@Composable
fun AcodeBlockEditor(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  dismiss: () -> Unit,
  allowRename: Boolean,
  save: (DocumentNode, String?) -> Unit,
) {
  var loaded by
    remember(node.id, files[node.asset]) { mutableStateOf(node.kind != BlockKind.TEXT_FILE) }
  val format by
    produceState<TextResourceCodec.Editable?>(null, node.id, files[node.asset]) {
      value =
        if (node.kind == BlockKind.TEXT_FILE)
          kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            files[node.asset]?.let(TextResourceCodec::editable)
          }
        else null
      loaded = true
    }
  if (!loaded) {
    DocumentPanel(node.name, dismiss) { CircularProgressIndicator() }
    return
  }
  val initial = if (node.kind == BlockKind.TEXT_FILE) format?.text else node.body
  if (initial == null) {
    DocumentPanel(node.name, dismiss) {
      Text(tr("text_readonly"))
      files[node.asset]?.let { TextFilePreview(it, node.name) }
    }
    return
  }
  var identity by rememberNodeIdentity(node)
  val draft: AcodeDraftViewModel = viewModel(key = "acode-${node.id}")
  draft.open(initial, node.name)
  var web by remember { mutableStateOf<WebView?>(null) }
  var confirm by rememberSession { mutableStateOf(false) }
  var rename by rememberSession { mutableStateOf(false) }
  var preview by rememberSession { mutableStateOf(false) }
  var help by rememberSession(node.id) { mutableStateOf(false) }
  var tooLarge by rememberSession { mutableStateOf(false) }
  var alive by remember { mutableStateOf(true) }
  val context = LocalContext.current
  DisposableEffect(draft, context) {
    onDispose {
      alive = false
      if (context.findActivity()?.isChangingConfigurations != true) draft.finish()
    }
  }
  val prefs = remember { EditorPreferences(context) }
  val language =
    LocalConfiguration.current.locales[0].language.let { if (it == "ru") "ru" else "en" }
  fun exit() {
    draft.finish()
    dismiss()
  }
  fun close() {
    if (draft.fullscreen) {
      draft.fullscreen = false
      return
    }
    readEditorSource(web, draft.text) { snapshot ->
      if (alive) {
        draft.text = snapshot
        if (
          snapshot != draft.original ||
            draft.name != draft.originalName ||
            identity != node.identity
        )
          confirm = true
        else exit()
      }
    }
  }
  fun commit() {
    readEditorSource(web, draft.text) { snapshot ->
      if (alive) {
        if (
          snapshot.length >
            (if (node.kind == BlockKind.TEXT_FILE) TextResourceCodec.EDIT_CHARS else 1_000_000)
        ) {
          tooLarge = true
          return@readEditorSource
        }
        val next =
          identity
            .copy(name = draft.name)
            .applyTo(node)
            .copy(
              name = draft.name.trim(),
              body = if (node.kind == BlockKind.TEXT_FILE) node.body else snapshot,
            )
        val content = snapshot.takeIf { node.kind == BlockKind.TEXT_FILE && it != draft.original }
        save(next, content)
        draft.finish()
      }
    }
  }
  SafeAppDialog(
    ::close,
    androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
  ) {
    Surface(Modifier.fillMaxSize().testTag("acode-editor-window")) {
      Column {
        if (!draft.fullscreen)
          TopAppBar(
            title = {
              Column {
                Text(draft.name, maxLines = 1)
                Text(
                  "." + textSyntaxName(node.copy(name = draft.name)).substringAfterLast('.', "txt"),
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            },
            navigationIcon = {
              HintIconButton(::close, tooltip = tr("back")) {
                Icon(Icons.Default.ArrowBack, tr("back"))
              }
            },
            actions = {
              if (
                node.kind == BlockKind.DOCUMENT ||
                  (node.isTextBlock &&
                    node.syntaxExtension?.lowercase() in setOf("js", "mjs", "cjs", "jsx"))
              )
                HintIconButton(
                  { help = true },
                  tooltip =
                    androidx.compose.ui.res.stringResource(
                      com.karandaev.fieldfolio.R.string.v21_help
                    ),
                ) {
                  Icon(
                    Icons.Default.HelpOutline,
                    androidx.compose.ui.res.stringResource(
                      com.karandaev.fieldfolio.R.string.v21_help
                    ),
                  )
                }

              com.karandaev.fieldfolio.core.ui.components.actions.EditorActionRow {
                if (allowRename)
                  HintIconButton(
                    {
                      readEditorSource(web, draft.text) { snapshot ->
                        if (alive) {
                          draft.text = snapshot
                          preview = true
                        }
                      }
                    },
                    tooltip = tr("view"),
                  ) {
                    Icon(Icons.Default.Preview, tr("view"))
                  }
                if (allowRename) PresentationActions(identity, { identity = it })
                if (allowRename)
                  HintIconButton({ rename = true }, tooltip = tr("rename")) {
                    Icon(Icons.Default.DriveFileRenameOutline, tr("rename"))
                  }
              }
              HintIconButton(::commit, enabled = draft.name.isNotBlank(), tooltip = tr("save")) {
                Icon(Icons.Default.Save, tr("save"))
              }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
          )
        NotebookEditorWebView(
          id = "geo-${node.id}",
          initialSource = draft.text,
          dark = MaterialTheme.colorScheme.background.luminance() < .5f,
          language = language,
          editorPreferences = prefs,
          onChanged = { if (alive) draft.text = it },
          selectionAnchor = draft.anchor,
          selectionHead = draft.head,
          editorScrollTop = draft.scroll,
          onCursorSelection = { a, h, s ->
            if (alive) {
              draft.anchor = a
              draft.head = h
              draft.scroll = s
            }
          },
          searchStateJson = draft.search,
          onSearchStateChange = { if (alive) draft.search = it },
          fullscreen = draft.fullscreen,
          onFullscreenChange = { if (alive) draft.fullscreen = it },
          modifier = Modifier.fillMaxWidth().weight(1f),
          fileName = textSyntaxName(node.copy(name = draft.name)),
          resourceFile = true,
          documentRuntime = node.kind == BlockKind.DOCUMENT,
          onSaveRequested = ::commit,
          onWebView = { web = it },
          active = !help && !preview,
        )
      }
    }
  }
  if (preview) {
    com.karandaev.fieldfolio.core.ui.state.ViewerSession("acode-preview:${node.id}") {
      PagedTextDialog(draft.text.toByteArray(Charsets.UTF_8), draft.name) { preview = false }
    }
  }
  if (help) NotebookHelpDialog { help = false }

  if (tooLarge)
    SafeAlertDialog(
      { tooLarge = false },
      title = { Text(tr("text_limit")) },
      confirmButton = { TextButton({ tooLarge = false }) { Text(tr("close")) } },
    )
  if (rename)
    com.karandaev.fieldfolio.core.ui.components.fields.ResourceIdentityDialog(
      identity.copy(name = draft.name),
      { rename = false },
      file = node.kind == BlockKind.TEXT_FILE,
      presentation = false,
      showVisibility = false,
    ) {
      identity = it
      draft.name = it.name
      rename = false
    }
  if (confirm)
    SafeAlertDialog(
      { confirm = false },
      title = { Text(tr("unsaved")) },
      text = { Text(tr("discard_body")) },
      confirmButton = { TextButton(::exit) { Text(tr("discard")) } },
      dismissButton = { TextButton({ confirm = false }) { Text(tr("cancel")) } },
    )
}

@Composable
fun DocumentNodeEditor(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  dismiss: () -> Unit,
  creating: Boolean = false,
  importFile: ((DocumentNode, ByteArray) -> Unit)? = null,
  save: (DocumentNode, String?) -> Unit,
) {
  if (node.kind == BlockKind.DOCUMENT) NotebookDocumentProperties(node, files, dismiss, save)
  else if (node.kind in setOf(BlockKind.FOLDER, BlockKind.IMAGE, BlockKind.AUDIO))
    NodeIdentityDialog(node, dismiss, creating) { save(it, null) }
  else if (node.kind in setOf(BlockKind.TEXT, BlockKind.TEXT_FILE))
    TextPropertiesDialog(node, files, dismiss, importFile = importFile, save = save)
  else if (node.kind == BlockKind.HEADING)
    TextPropertiesDialog(node, files, dismiss, importFile = importFile, save = save)
  else DocumentNodeDialog(node, files, dismiss, save)
}
