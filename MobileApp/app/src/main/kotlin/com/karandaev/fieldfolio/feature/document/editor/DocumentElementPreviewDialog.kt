@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.material.ExperimentalMaterialApi::class,
)

package com.karandaev.fieldfolio.feature.document.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.pullrefresh.*
import com.karandaev.fieldfolio.core.datarefresh.LocalRefreshScope
import com.karandaev.fieldfolio.core.datarefresh.rememberRefreshScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog
import com.karandaev.fieldfolio.core.ui.components.fields.PresentationActions
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll
import com.karandaev.fieldfolio.core.ui.state.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.viewer.*

@Composable
fun DocumentElementPreviewDialog(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  device: MapPoint?,
  refresh: Long,
  audio: DocumentAudioSession,
  permission: () -> Unit,
  dismiss: () -> Unit,
  update: ((DocumentNode) -> Unit)? = null,
  edit: (() -> Unit)? = null,
) {
  val previewRefresh = rememberRefreshScope()
  val scope = rememberCoroutineScope()
  val pull = rememberPullRefreshState(previewRefresh.refreshing, {
    scope.launch { previewRefresh.refresh() }
  })
  var rename by rememberViewerSession("preview-rename") { mutableStateOf(false) }
  @Composable
  fun RowScope.Actions() {
    if (update != null) {
      HintIconButton({ rename = true }, tooltip = tr("rename")) {
        Icon(Icons.Default.DriveFileRenameOutline, tr("rename"))
      }
      PresentationActions(node.identity, { update(it.applyTo(node)) })
    }
    if (edit != null)
      HintIconButton(edit, tooltip = tr("edit")) { Icon(Icons.Default.Edit, tr("edit")) }
  }
  if (node.kind == BlockKind.IMAGE && files[node.asset] != null) {
    ImageResultDialog(
      files.getValue(node.asset!!),
      null,
      null,
      toolbarActions = { Actions() },
      dismiss = dismiss,
    )
  } else if (node.kind == BlockKind.LOCATION) {
    LocationPreviewDialog(
      node,
      device,
      dismiss,
      edit ?: {},
      permission,
      toolbarActions = { Actions() },
    )
  } else
    SafeAppDialog(dismiss) {
      CompositionLocalProvider(LocalRefreshScope provides previewRefresh) {
      Scaffold(
        Modifier.testTag("element-preview-window"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
          TopAppBar(
            title = { Text(node.displayLabel, maxLines = 1) },
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
              HintIconButton(dismiss, tooltip = tr("close")) {
                Icon(Icons.Default.Close, tr("close"))
              }
            },
            actions = { Actions() },
          )
        },
      ) { pad ->
        Box(
          Modifier.fillMaxSize().padding(pad).then(
            if (previewRefresh.hasRefreshableNeeds) Modifier.pullRefresh(pull) else Modifier
          ),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            Modifier.widthIn(max = if (node.kind == BlockKind.AUDIO) 440.dp else 560.dp)
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            DocumentElementCard(
              node,
              files,
              device,
              refresh,
              audio,
              permission,
              editorPreview = true,
            )
          }
          if (previewRefresh.hasRefreshableNeeds)
            PullRefreshIndicator(previewRefresh.refreshing, pull, Modifier.align(Alignment.TopCenter))
        }
      }
      }
    }
  if (rename && update != null)
    NodeIdentityDialog(node, { rename = false }) {
      update(it)
      rename = false
    }
}
