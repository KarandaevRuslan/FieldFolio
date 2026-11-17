@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.document.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.components.fields.PresentationActions
import com.karandaev.fieldfolio.core.ui.components.map.AdaptiveOsmMap
import com.karandaev.fieldfolio.core.ui.components.map.camera.OsmMapCameraConfig
import com.karandaev.fieldfolio.core.ui.components.map.controls.model.OsmMapInteractions
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.*
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll
import com.karandaev.fieldfolio.core.ui.state.ViewerSession
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.viewer.DocumentElementCard
import com.karandaev.fieldfolio.feature.document.viewer.DocumentImage

@Composable
fun DocumentPanel(
  title: String,
  dismiss: () -> Unit,
  actions: @Composable RowScope.() -> Unit = {},
  content: @Composable ColumnScope.() -> Unit,
) {
  SafeAppDialog(dismiss, DialogProperties(dismissOnClickOutside = false)) {
    Surface(Modifier.fillMaxSize()) {
      Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
          TopAppBar(
            title = { Text(title, maxLines = 1) },
            navigationIcon = {
              HintIconButton(dismiss, tooltip = tr("close")) {
                Icon(Icons.Default.Close, tr("close"))
              }
            },
            actions = actions,
            windowInsets = WindowInsets(0, 0, 0, 0),
          )
        },
      ) { pad ->
        Column(
          Modifier.fillMaxSize()
            .padding(pad)
            .consumeWindowInsets(pad)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          content = content,
        )
      }
    }
  }
}

@Composable
fun DocumentNodeDialog(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  dismiss: () -> Unit,
  save: (DocumentNode, String?) -> Unit,
) = DocumentNodeDialog(node, files, dismiss, reanalyze = null, save = save)

@Composable
fun DocumentNodeDialog(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  dismiss: () -> Unit,
  reanalyze: ((DocumentNode) -> Unit)?,
  externalDirty: Boolean = false,
  save: (DocumentNode, String?) -> Unit,
) {
  if (node.kind == BlockKind.DOCUMENT) {
    NotebookDocumentProperties(node, files, dismiss, save)
    return
  }
  if (node.isTextBlock) {
    TextPropertiesDialog(node, files, dismiss, save = save)
    return
  }

  if (node.kind == BlockKind.CLOCK) {
    ClockPropertiesDialog(node, dismiss, save = save)
    return
  }
  if (node.kind in setOf(BlockKind.LOCATION, BlockKind.WEATHER)) {
    GeoNodeDialog(node, dismiss, save)
    return
  }
  var identity by rememberNodeIdentity(node)
  val originalBody = node.body
  var body by rememberSession(node.id) { mutableStateOf(originalBody) }
  var code by rememberSession { mutableStateOf(false) }
  var preview by rememberSession { mutableStateOf(false) }
  val draft = identity.asDraft(node).copy(body = body)
  val valid = identity.valid
  val close =
    rememberUnsavedClose(
      externalDirty || identity != node.identity || body != originalBody,
      dismiss,
    )
  val session = rememberSession { DocumentAudioSession() }
  DisposableEffect(session) { onDispose { session.close() } }
  SafeAppDialog(close) {
    Scaffold(
      Modifier.testTag("node-properties"),
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        TopAppBar(
          title = { Text(node.kind.label()) },
          windowInsets = WindowInsets(0, 0, 0, 0),
          navigationIcon = {
            HintIconButton(close, tooltip = tr("close")) { Icon(Icons.Default.Close, tr("close")) }
          },
          actions = {
            com.karandaev.fieldfolio.core.ui.components.actions.EditorActionRow {
              PresentationActions(identity, { identity = it })
              HintIconButton({ preview = true }, enabled = valid, tooltip = tr("view")) {
                Icon(Icons.Default.Preview, tr("view"))
              }
            }
            HintIconButton(
              { save(identity.asDraft(node).copy(body = body), null) },
              enabled = valid && (node.kind != BlockKind.MODEL_RESULT || node.result != null),
              tooltip = tr("save"),
            ) {
              Icon(Icons.Default.Save, tr("save"))
            }
          },
        )
      },
    ) { pad ->
      com.karandaev.fieldfolio.core.ui.layout.ElementWorkspace(
        Modifier.padding(pad),
        fields = {
          NodeIdentityFields(identity, { identity = it }, node.kind)
          if (node.kind == BlockKind.MODEL_RESULT)
            OutlinedCard(Modifier.fillMaxWidth()) {
              Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  Modifier.fillMaxWidth(),
                  verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                  Text(tr("text"), Modifier.weight(1f))
                  HintIconButton({ code = true }, enabled = true, tooltip = tr("edit_text")) {
                    Icon(Icons.Default.Code, tr("edit_text"))
                  }
                }
                HorizontalDivider()
                Text(
                  body.take(300).ifBlank { "—" },
                  maxLines = 5,
                  overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
              }
            }
          if (node.kind == BlockKind.MODEL_RESULT && reanalyze != null)
            OutlinedCard(Modifier.fillMaxWidth()) {
              ListItem(
                headlineContent = { Text(node.result?.modelName ?: tr("choose_model")) },
                supportingContent = {
                  node.result?.let { Text(com.karandaev.fieldfolio.feature.models.taskLabel(it)) }
                },
                trailingContent = {
                  HintIconButton(
                    { reanalyze(identity.asDraft(node).copy(body = body)) },
                    enabled = valid,
                    tooltip = tr("choose_model"),
                  ) {
                    Icon(Icons.Default.ModelTraining, tr("choose_model"))
                  }
                },
              )
            }
        },
        preview = { modifier ->
          Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            if (node.kind == BlockKind.MODEL_RESULT && draft.result == null)
              Text(
                androidx.compose.ui.res.stringResource(
                  com.karandaev.fieldfolio.R.string.v16_no_result
                )
              )
            else if (
              node.kind == BlockKind.MODEL_RESULT && files[draft.result?.inputAsset] != null
            ) {
              val r = draft.result!!
              DocumentImage(
                files[r.inputAsset],
                r,
                files[r.maskAsset],
                files,
                fillViewport = true,
                modifier = Modifier.fillMaxSize(),
              ) {
                preview = true
              }
            } else
              Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ViewerSession("properties-preview:${node.id}") {
                  DocumentElementCard(draft, files, null, 0, session, {}, editorPreview = true)
                }
              }
          }
        },
      )
    }
  }
  if (preview)
    ViewerSession("properties-full-preview:${node.id}") {
      DocumentElementPreviewDialog(draft, files, null, 0, session, {}, { preview = false })
    }
  if (code)
    AcodeBlockEditor(
      node.copy(kind = BlockKind.TEXT, body = body),
      emptyMap(),
      { code = false },
      allowRename = false,
    ) { next, _ ->
      body = next.body
      code = false
    }
}

