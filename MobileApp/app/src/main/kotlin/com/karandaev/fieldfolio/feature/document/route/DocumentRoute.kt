@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.material.ExperimentalMaterialApi::class,
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.karandaev.fieldfolio.feature.document.route

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.*
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.datarefresh.LocalRefreshScope
import com.karandaev.fieldfolio.core.datarefresh.rememberRefreshScope
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAlertDialog
import com.karandaev.fieldfolio.core.ui.lifecycle.findActivity
import com.karandaev.fieldfolio.core.ui.state.ViewerSession
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.editor.AudioPropertiesDialog
import com.karandaev.fieldfolio.feature.document.editor.ChooseModelDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentAddDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentDestinationDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentElementPreviewDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentFileBrowser
import com.karandaev.fieldfolio.feature.document.editor.DocumentNodeDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentNodeEditor
import com.karandaev.fieldfolio.feature.document.editor.ReportPropertiesDialog
import com.karandaev.fieldfolio.feature.document.state.DocumentPlaybackViewModel
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import com.karandaev.fieldfolio.feature.document.viewer.DocumentBreadcrumbs
import com.karandaev.fieldfolio.feature.document.viewer.DocumentImage
import com.karandaev.fieldfolio.feature.document.viewer.DocumentText
import com.karandaev.fieldfolio.feature.document.viewer.ImageResultDialog
import com.karandaev.fieldfolio.feature.document.viewer.LocalViewerImages
import com.karandaev.fieldfolio.feature.document.viewer.NotebookWindowHost
import com.karandaev.fieldfolio.feature.document.viewer.ReportViewerFeed
import com.karandaev.fieldfolio.feature.document.viewer.ViewerImageWindowHost
import com.karandaev.fieldfolio.feature.library.LibraryBrowserDialogs
import com.karandaev.fieldfolio.feature.library.LibraryViewModel
import com.karandaev.fieldfolio.feature.library.rememberLibraryTransfers
import com.karandaev.fieldfolio.feature.models.AnalysisWorkspace
import com.karandaev.fieldfolio.feature.models.pickedName
import com.karandaev.fieldfolio.feature.models.readPickedBytes
import kotlinx.coroutines.*

@Composable
/**
 * Coordinates report navigation, retained editor windows and permission/result hand-offs.
 *
 * Rendering lives in the viewer/editor packages; persistence and mutation invariants belong to the
 * session ViewModel and core tree. Rotation reuses the supplied owner rather than creating a draft.
 */
