package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession

@Composable
fun DocumentElementCard(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  device: MapPoint?,
  refresh: Long,
  audio: DocumentAudioSession,
  permission: () -> Unit,
  editorPreview: Boolean = false,
) {
  Card(Modifier.fillMaxWidth().testTag("report-cell-${node.id}")) {
    Column(
      Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (node.kind != BlockKind.HEADING && node.headingLevel == null && !node.hideTitle)
        Text(node.visualTitle, style = MaterialTheme.typography.titleMedium)
      DocumentBlockBody(node, files, device, refresh, audio, permission, editorPreview)
    }
  }
}
