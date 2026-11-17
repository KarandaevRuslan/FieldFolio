@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.document.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.document.textSyntaxName
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.components.fields.PresentationActions
import com.karandaev.fieldfolio.core.ui.layout.ElementWorkspace
import com.karandaev.fieldfolio.core.ui.state.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.viewer.DocumentElementCard
import kotlinx.coroutines.*

@Composable
fun TextPropertiesDialog(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  dismiss: () -> Unit,
  importFile: ((DocumentNode, ByteArray) -> Unit)? = null,
  save: (DocumentNode, String?) -> Unit,
) {
  var loaded by
    remember(node.id, files[node.asset]) { mutableStateOf(node.kind != BlockKind.TEXT_FILE) }
  val editable by
    produceState<TextResourceCodec.Editable?>(null, node.id, files[node.asset]) {
      if (node.kind == BlockKind.TEXT_FILE)
        value =
          withContext(Dispatchers.Default) { files[node.asset]?.let(TextResourceCodec::editable) }
      loaded = true
    }
  if (!loaded) {
    DocumentPanel(node.name, dismiss) { CircularProgressIndicator() }
    return
  }
  val original = if (node.kind == BlockKind.TEXT_FILE) editable?.text.orEmpty() else node.body
  var identity by rememberNodeIdentity(node)
  var body by rememberSession(node.id) { mutableStateOf(original) }
  var headingEnabled by
    rememberSession(node.id) { mutableStateOf(node.effectiveHeadingLevel != null) }
  var headingInput by
    rememberSession(node.id) { mutableStateOf((node.effectiveHeadingLevel ?: 1).toString()) }
  val headingValid = !headingEnabled || headingInput.toIntOrNull() in 1..6
  val heading =
    if (headingEnabled)
      headingInput.toIntOrNull()?.takeIf { it in 1..6 } ?: node.effectiveHeadingLevel ?: 1
    else null
  var syntax by rememberSession(node.id) { mutableStateOf(node.syntaxExtension.orEmpty()) }
  val extension = syntax.trim().removePrefix(".").lowercase()
  val syntaxValid = extension.isEmpty() || extension.matches(Regex("[a-z0-9]{1,12}"))
  var mode by rememberSession(node.id) { mutableStateOf(node.textOpenMode) }
  var pending by rememberSession(node.id) { mutableStateOf<ByteArray?>(null) }
  var fileMode by rememberSession(node.id) { mutableStateOf(node.kind == BlockKind.TEXT_FILE) }
  var code by rememberSession { mutableStateOf(false) }
  var preview by rememberSession { mutableStateOf(false) }
  var error by rememberSession { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val app = com.karandaev.fieldfolio.core.di.rememberAppServices()
  fun currentDraft() =
    identity
      .asDraft(node)
      .copy(
        kind = if (fileMode) BlockKind.TEXT_FILE else BlockKind.TEXT,
        body = if (fileMode) node.body else body,
        asset = if (fileMode) node.asset else null,
        headingLevel =
          if (headingEnabled) headingInput.toIntOrNull()?.takeIf { it in 1..6 } else null,
        level =
          if (headingEnabled) headingInput.toIntOrNull()?.takeIf { it in 1..6 } ?: node.level
          else node.level,
        textOpenMode = mode,
        syntaxExtension = syntax.trim().removePrefix(".").lowercase().takeIf { it.isNotEmpty() },
      )
  val draft = currentDraft()
  val sourceBytes = pending ?: files[node.asset]
  val encoding =
    key(sourceBytes, body, fileMode) {
      produceState<Result<ByteArray>?>(null) {
        if (fileMode && sourceBytes != null)
          value =
            withContext(Dispatchers.Default) {
              runCatching {
                val originalText = TextResourceCodec.editable(sourceBytes)?.text
                if (originalText == null || body == originalText) sourceBytes
                else TextResourceCodec.encode(body, TextResourceCodec.encoding(sourceBytes))
              }
            }
      }
    }
  val encoded = encoding.value?.getOrNull()
  val previewPath = draft.asset ?: "resources/doc/draft.txt"
  val privateFiles = if (fileMode && encoded != null) files + (previewPath to encoded!!) else files
  val previewNode = draft.copy(asset = if (fileMode) previewPath else null)
  val close =
    rememberUnsavedClose(
      identity != node.identity ||
        body != original ||
        headingEnabled != (node.effectiveHeadingLevel != null) ||
        headingInput != (node.effectiveHeadingLevel ?: 1).toString() ||
        extension != node.syntaxExtension.orEmpty() ||
        mode != node.textOpenMode ||
        pending != null ||
        fileMode != (node.kind == BlockKind.TEXT_FILE),
      dismiss,
    )
  val pick =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      app.reportLibrary.externalPicker = false
      if (uri != null)
        scope.launch {
          try {
            val bytes =
              withContext(Dispatchers.IO) {
                com.karandaev.fieldfolio.feature.models.readPickedBytes(
                  context,
                  uri,
                  TextResourceCodec.EDIT_BYTES,
                )
              }
            val decoded =
              withContext(Dispatchers.Default) { requireNotNull(TextResourceCodec.editable(bytes)) }
            pending = bytes
            body = decoded.text
            fileMode = true
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            error = e.message
          }
        }
    }
  val audio = rememberSession { DocumentAudioSession() }
  DisposableEffect(audio) { onDispose { audio.close() } }
  SafeAppDialog(close) {
    Scaffold(
      Modifier.testTag("text-properties"),
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        TopAppBar(
          title = { Text(tr("text")) },
          windowInsets = WindowInsets(0, 0, 0, 0),
          navigationIcon = {
            HintIconButton(close, tooltip = tr("close")) { Icon(Icons.Default.Close, tr("close")) }
          },
          actions = {
            com.karandaev.fieldfolio.core.ui.components.actions.EditorActionRow {
              PresentationActions(identity, { identity = it })
              if (importFile != null)
                HintIconButton(
                  {
                    app.reportLibrary.externalPicker = true
                    pick.launch(
                      arrayOf(
                        "text/*",
                        "application/json",
                        "application/xml",
                        "application/javascript",
                        "application/octet-stream",
                      )
                    )
                  },
                  tooltip = stringResource(R.string.v17_import_text),
                ) {
                  Icon(Icons.Default.FolderOpen, stringResource(R.string.v17_import_text))
                }
              HintIconButton({ preview = true }, tooltip = tr("view")) {
                Icon(Icons.Default.Preview, tr("view"))
              }
            }
            HintIconButton(
              {
                // Read all mutable fields when committing, rather than a render-time snapshot.
                val committed = currentDraft()
                if (
                  fileMode &&
                    importFile != null &&
                    (pending != null || body != original || node.kind != committed.kind)
                ) {
                  val bytes = encoded ?: return@HintIconButton
                  importFile(committed, bytes)
                } else save(committed, body.takeIf { fileMode && it != original })
              },
              enabled =
                identity.valid &&
                  headingValid &&
                  syntaxValid &&
                  (fileMode || body.length <= 1_000_000) &&
                  (!fileMode || sourceBytes == null || encoded != null),
              tooltip = tr("save"),
            ) {
              Icon(Icons.Default.Save, tr("save"))
            }
          },
        )
      },
    ) { pad ->
      ElementWorkspace(
        Modifier.padding(pad),
        fields = {
          NodeIdentityFields(identity, { identity = it }, draft.kind)
          Text(stringResource(R.string.v17_text_style), style = MaterialTheme.typography.labelLarge)
          SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
              !headingEnabled,
              { headingEnabled = false },
              shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) {
              Text(stringResource(R.string.v17_paragraph))
            }
            SegmentedButton(
              headingEnabled,
              { headingEnabled = true },
              shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) {
              Text(tr("heading"))
            }
          }
          if (headingEnabled)
            OutlinedTextField(
              headingInput,
              { headingInput = it.take(2) },
              Modifier.fillMaxWidth().testTag("text-heading-level"),
              singleLine = true,
              label = { Text(tr("heading_level")) },
              isError = !headingValid,
              keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                  keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
              supportingText = {
                if (!headingValid) Text(stringResource(R.string.v18_heading_range))
              },
            )
          OutlinedTextField(
            syntax,
            { syntax = it.take(13) },
            Modifier.fillMaxWidth().testTag("text-syntax-extension"),
            singleLine = true,
            label = { Text(stringResource(R.string.v18_syntax_extension)) },
            placeholder = { Text(textSyntaxName(node).substringAfterLast('.', "txt")) },
            isError = !syntaxValid,
            supportingText = { Text(stringResource(R.string.v18_syntax_help)) },
          )
          Text(stringResource(R.string.v17_open_text), style = MaterialTheme.typography.labelLarge)
          SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
              mode == TextOpenMode.PAGED,
              { mode = TextOpenMode.PAGED },
              shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) {
              Text(stringResource(R.string.v17_paged))
            }
            SegmentedButton(
              mode == TextOpenMode.ACODE,
              { mode = TextOpenMode.ACODE },
              shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) {
              Text("Acode")
            }
          }
          OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(Modifier.fillMaxWidth()) {
                Text(tr("text"), Modifier.weight(1f))
                HintIconButton(
                  { code = true },
                  enabled = !fileMode || editable != null || pending != null,
                  tooltip = tr("edit_text"),
                ) {
                  Icon(Icons.Default.Code, tr("edit_text"))
                }
              }
              Text(
                body.take(300).ifBlank { "—" },
                maxLines = 5,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
              )
            }
          }
          if (encoding.value?.isFailure == true)
            Text(
              stringResource(R.string.v21_text_encoding_error),
              color = MaterialTheme.colorScheme.error,
            )
          if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        },
        preview = { modifier ->
          Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            DocumentElementCard(
              previewNode.copy(body = if (fileMode) previewNode.body else body),
              privateFiles,
              null,
              0,
              audio,
              {},
              editorPreview = true,
            )
          }
        },
      )
    }
  }
  if (code)
    AcodeBlockEditor(
      DocumentNode(
        id = "${node.id}-source",
        name = identity.name.ifBlank { node.name },
        kind = BlockKind.TEXT,
        body = body,
        syntaxExtension = extension.takeIf { syntaxValid && it.isNotEmpty() },
      ),
      emptyMap(),
      { code = false },
      allowRename = false,
    ) { next, _ ->
      body = next.body
      code = false
    }
  if (preview)
    DocumentElementPreviewDialog(previewNode, privateFiles, null, 0, audio, {}, { preview = false })
}
