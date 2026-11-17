@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.models.decodeModelImage
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog
import com.karandaev.fieldfolio.core.ui.state.rememberViewerSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.ViewerImageWindows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val ImageViewerScale = SemanticsPropertyKey<Float>("ImageViewerScale")

@Composable
fun ImageResultDialog(
  bytes: ByteArray,
  result: ModelResultData?,
  mask: ByteArray?,
  resources: Map<String, ByteArray> = emptyMap(),
  resetFrame: (() -> Unit)? = null,
  restoreSource: (() -> Unit)? = null,
  editingEnabled: Boolean = true,
  toolbarActions: @Composable RowScope.() -> Unit = {},
  dismiss: () -> Unit,
) {
  val parent = com.karandaev.fieldfolio.core.ui.state.LocalViewerSession.current
  val host = LocalViewerImages.current
  if (host != null && parent != null) {
    SideEffect {
      host.show(
        ViewerImageWindows.Image("$parent:image-window", bytes, result, mask, resources, dismiss)
      )
    }
    return
  }
  if (parent == null)
    ImageResultWindow(
      bytes,
      result,
      mask,
      resources,
      resetFrame,
      restoreSource,
      editingEnabled,
      toolbarActions,
      dismiss,
    )
  else
    com.karandaev.fieldfolio.core.ui.state.ViewerSession("$parent:image-window") {
      ImageResultWindow(
        bytes,
        result,
        mask,
        resources,
        resetFrame,
        restoreSource,
        editingEnabled,
        toolbarActions,
        dismiss,
      )
    }
}

@Composable
internal fun ImageResultWindow(
  bytes: ByteArray,
  result: ModelResultData?,
  mask: ByteArray?,
  resources: Map<String, ByteArray> = emptyMap(),
  resetFrame: (() -> Unit)? = null,
  restoreSource: (() -> Unit)? = null,
  editingEnabled: Boolean = true,
  toolbarActions: @Composable RowScope.() -> Unit = {},
  dismiss: () -> Unit,
) {
  var failed by rememberViewerSession("image-decode-failed", bytes) { mutableStateOf(false) }
  val imageState =
    key(bytes) {
      produceState<android.graphics.Bitmap?>(null) {
        value =
          withContext(Dispatchers.Default) { runCatching { decodeModelImage(bytes) }.getOrNull() }
        failed = value == null
      }
    }
  val bitmap = imageState.value
  val overlayState =
    key(mask) {
      produceState<android.graphics.Bitmap?>(null) {
        value =
          mask?.let {
            withContext(Dispatchers.Default) { runCatching { decodeModelImage(it) }.getOrNull() }
          }
      }
    }
  val overlay = overlayState.value
  val imageFailed = failed
  var enabledLayers by
    rememberViewerSession("image-layers", result) {
      mutableStateOf(result?.layers?.indices?.toSet().orEmpty())
    }
  val masks = layerMasks(result, resources, enabledLayers)
  var show by rememberViewerSession("image-overlay") { mutableStateOf(true) }
  var scale by rememberViewerSession("image-scale") { mutableFloatStateOf(1f) }
  var offset by
    rememberViewerSession("image-offset") {
      mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    }
  val transform = rememberTransformableState { zoom, pan, _ ->
    scale = (scale * zoom).coerceIn(1f, 8f)
    offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
  }
  SafeAppDialog(dismiss) {
    Surface(
      Modifier.fillMaxSize().testTag("image-result-window").semantics {
        this[ImageViewerScale] = scale
      }
    ) {
      com.karandaev.fieldfolio.core.ui.layout.MediaWorkspace(
        compactControls = result == null,
        controls = {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HintIconButton(dismiss, tooltip = tr("close")) {
              Icon(Icons.Default.Close, tr("close"))
            }
            if (result != null) {
              Text(tr("overlay"), Modifier.weight(1f))
              Switch(show, { show = it }, Modifier.testTag("result-overlay-toggle"))
            } else Spacer(Modifier.weight(1f))
            HintIconButton(
              {
                scale = 1f
                offset = androidx.compose.ui.geometry.Offset.Zero
                resetFrame?.invoke()
              },
              enabled = editingEnabled,
              tooltip = tr("reset_zoom"),
            ) {
              Icon(Icons.Default.RestartAlt, tr("reset_zoom"))
            }
            if (restoreSource != null)
              HintIconButton(
                {
                  scale = 1f
                  offset = androidx.compose.ui.geometry.Offset.Zero
                  restoreSource()
                },
                enabled = editingEnabled,
                tooltip =
                  androidx.compose.ui.res.stringResource(
                    com.karandaev.fieldfolio.R.string.media_restore_source
                  ),
              ) {
                Icon(
                  Icons.Default.SettingsBackupRestore,
                  androidx.compose.ui.res.stringResource(
                    com.karandaev.fieldfolio.R.string.media_restore_source
                  ),
                )
              }
            toolbarActions()
          }
        },
        details = {
          if (!result?.layers.isNullOrEmpty())
            androidx.compose.foundation.layout.FlowRow(
              Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              result!!.layers.forEachIndexed { index, layer ->
                FilterChip(
                  index in enabledLayers,
                  {
                    enabledLayers =
                      if (index in enabledLayers) enabledLayers - index else enabledLayers + index
                  },
                  label = { Text(layer.label) },
                  leadingIcon = { Box(Modifier.size(12.dp).background(segmentationColor(index))) },
                )
              }
            }
          if (result != null)
            com.karandaev.fieldfolio.feature.models.AnalysisDetailsContent(result, bytes, resources)
          else bitmap?.let { Text("${it.width} × ${it.height} px") }
        },
        image = {
          Box(
            Modifier.fillMaxSize().clipToBounds().transformable(transform).pointerInput(Unit) {
              detectTapGestures(
                onDoubleTap = {
                  scale = if (scale == 1f) 2f else 1f
                  offset = androidx.compose.ui.geometry.Offset.Zero
                }
              )
            }
          ) {
            bitmap?.let {
              FittedImage(
                it,
                result,
                overlay,
                show,
                Modifier.graphicsLayer(
                  scaleX = scale,
                  scaleY = scale,
                  translationX = offset.x,
                  translationY = offset.y,
                ),
                masks = masks,
                enabledLayers = enabledLayers,
              )
            } ?: Text(tr(if (imageFailed) "error" else "loading"))
          }
        },
      )
    }
  }
}
