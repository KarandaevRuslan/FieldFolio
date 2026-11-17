package com.karandaev.fieldfolio.feature.models

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.models.decodeModelImage
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.detection.components.crop.cropper.*
import kotlinx.coroutines.*

/** Reuses the existing cropper canvas, transform state and normalized crop geometry. */
@Composable
fun AnalysisCropDialog(bytes: ByteArray, dismiss: () -> Unit, apply: (ByteArray) -> Unit) =
  AnalysisCropDialog(bytes, dismiss, null, apply)

@Composable
fun AnalysisCropDialog(
  bytes: ByteArray,
  dismiss: () -> Unit,
  selection: ((com.karandaev.fieldfolio.core.image.crop.NormalizedCropBox) -> Unit)?,
  apply: (ByteArray) -> Unit,
) {
  val loaded =
    produceState<android.graphics.Bitmap?>(null, bytes) {
      value =
        withContext(Dispatchers.Default) { runCatching { decodeModelImage(bytes) }.getOrNull() }
    }
  // Read readiness in this composition before moving the toolbar/image between adaptive panes.
  val bitmap = loaded.value
  val state = rememberSession { DetectCropperState() }
  var viewport by rememberSession { mutableStateOf(IntSize.Zero) }
  var applying by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  var ratio by rememberSession { mutableFloatStateOf(4f / 3f) }
  SafeAppDialog(dismiss) {
    Surface(Modifier.fillMaxSize()) {
      com.karandaev.fieldfolio.core.ui.layout.MediaWorkspace(
        controls = {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HintIconButton(dismiss, tooltip = tr("close")) {
              Icon(Icons.Default.Close, tr("close"))
            }
            Text(tr("crop_image"), Modifier.weight(1f))
            HintIconButton(state::reset, tooltip = tr("reset_zoom")) {
              Icon(Icons.Default.RestartAlt, tr("reset_zoom"))
            }
            HintIconButton(
              {
                val image = bitmap ?: return@HintIconButton
                if (viewport.width > 0 && viewport.height > 0) {
                  applying = true
                  scope.launch {
                    try {
                      val box =
                        calculateDetectCropBox(
                          IntSize(image.width, image.height),
                          viewport,
                          state.scale,
                          state.offset,
                        )
                      if (selection != null) {
                        selection(box)
                        return@launch
                      }
                      val output =
                        withContext(Dispatchers.Default) {
                          com.karandaev.fieldfolio.core.image.crop
                            .ImageCropper()
                            .cropBytes(bytes, box)
                        }
                      apply(output)
                    } finally {
                      applying = false
                    }
                  }
                }
              },
              enabled = bitmap != null && !applying,
              tooltip = tr("apply"),
            ) {
              Icon(Icons.Default.Check, tr("apply"))
            }
          }
        },
        details = {
          Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            val ratios = listOf(1f, 4f / 3f, bitmap?.let { it.width.toFloat() / it.height } ?: 1f)
            val labels = listOf("1:1", "4:3", tr("original_ratio"))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
              ratios.forEachIndexed { index, value ->
                SegmentedButton(
                  selected = ratio == value,
                  onClick = {
                    ratio = value
                    state.reset()
                  },
                  shape = SegmentedButtonDefaults.itemShape(index, ratios.size),
                  enabled = bitmap != null,
                ) {
                  Text(labels[index], maxLines = 1)
                }
              }
            }
          }
          Text(tr("crop_hint"), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        },
        image = {
          BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth, maxHeight * ratio)
            val height = width / ratio
            bitmap?.let { image ->
              DetectCropperCanvas(
                image.asImageBitmap(),
                IntSize(image.width, image.height),
                state,
                !applying,
                tr("crop_image"),
                { next ->
                  if (
                    viewport.width > 0 &&
                      viewport.height > 0 &&
                      next.width > 0 &&
                      next.height > 0 &&
                      viewport != next &&
                      kotlin.math.abs(
                        viewport.width.toFloat() / viewport.height -
                          next.width.toFloat() / next.height
                      ) < .01f
                  ) {
                    val selection =
                      calculateDetectCropBox(
                        IntSize(image.width, image.height),
                        viewport,
                        state.scale,
                        state.offset,
                      )
                    state.restoreFromCropBox(selection, IntSize(image.width, image.height), next)
                  }
                  viewport = next
                },
                Modifier.width(width).height(height),
              )
            } ?: CircularProgressIndicator()
          }
        },
      )
    }
  }
}
