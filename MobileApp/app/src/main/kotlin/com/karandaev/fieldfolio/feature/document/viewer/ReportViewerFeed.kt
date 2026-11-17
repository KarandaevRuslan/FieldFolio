@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.material.ExperimentalMaterialApi::class,
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.*
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.state.ViewerSession
import com.karandaev.fieldfolio.core.ui.state.rememberViewerSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import kotlinx.coroutines.*

/** Renders a report feed with one shared scroll container and stable per-item viewer sessions. */
@Composable
internal fun ReportViewerFeed(
  vm: DocumentViewModel,
  folder: String?,
  reportId: Long,
  onFolder: (String?) -> Unit,
  device: MapPoint?,
  refresh: Long,
  audio: DocumentAudioSession,
  requestPermission: () -> Unit,
  wide: Boolean,
  headerVisible: Boolean,
  headerLoaded: Boolean,
  stableMetadata: @Composable () -> Unit,
  Toolbar: @Composable () -> Unit,
) {
  val tree = remember(vm.document) { DocumentTree(vm.document) }

  var expanded by rememberViewerSession("expanded", folder) { mutableStateOf(setOf<String>()) }
  val presentation by
    produceState<DocumentPresentation?>(
      vm.viewerPresentation?.takeIf { it.tree.document == vm.document && it.files == vm.files },
      vm.document,
      vm.files,
    ) {
      val snapshot = vm.document
      val resources = vm.files
      val computed =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
          DocumentPresentation(DocumentTree(snapshot), resources).also { p ->
            snapshot.nodes.forEach { p.hasVisual(it) }
          }
        }
      vm.viewerPresentation = computed
      value = computed
    }
  LaunchedEffect(presentation, folder) {
    if (presentation?.canOpenFolder(folder) == false) onFolder(null)
  }
  var pathOpen by rememberViewerSession("viewer-path-open") { mutableStateOf(false) }
  var pathError by rememberViewerSession("viewer-path-error") { mutableStateOf<String?>(null) }
  val pathFailure = stringResource(R.string.v18_viewer_folder_path)
  if (pathOpen && presentation != null)
    com.karandaev.fieldfolio.core.ui.components.fields.VfsPathDialog(
      tree.path(folder),
      { pathOpen = false },
      tag = "viewer-path",
      error = pathError,
    ) { path ->
      runCatching { presentation!!.resolveViewerPath(path, folder) }
        .onSuccess {
          onFolder(it)
          pathOpen = false
          pathError = null
        }
        .onFailure { pathError = pathFailure }
    }
  val shown = presentation?.children(folder).orEmpty()
  @Composable
  fun Leaf(node: DocumentNode) {
    ViewerSession("report:$reportId:item:${node.id}:${node.asset ?: node.result?.id ?: "body"}") {
      DocumentElementCard(node, vm.files, device, refresh, audio, requestPermission)
    }
  }
  @Composable
  fun Folder(node: DocumentNode, nested: Boolean = false) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("report-cell-${node.id}")) {
      Column {
        ListItem(
          headlineContent = { if (!node.hideTitle) Text(node.visualTitle) },
          leadingContent = { Icon(Icons.Default.Folder, null) },
          supportingContent =
            if (node.hideTitle) null
            else {
              { Text(presentation?.children(node.id).orEmpty().size.toString()) }
            },
          modifier =
            Modifier.clickable {
              if (nested) onFolder(node.id)
              else expanded = if (node.id in expanded) expanded - node.id else expanded + node.id
            },
          trailingContent = {
            Row {
              if (!nested)
                HintIconButton(
                  {
                    expanded = if (node.id in expanded) expanded - node.id else expanded + node.id
                  },
                  tooltip = tr(if (node.id in expanded) "collapse" else "expand"),
                ) {
                  Icon(
                    if (node.id in expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    tr(if (node.id in expanded) "collapse" else "expand"),
                  )
                }
              HintIconButton({ onFolder(node.id) }, tooltip = tr("open_folder")) {
                Icon(Icons.Default.ArrowForward, tr("open_folder"))
              }
            }
          },
        )
        if (!nested && node.id in expanded)
          Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            presentation?.children(node.id).orEmpty().forEach { child ->
              key(child.id) {
                if (child.kind == BlockKind.FOLDER) Folder(child, true) else Leaf(child)
              }
            }
          }
      }
    }
  }
  Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.weight(1f)) { Toolbar() }
      HintIconButton(
        {
          pathError = null
          pathOpen = true
        },
        enabled = presentation != null,
        tooltip = stringResource(R.string.library_path),
      ) {
        Icon(Icons.Default.EditLocationAlt, stringResource(R.string.library_path))
      }
    }
    com.karandaev.fieldfolio.core.ui.scroll.AppLazyStaggeredGrid(
      columns = if (wide) 2 else 1,
      modifier = Modifier.weight(1f).fillMaxWidth().testTag("report-view-grid"),
    ) {
      if (headerVisible && headerLoaded)
        item(key = "report-header") {
          Card(
            Modifier.fillMaxWidth().testTag("report-header"),
            colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
          ) {
            Column(Modifier.padding(16.dp)) {
              ViewerSession("report:$reportId:metadata") { stableMetadata() }
            }
          }
        }
      if (presentation != null && shown.isEmpty())
        item { Text(tr("empty"), Modifier.padding(16.dp)) }
      items(shown, key = { it.id }) { node ->
        if (node.kind == BlockKind.FOLDER) Folder(node) else Leaf(node)
      }
    }
  }
}
