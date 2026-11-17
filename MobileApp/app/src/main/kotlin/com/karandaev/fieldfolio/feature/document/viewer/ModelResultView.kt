package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll
import com.karandaev.fieldfolio.core.ui.state.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.models.*

@Composable
fun ModelResultSummary(result: ModelResultData) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Icon(taskIcon(result.task), null)
    Column(Modifier.weight(1f)) {
      Text(
        result.modelName,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
      )
      Text(taskLabel(result), style = MaterialTheme.typography.labelLarge)
      Text(
        "${result.provider} · ${result.elapsedMillis} ms",
        style = MaterialTheme.typography.labelSmall,
      )
    }
  }
  if (result.boxes.isNotEmpty()) Text("${tr("result")}: ${result.boxes.size}")
  if (result.layers.isNotEmpty() || result.maskAsset != null)
    Text("${tr("task_segmentation")}: ${result.layers.size.coerceAtLeast(1)}")
  result.scores.take(3).forEach { score ->
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        score.label,
        Modifier.weight(1f),
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
      )
      Text("${"%.1f".format(score.score*100)} %")
    }
    if (score.score in 0.0..1.0)
      LinearProgressIndicator(
        progress = { score.score.toFloat() },
        modifier = Modifier.fillMaxWidth(),
      )
  }
  result.values.take(3).forEach { value ->
    Row(Modifier.fillMaxWidth()) {
      Text(value.label, Modifier.weight(1f))
      Text("${"%.6g".format(value.value)} ${value.unit.orEmpty()}")
    }
  }
  if (result.text.isNotBlank()) DocumentText(result.text, result.modelName)
}

/** Non-image outputs have one details destination, without an empty image pane. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ModelOutputDialog(result: ModelResultData, files: Map<String, ByteArray>, dismiss: () -> Unit) {
  com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog(dismiss) {
    Scaffold(
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        TopAppBar(
          title = { Text(result.modelName, maxLines = 1) },
          windowInsets = WindowInsets(0, 0, 0, 0),
          navigationIcon = {
            HintIconButton(dismiss, tooltip = tr("close")) {
              Icon(Icons.Default.Close, tr("close"))
            }
          },
        )
      },
    ) { pad ->
      Box(
        Modifier.fillMaxSize().padding(pad),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter,
      ) {
        Column(
          Modifier.widthIn(max = 680.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          AnalysisDetailsContent(result, null, files)
        }
      }
    }
  }
}