fun DocumentRoute(
  reportId: Long = 0,
  parentId: Long? = null,
  initialPoint: MapPoint? = null,
  editing: Boolean,
  owner: ViewModelStoreOwner,
  folderScope: String? = null,
  revision: Long? = null,
  onBack: () -> Unit,
  onSaved: (Long) -> Unit,
  onEdit: () -> Unit,
  onFolder: (String?) -> Unit,
  onDelete: () -> Unit,
) {
  val app = rememberAppServices()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val retainedViewer: com.karandaev.fieldfolio.core.ui.state.SessionStateViewModel = viewModel()
  val vm: DocumentViewModel =
    viewModel(
      owner,
      key = "document-$reportId-$parentId",
      factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(c: Class<T>): T =
            DocumentViewModel(
              app.reportLibrary,
              app.catalogModelRunner,
              reportId,
              parentId,
              initialPoint,
            )
              as T
        },
    )
  val transfers = rememberLibraryTransfers(app.reportLibrary)
  val libraryActions: LibraryViewModel =
    viewModel(
      owner,
      key = "document-actions-$reportId",
      factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(type: Class<T>): T =
            LibraryViewModel(app.reportLibrary) as T
        },
    )
  if (!editing) LibraryBrowserDialogs(libraryActions, {})
  val tree = remember(vm.document) { DocumentTree(vm.document) }
  val folder =
    (if (editing) vm.folder else folderScope)?.takeIf { tree.byId[it]?.kind == BlockKind.FOLDER }
  var editNode by rememberSaveable { mutableStateOf<String?>(null) }
  var pendingNode by rememberSaveable { mutableStateOf<String?>(null) }
  var previewNode by rememberSaveable { mutableStateOf<String?>(null) }
  var deleting by rememberSession { mutableStateOf<String?>(null) }
  var move by rememberSession { mutableStateOf<String?>(null) }
  var selected by rememberSaveable { mutableStateOf<String?>(null) }
  var addMenu by rememberSession { mutableStateOf(false) }
  var discard by rememberSession { mutableStateOf(false) }
  var deleteReport by rememberSession { mutableStateOf(false) }
  val resultLabel = tr("model_result")
  var choosingModel by rememberSession { mutableStateOf(false) }
  var chooseInput by rememberSession { mutableStateOf(false) }
  var replacingNode by rememberSaveable { mutableStateOf<String?>(null) }
  var activeModelKey by rememberSaveable { mutableStateOf<String?>(null) }
  val catalogModels by app.modelCatalog.models.collectAsStateWithLifecycle()
  val remoteModels by app.serverModels.models.collectAsStateWithLifecycle()
  val analysisChoices = catalogChoices(catalogModels, remoteModels)
  var importKind by rememberSaveable { mutableStateOf(BlockKind.IMAGE) }
  var imageInput by rememberSaveable { mutableStateOf(false) }
  var imageIdentity by rememberSession {
    mutableStateOf(com.karandaev.fieldfolio.core.names.ResourceIdentity("image.jpg"))
  }
  var coverImport by rememberSaveable { mutableStateOf(false) }
  var refresh by rememberSession { mutableLongStateOf(0) }
  var properties by rememberSaveable { mutableStateOf(editing && reportId == 0L) }
  var showCover by rememberSaveable { mutableStateOf(false) }
  var headerVisible by rememberSaveable(reportId) { mutableStateOf(true) }
  var headerLoaded by rememberSaveable(reportId) { mutableStateOf(false) }
  LaunchedEffect(reportId) {
    if (!headerLoaded) {
      headerVisible =
        withContext(Dispatchers.IO) { ReportViewPreferences.headerVisible(context, reportId) }
      headerLoaded = true
    }
  }

  val permission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      app.resourceCoordinator.permissionChanged()
    }
  val requestPermission = {
    permission.launch(
      arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
      )
    )
  }
  // VFS reads a snapshot. Only composed widgets subscribe to live device/location updates.
  val resourceCoordinator = app.resourceCoordinator
  val deviceFix by resourceCoordinator.location.collectAsStateWithLifecycle()
  val device = deviceFix.point
  val refreshScope = rememberRefreshScope()
  val documentWebPool: com.karandaev.fieldfolio.core.document.runtime.DocumentWebPool =
    viewModel(owner, key = "web-$reportId-$parentId")
  val audioOwner: DocumentPlaybackViewModel = viewModel(owner, key = "audio-$reportId-$parentId")
  val audio = audioOwner.session
  val lifecycle = LocalLifecycleOwner.current
  val latestSaved by rememberUpdatedState(onSaved)
  LaunchedEffect(vm.savedId, lifecycle) {
    lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      vm.savedId?.let { id ->
        if (context.findActivity()?.isChangingConfigurations != true) {
          vm.consumeSaved()
          latestSaved(id)
        }
      }
    }
  }
  DisposableEffect(lifecycle, audio) {
    val observer = LifecycleEventObserver { _, event ->
      if (
        event == Lifecycle.Event.ON_STOP && context.findActivity()?.isChangingConfigurations != true
      )
        audio.pause()
    }
    lifecycle.lifecycle.addObserver(observer)
    onDispose { lifecycle.lifecycle.removeObserver(observer) }
  }
  LaunchedEffect(revision) {
    if (!editing && !vm.loading && reportId > 0 && revision != null && vm.stamp != revision)
      vm.refresh()
  }
  LaunchedEffect(vm) {
    app.reportLibrary.session.collect {
      if (
        (reportId > 0 || parentId != null) &&
          !runCatching { app.reportLibrary.isUnlocked(if (reportId > 0) reportId else parentId!!) }
            .getOrDefault(false)
      ) {
        activeModelKey = null
        editNode = null
        pendingNode = null
        previewNode = null
        selected = null
        properties = false
        imageInput = false
        showCover = false
        choosingModel = false
        discard = false
        audio.clear()
        retainedViewer.clearViewer()
        documentWebPool.clearReport(vm.runtimeScope)
        vm.discardForLock()
      }
    }
  }
  fun back() {
    if (editing && folder != null) {
      vm.navigate(tree.node(folder).parentId)
      selected = null
    } else if (editing && vm.dirty) discard = true else onBack()
  }
  BackHandler(onBack = ::back)
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      app.reportLibrary.externalPicker = false
      if (uri != null)
        scope.launch {
          try {
            vm.addFile(
              readPickedBytes(context, uri, 48 * 1024 * 1024),
              pickedName(context, uri),
              context.contentResolver.getType(uri) ?: "application/octet-stream",
              importKind,
              coverImport,
            )
            if (!coverImport && vm.error == null) {
              vm.document.nodes.lastOrNull()?.let { editNode = it.id }
            }
          } catch (e: Throwable) {
            vm.error = e
          }
        }
    }
  fun pick(kind: BlockKind, cover: Boolean = false) {
    importKind = kind
    coverImport = cover
    if (kind == BlockKind.IMAGE) {
      imageIdentity = com.karandaev.fieldfolio.core.names.ResourceIdentity("image.jpg")
      imageInput = true
      return
    }
    app.reportLibrary.externalPicker = true
    picker.launch(
      arrayOf(
        when (kind) {
          BlockKind.IMAGE -> "image/*"
          BlockKind.AUDIO -> "audio/*"
          else -> "*/*"
        }
      )
    )
  }
  val pull =
    rememberPullRefreshState(
      vm.loading || refreshScope.refreshing,
      {
        refresh++
        // Data needs are declared by the visible UI; VFS get() itself never starts polling.
        scope.launch { refreshScope.refresh() }
        if (reportId > 0 && (!editing || !vm.dirty)) vm.refresh()
      },
    )
  SideEffect { vm.runtimeLocation = device }
  val documentAccess = vm.documentAccess(app.weatherProvider)
  CompositionLocalProvider(
    com.karandaev.fieldfolio.core.document.runtime.LocalDocumentAccess provides documentAccess,
    com.karandaev.fieldfolio.core.document.runtime.LocalDocumentWebPool provides documentWebPool,
    com.karandaev.fieldfolio.core.document.runtime.LocalNotebookWindows provides vm.notebookWindows,
    LocalRefreshScope provides refreshScope,
  ) {
    Scaffold(
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        com.karandaev.fieldfolio.feature.document.components.DocumentTopBar(
          vm,
          editing,
          reportId,
          transfers,
          libraryActions,
          headerVisible,
          headerLoaded,
          { visible ->
            headerVisible = visible
            ReportViewPreferences.setHeaderVisible(context, reportId, visible)
          },
          ::back,
          { properties = true },
          onEdit,
          { deleteReport = true },
        )
      },
    ) { padding ->
      BoxWithConstraints(
        Modifier.fillMaxSize()
          .padding(padding)
          .consumeWindowInsets(padding)
          .then(
            if (!editing) Modifier.pullRefresh(pull).testTag("report-pull-refresh") else Modifier
          )
      ) {
        val wide = maxWidth > maxHeight && maxWidth >= 600.dp
        if (vm.loading) {
          CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
          @Composable
          fun Metadata() {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text(
                vm.displayName?.takeIf { it.isNotBlank() } ?: vm.title,
                style = MaterialTheme.typography.headlineMedium,
              )
              if (vm.description.isNotBlank()) DocumentText(vm.description, vm.title)
              vm.document.cover?.let {
                DocumentImage(vm.files[it], compact = true, onClick = { showCover = true })
              }
            }
          }
          val currentMetadata = rememberUpdatedState<@Composable () -> Unit>({ Metadata() })
          val stableMetadata = remember { movableContentOf { currentMetadata.value() } }
          @Composable
          fun Toolbar() {
            DocumentBreadcrumbs(tree, folder) { target ->
              if (editing) {
                vm.navigate(target)
                selected = null
              } else onFolder(target)
            }
          }
          @Composable
          fun EditorFiles() {
            DocumentFileBrowser(
              vm,
              folder,
              {
                vm.navigate(it)
                selected = null
              },
              onOpen = { node ->
                selected = node.id
                previewNode = node.id
              },
              onEdit = { node -> editNode = node.id },
              add = {
                Box {
                  HintIconButton({ addMenu = true }, enabled = !vm.busy, tooltip = tr("add")) {
                    Icon(Icons.Default.Add, tr("add"))
                  }
                }
              },
            )
          }
          val currentEditor = rememberUpdatedState<@Composable () -> Unit>({ EditorFiles() })
          val stableEditor = remember { movableContentOf { currentEditor.value() } }
          if (editing) stableEditor()
          else
            CompositionLocalProvider(LocalViewerImages provides vm.viewerImages) {
              ViewerSession("report:$reportId:feed") {
                ReportViewerFeed(
                  vm,
                  folder,
                  reportId,
                  onFolder,
                  device,
                  refresh,
                  audio,
                  requestPermission,
                  wide,
                  headerVisible,
                  headerLoaded,
                  stableMetadata,
                  { Toolbar() },
                )
              }
            }
        }
        if (!editing) PullRefreshIndicator(vm.loading || refreshScope.refreshing, pull, Modifier.align(Alignment.TopCenter))
      }
    }
    if (addMenu)
      DocumentAddDialog({ addMenu = false }) { kind, label ->
        addMenu = false
        when (kind) {
          BlockKind.IMAGE,
          BlockKind.AUDIO,
          BlockKind.TEXT_FILE -> pick(kind)
          BlockKind.MODEL_RESULT -> {
            val n = vm.add(kind, label)
            pendingNode = n.id
            selected = n.id
            vm.stageModelProperties(n)
            editNode = n.id
          }
          else -> {
            val n = vm.add(kind, label)
            selected = n.id
            pendingNode = n.id
            editNode = n.id
          }
        }
      }
    if (!editing) ViewerImageWindowHost(vm.viewerImages)
    if (properties)
      ReportPropertiesDialog(
        vm,
        {
          if (reportId == 0L && !com.karandaev.fieldfolio.core.names.ItemNames.valid(vm.title))
            onBack()
          else properties = false
        },
        { pick(BlockKind.IMAGE, true) },
        { showCover = true },
      )
    if (imageInput)
      com.karandaev.fieldfolio.feature.document.editor.DocumentImageImportDialog(
        vm,
        reportId,
        coverImport,
        imageIdentity,
        { imageIdentity = it },
        { imageInput = false },
      )
    editNode?.let { id ->
      tree.byId[id]?.let { n ->
        if (n.kind == BlockKind.IMAGE) {
          var identity by rememberSession(n.id) { mutableStateOf(n.identity) }
          com.karandaev.fieldfolio.feature.media.ReportImageInputDialog(
            "image-node-$reportId-${n.id}",
            tr("choose_image"),
            { editNode = null },
            initialBytes = vm.files[n.asset],
            initialName = n.name,
            document = vm.document,
            files = vm.files,
            preserveNameOnAcquire = true,
            initialIdentity = n.identity,
            onIdentity = { identity = it },
          ) { bytes, _ ->
            vm.updateImage(n, bytes, identity)
            if (vm.error == null) {
              editNode = null
              pendingNode = null
            } else bytes.fill(0)
            vm.error == null
          }
        } else if (n.kind == BlockKind.AUDIO)
          AudioPropertiesDialog(n, vm.files, { editNode = null }, document = vm.document) {
            next,
            cover ->
            vm.updateAudio(next, cover)
            if (vm.error == null) {
              pendingNode = null
              editNode = null
            }
          }
        else if (n.kind == BlockKind.MODEL_RESULT) {
          val draft = vm.modelDraft?.takeIf { it.id == id } ?: n
          DocumentNodeDialog(
            draft,
            vm.files + vm.modelFiles,
            {
              vm.discardModelDraft()
              if (pendingNode == id) {
                vm.remove(id)
                pendingNode = null
                selected = null
              }
              replacingNode = null
              editNode = null
            },
            reanalyze = { next ->
              vm.stageModelProperties(next)
              replacingNode = id
              vm.reanalysisImage = (vm.files + vm.modelFiles)[next.result?.inputAsset]
              vm.reanalysisText = next.result?.inputText.orEmpty()
              editNode = null
              choosingModel = true
            },
            externalDirty = vm.modelDraft?.let { it != n } == true,
          ) { next, _ ->
            vm.commitModelDraft(next)
            if (vm.error == null) {
              pendingNode = null
              replacingNode = null
              editNode = null
            }
          }
        } else
          DocumentNodeEditor(
            n,
            vm.files,
            {
              if (pendingNode == id) {
                vm.remove(id)
                selected = null
                pendingNode = null
              }
              editNode = null
            },
            creating = pendingNode == id,
            importFile = { next, bytes ->
              vm.updateTextResource(next, bytes)
              if (vm.error == null) {
                pendingNode = null
                editNode = null
              }
            },
          ) { next, text ->
            vm.update(next)
            if (vm.error == null) {
              if (text != null) vm.editTextFile(next, text)
              pendingNode = null
              editNode = null
            }
          }
      }
    }
    previewNode?.let { id ->
      tree.byId[id]?.let { n ->
        ViewerSession("editor-preview:$reportId:${n.id}") {
          DocumentElementPreviewDialog(
            n,
            vm.files,
            device,
            refresh,
            audio,
            requestPermission,
            { previewNode = null },
            update = vm::update,
            edit = {
              previewNode = null
              editNode = n.id
            },
          )
        }
      }
    }
    deleting?.let { id ->
      SafeAlertDialog(
        { deleting = null },
        title = { Text(tr("delete")) },
        text = { Text(tr("confirm_delete")) },
        confirmButton = {
          TextButton({
            vm.remove(id)
            deleting = null
            if (selected == id) selected = null
          }) {
            Text(tr("delete"))
          }
        },
        dismissButton = { TextButton({ deleting = null }) { Text(tr("cancel")) } },
      )
    }
    move?.let { id ->
      DocumentDestinationDialog(vm.document, listOf(id), folder, { move = null }) { target ->
        vm.transfer(listOf(id), target, false)
        move = null
      }
    }
    if (choosingModel)
      ChooseModelDialog({
        choosingModel = false
        editNode = replacingNode
      }) { choice ->
        choosingModel = false
        activeModelKey = "${choice.remote}:${choice.id}"
      }
    analysisChoices
      .firstOrNull { "${it.remote}:${it.id}" == activeModelKey }
      ?.let { choice ->
        AnalysisWorkspace(
          choice,
          {
            activeModelKey = null
            editNode = replacingNode
          },
          vm.document,
          vm.files,
          instanceKey = replacingNode,
          initialImage = vm.reanalysisImage,
          initialText = vm.reanalysisText,
          onChangeModel = { image, text ->
            vm.reanalysisImage = image
            vm.reanalysisText = text
            activeModelKey = null
            choosingModel = true
          },
        ) { execution, image ->
          vm.stageAnalysisResult(execution, image)
          vm.reanalysisImage = null
          vm.reanalysisText = ""
        }
      }
    if (discard)
      SafeAlertDialog(
        { discard = false },
        title = { Text(tr("unsaved")) },
        text = { Text(tr("discard_body")) },
        confirmButton = {
          TextButton({
            discard = false
            onBack()
          }) {
            Text(tr("discard"))
          }
        },
        dismissButton = { TextButton({ discard = false }) { Text(tr("cancel")) } },
      )
    if (deleteReport)
      SafeAlertDialog(
        { deleteReport = false },
        title = { Text(tr("report_delete")) },
        text = { Text(tr("confirm_delete")) },
        confirmButton = {
          TextButton({
            deleteReport = false
            onDelete()
          }) {
            Text(tr("delete"))
          }
        },
        dismissButton = { TextButton({ deleteReport = false }) { Text(tr("cancel")) } },
      )
    if (showCover)
      vm.files[vm.document.cover]?.let { data ->
        ViewerSession("report:$reportId:cover:${vm.document.cover}") {
          ImageResultDialog(data, null, null) { showCover = false }
        }
      }
    vm.error?.let { error ->
      SafeAlertDialog(
        { vm.error = null },
        title = { Text(tr("error")) },
        text = { Text(error.message.orEmpty()) },
        confirmButton = { TextButton({ vm.error = null }) { Text(tr("close")) } },
      )
    }
    NotebookWindowHost(vm.notebookWindows, documentAccess)
  }
}
