@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.image.crop.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintFilledIconButton
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.lifecycle.findActivity
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.*
import com.karandaev.fieldfolio.feature.document.route.*
import com.karandaev.fieldfolio.feature.document.state.*
import com.karandaev.fieldfolio.feature.document.viewer.*
import kotlinx.coroutines.*

/** The same input/camera/crop/run/details flow is used in Models and in document editing. */
@Composable
fun AnalysisWorkspace(
  choice: ModelChoice,
  onDismiss: () -> Unit,
  document: ReportDocument? = null,
  files: Map<String, ByteArray> = emptyMap(),
  instanceKey: String? = null,
  initialImage: ByteArray? = null,
  initialText: String = "",
  onChangeModel: ((ByteArray?, String) -> Unit)? = null,
  standalone: Boolean = false,
  onKeep: (suspend (ModelExecution, ByteArray?) -> Unit)? = null,
) {
  val app = rememberAppServices()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val vm: AnalysisViewModel =
    viewModel(
      key = "analysis-${choice.remote}-${choice.id}" + (instanceKey?.let { "-$it" } ?: ""),
      factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(c: Class<T>): T =
            AnalysisViewModel(app.catalogModelRunner) as T
        },
    )
  // Observe the frame at the route boundary before passing closures into movableContent.
  // A projection changes bytes and overlays together; neither pane may retain the old input.
  val inputImage = vm.image
  val execution = vm.result
  val analyzing = vm.busy || vm.exportPreparing
  val export =
    androidx.activity.compose.rememberLauncherForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
      app.reportLibrary.externalPicker = false
      vm.exportResult(context.applicationContext, uri, app.moshi)
    }
  LaunchedEffect(vm.exportReady) {
    if (vm.exportReady) {
      vm.consumeExportRequest()
      try {
        app.reportLibrary.externalPicker = true
        export.launch("analysis-result.zip")
      } catch (e: Throwable) {
        app.reportLibrary.externalPicker = false
        vm.takeExportSnapshot()?.release()
        vm.error = e
      }
    }
  }
  val sourceImage = vm.frames.source
  val viewBounds = vm.frames.bounds
  val lastRunBounds = vm.runBounds
  LaunchedEffect(vm, instanceKey) {
    if (vm.image == null && vm.text.isBlank() && vm.result == null) {
      if (choice.textInput && initialText.isNotBlank()) vm.text(initialText)
      if (!choice.textInput && initialImage != null)
        vm.image(withContext(Dispatchers.Default) { initialImage.copyOf() })
    }
  }
  DisposableEffect(vm, context) {
    onDispose { if (context.findActivity()?.isChangingConfigurations != true) vm.clear() }
  }
  var editText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
  var inputs by rememberSession { mutableStateOf(false) }
  var zoom by rememberSession { mutableStateOf(false) }
  fun close() {
    if (vm.exportPreparing) return
    if (vm.busy) vm.cancel()
    vm.clear()
    onDismiss()
  }
  val guardedClose =
    com.karandaev.fieldfolio.core.ui.components.dialog.rememberUnsavedClose(
      !standalone && (vm.image != null || vm.text.isNotBlank() || vm.result != null),
      ::close,
    )
  SafeAppDialog(
    guardedClose,
    androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
  ) {
    Surface(Modifier.fillMaxSize().testTag("analysis-workspace")) {
      Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
          TopAppBar(
            title = {
              Column {
                Text(choice.name, maxLines = 1)
                Text(taskLabel(choice), style = MaterialTheme.typography.labelSmall)
              }
            },
            navigationIcon = {
              HintIconButton(guardedClose, tooltip = tr("back")) {
                Icon(Icons.Default.ArrowBack, tr("back"))
              }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            actions = {
              if (standalone)
                HintIconButton(
                  vm::prepareExport,
                  enabled = execution != null && !analyzing && !vm.exportWriting,
                  tooltip = tr("export"),
                ) {
                  Icon(com.karandaev.fieldfolio.core.ui.FileTransferIcons.Export, tr("export"))
                }
              if (onKeep != null)
                HintIconButton(
                  {
                    scope.launch {
                      execution?.let { onKeep(it, inputImage) }
                      close()
                    }
                  },
                  enabled = execution != null && !analyzing,
                  tooltip = tr(if (instanceKey != null) "save" else "add_result"),
                ) {
                  Icon(
                    if (instanceKey != null) Icons.Default.Save else Icons.Default.AddTask,
                    tr(if (instanceKey != null) "save" else "add_result"),
                  )
                }
            },
          )
        },
      ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
          @Composable
          fun RowScope.AnalysisActions() {
            if (analyzing)
              HintIconButton(vm::cancel, tooltip = tr("cancel")) {
                Icon(Icons.Default.Stop, tr("cancel"))
              }
            else
              HintFilledIconButton(
                { vm.run(choice) },
                enabled = if (choice.textInput) vm.text.isNotBlank() else inputImage != null,
                tooltip = tr("run"),
              ) {
                Icon(Icons.Default.PlayArrow, tr("run"))
              }
          }
          com.karandaev.fieldfolio.core.ui.layout.MediaWorkspace(
            controls = {
              if (choice.textInput)
                Surface(
                  Modifier.fillMaxWidth(),
                  shape = MaterialTheme.shapes.medium,
                  color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                  Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                  ) {
                    HintIconButton(
                      { editText = true },
                      enabled = !analyzing,
                      tooltip = tr("edit_text"),
                    ) {
                      Icon(Icons.Default.Code, tr("edit_text"))
                    }
                    if (document != null)
                      HintIconButton(
                        { inputs = true },
                        enabled = !analyzing,
                        tooltip = tr("choose_text_block"),
                      ) {
                        Icon(Icons.Default.FolderOpen, tr("choose_text_block"))
                      }
                    Spacer(Modifier.weight(1f))
                    VerticalDivider(Modifier.height(32.dp))
                    AnalysisActions()
                  }
                }
              else
                com.karandaev.fieldfolio.feature.media.ImageInputToolbar(
                  inputImage,
                  enabled = !analyzing,
                  onImage = { data, _ -> vm.image(data) },
                  onPreview = { zoom = true },
                  actionsVisible = true,
                  cropSource = sourceImage,
                  onCropSelected = vm::crop,
                  onResetCrop = vm::restoreRun,
                  resetLabel =
                    androidx.compose.ui.res.stringResource(
                      com.karandaev.fieldfolio.R.string.media_reset_run
                    ),
                  onRestoreSource = vm::restoreSource,
                  afterCamera = {
                    if (document != null)
                      HintIconButton(
                        { inputs = true },
                        enabled = !analyzing,
                        tooltip = tr("choose_image_block"),
                      ) {
                        Icon(Icons.Default.FolderOpen, tr("choose_image_block"))
                      }
                  },
                ) {
                  AnalysisActions()
                }
            },
            details = {
              if (choice.numericInput)
                Text(
                  androidx.compose.ui.res.stringResource(
                    com.karandaev.fieldfolio.R.string.finish_numeric_hint,
                    choice.local?.inputShape.toString(),
                  ),
                  style = MaterialTheme.typography.bodySmall,
                )
              if (analyzing) LinearProgressIndicator(Modifier.fillMaxWidth())
              if (execution != null && viewBounds != lastRunBounds)
                Text(
                  androidx.compose.ui.res.stringResource(
                    com.karandaev.fieldfolio.R.string.media_projected_hint
                  ),
                  style = MaterialTheme.typography.bodySmall,
                )
              execution?.let { ResultDetails(it.result, inputImage, resources = it.files) }
            },
            image = {
              if (choice.textInput)
                Column(
                  Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
                ) {
                  if (vm.text.isNotBlank()) DocumentText(vm.text, tr("input_text"))
                  else Text(tr("input_text"))
                }
              else
                inputImage?.let {
                  DocumentImage(
                    it,
                    execution?.result,
                    execution?.result?.maskAsset?.let { key -> execution?.files?.get(key) },
                    resources = execution?.files.orEmpty(),
                    fillViewport = true,
                    modifier = Modifier.fillMaxSize(),
                  ) {
                    zoom = true
                  }
                }
                  ?: Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                  ) {
                    Text(tr("choose_image"))
                  }
            },
          )
        }
      }
    }
  }
  if (editText)
    AcodeBlockEditor(
      DocumentNode(
        id = "input-${choice.remote}-${choice.id}",
        name = "input.txt",
        kind = BlockKind.TEXT,
        body = vm.text,
      ),
      emptyMap(),
      { editText = false },
      allowRename = false,
    ) { node, _ ->
      vm.text(node.body)
      editText = false
    }
  if (inputs && document != null)
    ModelInputPicker(document, choice.textInput, { inputs = false }, files = files) { node ->
      scope.launch {
        if (choice.textInput) {
          val text =
            if (node.kind == BlockKind.TEXT_FILE)
              withContext(Dispatchers.Default) {
                files[node.asset]?.let { TextResourceCodec.editable(it)?.text }
              }
            else node.body.ifBlank { node.name }
          if (text != null) vm.text(text)
          else
            vm.error =
              IllegalArgumentException(
                context.getString(com.karandaev.fieldfolio.R.string.doc_text_encoding_error)
              )
        } else
          files[node.asset]?.let { source ->
            vm.image(withContext(Dispatchers.Default) { source.copyOf() })
          }
      }

      inputs = false
    }
  if (zoom)
    inputImage?.let {
      ImageResultDialog(
        it,
        execution?.result,
        execution?.result?.maskAsset?.let { key -> execution?.files?.get(key) },
        resources = execution?.files.orEmpty(),
        resetFrame = vm::restoreRun,
        restoreSource = vm::restoreSource,
        editingEnabled = !analyzing,
      ) {
        zoom = false
      }
    }
  vm.error?.let { error ->
    SafeAlertDialog(
      { vm.error = null },
      title = { Text(tr("error")) },
      text = {
        Text(com.karandaev.fieldfolio.core.localization.localizedMessage(error.message.orEmpty()))
      },
      confirmButton = { TextButton({ vm.error = null }) { Text(tr("close")) } },
    )
  }
}