@Composable
fun DocumentPointPicker(initial: MapPoint?, dismiss: () -> Unit, choose: (MapPoint) -> Unit) {
  var point by rememberSession { mutableStateOf(initial ?: MapPoint(51.5074, -0.1278)) }
  val original = rememberSession { point }
  val close = rememberUnsavedClose(point != original, dismiss)
  SafeAppDialog(close) {
    Surface(Modifier.fillMaxSize()) {
      Column {
        Row(
          Modifier.fillMaxWidth(),
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
          HintIconButton(close, tooltip = tr("close")) { Icon(Icons.Default.Close, tr("close")) }
          Text(tr("pick_map"), Modifier.weight(1f))
          HintIconButton({ choose(point) }, tooltip = tr("select")) {
            Icon(Icons.Default.Check, tr("select"))
          }
        }
        Text("${point.latitude}, ${point.longitude}", Modifier.padding(horizontal = 16.dp))
        AdaptiveOsmMap(
          listOf(OsmMarkerSpec("selected", point, OsmMarkerKind.Selected, tr("location_point"))),
          Modifier.fillMaxWidth().weight(1f),
          camera = OsmMapCameraConfig(initialCenter = point, initialZoom = 9.0),
          focusTarget = point,
          interactions = OsmMapInteractions(onTap = { point = it }),
        )
      }
    }
  }
}

@Composable
fun DocumentDestinationDialog(
  document: ReportDocument,
  source: List<String>,
  initial: String?,
  dismiss: () -> Unit,
  choose: (String?) -> Unit,
) {
  val tree = DocumentTree(document)
  val invalid = tree.subtree(source).map { it.id }.toSet()
  var folder by rememberSession { mutableStateOf(initial) }
  SafeAppDialog(dismiss) {
    Surface(Modifier.fillMaxSize()) {
      Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
          TopAppBar(
            title = { Text(tr("choose_folder")) },
            navigationIcon = {
              HintIconButton(dismiss, tooltip = tr("close")) {
                Icon(Icons.Default.Close, tr("close"))
              }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
          )
        },
        bottomBar = {
          Button(
            { choose(folder) },
            Modifier.fillMaxWidth().padding(12.dp),
            enabled = folder !in invalid,
          ) {
            Text(tr("move_here"))
          }
        },
      ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
          TextButton({ folder = folder?.let { tree.node(it).parentId } }) {
            Text(if (folder == null) tr("root") else "↑ ${tree.node(folder!!).name}")
          }
          tree
            .children(folder)
            .filter { it.kind == BlockKind.FOLDER && it.id !in invalid }
            .forEach { n ->
              ListItem(
                headlineContent = { Text(n.name) },
                leadingContent = { Icon(Icons.Default.Folder, null) },
                modifier = Modifier.clickable { folder = n.id },
              )
            }
        }
      }
    }
  }
}

@Composable
fun ChooseModelDialog(
  dismiss: () -> Unit,
  choose: (com.karandaev.fieldfolio.core.models.ModelChoice) -> Unit,
) = com.karandaev.fieldfolio.feature.models.ModelPickerDialog(dismiss, choose)
