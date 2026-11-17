@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.detection.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.*
import com.karandaev.fieldfolio.feature.document.route.*
import com.karandaev.fieldfolio.feature.document.state.*
import com.karandaev.fieldfolio.feature.document.viewer.*
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.dialog.details.*
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.dialog.details.palette.*

/** Inline data under the image. The palette and bbox calculations remain the original widgets. */
@Composable
fun AnalysisDetailsContent(
  result: ModelResultData,
  image: ByteArray?,
  resources: Map<String, ByteArray> = emptyMap(),
) {
  val palette = rememberAnalysisImagePalette(image)
  val size =
    remember(image) {
      image?.let { data ->
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
          val orientation =
            runCatching {
                android.media
                  .ExifInterface(data.inputStream())
                  .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
              }
              .getOrDefault(1)
          if (orientation in 5..8) IntSize(bounds.outHeight, bounds.outWidth)
          else IntSize(bounds.outWidth, bounds.outHeight)
        } else null
      }
    }
  val detections =
    remember(result) {
      result.boxes.map {
        Detection(it.label, it.confidence, NormalizedBBox(it.xMin, it.yMin, it.xMax, it.yMax))
      }
    }
  Column(
    Modifier.fillMaxWidth().padding(16.dp).testTag("analysis-details-content"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(taskLabel(result), style = MaterialTheme.typography.titleLarge)
    if (size != null) {
      Text("${size.width} × ${size.height} px")
      Text("${tr("aspect_ratio")}: ${"%.3f".format(size.width.toDouble()/size.height)}")
      Text("${tr("file_size")}: ${image!!.size} B")
      DetectionPreviewPaletteRow(palette)
    }
    ResultDetails(result, showDetailsAction = false)
    if (detections.isNotEmpty()) DetectionPreviewBoxesDetails(detections, size)
    if (result.layers.isNotEmpty())
      Text(
        "${tr("layers")}: ${result.layers.size} · ${tr("polygons")}: ${result.layers.sumOf{it.polygons.size}}"
      )
  }
}

/** Compatibility entry point for text-only callers, without nesting another image window. */
@Composable
fun AnalysisDetailsDialog(
  result: ModelResultData,
  image: ByteArray?,
  resources: Map<String, ByteArray> = emptyMap(),
  dismiss: () -> Unit,
) {
  SafeAppDialog(dismiss) {
    Surface(Modifier.fillMaxSize().testTag("analysis-details-window")) {
      Column {
        Row(Modifier.fillMaxWidth()) {
          HintIconButton(dismiss, tooltip = tr("close")) { Icon(Icons.Default.Close, tr("close")) }
          Text(tr("analysis_details"), Modifier.padding(12.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          AnalysisDetailsContent(result, image, resources)
        }
      }
    }
  }
}