@Composable
fun ModelInputPicker(
  document: ReportDocument,
  text: Boolean,
  dismiss: () -> Unit,
  choose: (DocumentNode) -> Unit,
) = ModelInputPicker(document, text, dismiss, emptyMap(), choose)

@Composable
fun ModelInputPicker(
  document: ReportDocument,
  text: Boolean,
  dismiss: () -> Unit,
  files: Map<String, ByteArray>,
  choose: (DocumentNode) -> Unit,
) {
  val tree = remember(document) { DocumentTree(document) }
  var folder by rememberSession { mutableStateOf<String?>(null) }
  var query by rememberSession { mutableStateOf("") }
  var ascending by rememberSession { mutableStateOf(true) }
  val thumbs = remember { com.karandaev.fieldfolio.feature.document.state.DocumentThumbnailCache() }
  SafeAppDialog(dismiss) {
    Surface(Modifier.fillMaxSize()) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        Column {
          Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
          ) {
            HintIconButton(dismiss, tooltip = tr("close")) {
              Icon(Icons.Default.Close, tr("close"))
            }
            Text(
              tr(if (text) "choose_text_block" else "choose_image_block"),
              Modifier.weight(1f),
              style = MaterialTheme.typography.titleLarge,
            )
            HintIconButton({ ascending = !ascending }, tooltip = tr("sort")) {
              Icon(Icons.Default.Sort, tr("sort"))
            }
          }
          DocumentBreadcrumbs(tree, folder) {
            folder = it
            query = ""
          }
          OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            singleLine = true,
            label = { Text(tr("search_files")) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
          )
          val nodes =
            tree
              .children(folder)
              .filter { node ->
                (node.name.contains(query, true) || node.displayLabel.contains(query, true)) &&
                  (node.kind == BlockKind.FOLDER ||
                    if (text)
                      node.kind in setOf(BlockKind.TEXT, BlockKind.TEXT_FILE, BlockKind.HEADING)
                    else node.kind == BlockKind.IMAGE)
              }
              .sortedWith(
                compareByDescending<DocumentNode> { it.kind == BlockKind.FOLDER }
                  .thenBy { if (ascending) it.name.lowercase() else "" }
              )
              .let {
                if (ascending) it
                else
                  it.sortedWith(
                    compareByDescending<DocumentNode> { it.kind == BlockKind.FOLDER }
                      .thenByDescending { it.name.lowercase() }
                  )
              }
          com.karandaev.fieldfolio.core.ui.scroll.AppLazyGrid(
            if (wide) 2 else 1,
            Modifier.weight(1f).fillMaxWidth().testTag("model-input-grid"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (nodes.isEmpty()) item { Text(tr("empty")) }
            items(nodes, key = { it.id }) { node ->
              OutlinedCard(
                onClick = {
                  if (node.kind == BlockKind.FOLDER) {
                    folder = node.id
                    query = ""
                  } else choose(node)
                },
                modifier = Modifier.fillMaxWidth(),
              ) {
                ListItem(
                  headlineContent = { Text(node.displayLabel, maxLines = 2) },
                  supportingContent = { Text(node.kind.label()) },
                  leadingContent = {
                    com.karandaev.fieldfolio.feature.document.state.DocumentThumbnail(
                      node,
                      files,
                      thumbs,
                    ) {
                      Icon(node.kind.icon(), null)
                    }
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}
