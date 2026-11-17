@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.models.decodeModelImage
import com.karandaev.fieldfolio.core.ui.state.rememberViewerSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentImage(
  bytes: ByteArray?,
  result: ModelResultData? = null,
  mask: ByteArray? = null,
  resources: Map<String, ByteArray> = emptyMap(),
  compact: Boolean = false,
  fillViewport: Boolean = false,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  if (bytes == null) {
    Text(tr("missing"))
    return
  }
  var failed by rememberViewerSession("image-decode-failed", bytes) { mutableStateOf(false) }
  val imageState =
    key(bytes) {
      produceState<android.graphics.Bitmap?>(null) {
        value =
          withContext(Dispatchers.Default) {
            runCatching { decodeModelImage(bytes, 1024) }.getOrNull()
          }
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
  BoxWithConstraints(modifier.fillMaxWidth()) {
    val previewHeight =
      if (fillViewport) maxHeight
      else if (compact) 160.dp
      else
        bitmap?.let { (maxWidth * (it.height.toFloat() / it.width)).coerceIn(160.dp, 540.dp) }
          ?: 220.dp
    Box(
      Modifier.fillMaxWidth()
        .height(previewHeight)
        .testTag("document-image-preview")
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
      contentAlignment = Alignment.Center,
    ) {
      val masks = layerMasks(result, resources)
      bitmap?.let { FittedImage(it, result, overlay, true, masks = masks) }
        ?: Text(tr(if (imageFailed) "error" else "loading"))
    }
  }
}

@Composable
internal fun FittedImage(
  bitmap: android.graphics.Bitmap,
  result: ModelResultData?,
  mask: android.graphics.Bitmap?,
  show: Boolean,
  modifier: Modifier = Modifier,
  masks: Map<String, android.graphics.Bitmap> = emptyMap(),
  enabledLayers: Set<Int>? = null,
) {
  BoxWithConstraints(
    modifier.fillMaxSize().testTag("rendered-image-${bitmap.width}x${bitmap.height}"),
    contentAlignment = Alignment.Center,
  ) {
    val ratio = bitmap.width.toFloat() / bitmap.height
    val width = minOf(maxWidth, maxHeight * ratio)
    val height = width / ratio
    Box(Modifier.width(width).height(height)) {
      Image(
        bitmap.asImageBitmap(),
        null,
        Modifier.matchParentSize(),
        contentScale = ContentScale.FillBounds,
      )
      if (show) {
        mask
          ?.takeIf { result?.layers.isNullOrEmpty() }
          ?.let {
            Image(
              it.asImageBitmap(),
              null,
              Modifier.matchParentSize(),
              contentScale = ContentScale.FillBounds,
            )
          }
        masks["composite"]?.let { raster ->
          Image(
            raster.asImageBitmap(),
            null,
            Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds,
          )
        }
        Canvas(Modifier.matchParentSize()) {
          result?.layers?.forEachIndexed { index, layer ->
            if (enabledLayers == null || index in enabledLayers)
              layer.polygons.forEach { polygon ->
                val path =
                  Path().apply {
                    fillType = PathFillType.EvenOdd
                    polygon.rings.forEach { ring ->
                      ring.forEachIndexed { i, p ->
                        if (i == 0)
                          moveTo((p.x * size.width).toFloat(), (p.y * size.height).toFloat())
                        else lineTo((p.x * size.width).toFloat(), (p.y * size.height).toFloat())
                      }
                      close()
                    }
                  }
                val color = segmentationColor(index)
                drawPath(path, color.copy(alpha = .35f))
                drawPath(path, color, style = Stroke(2.dp.toPx()))
              }
          }
          result?.boxes?.forEach { b ->
            val x = (b.xMin * size.width).toFloat()
            val y = (b.yMin * size.height).toFloat()
            drawRect(
              Color.Cyan,
              androidx.compose.ui.geometry.Offset(x, y),
              androidx.compose.ui.geometry.Size(
                ((b.xMax - b.xMin) * size.width).toFloat(),
                ((b.yMax - b.yMin) * size.height).toFloat(),
              ),
              style = Stroke(2.dp.toPx()),
            )
            drawContext.canvas.nativeCanvas.drawText(
              "${b.label} ${(b.confidence*100).toInt()}%",
              x.coerceAtLeast(0f),
              (y - 4.dp.toPx()).coerceAtLeast(15.dp.toPx()),
              android.graphics.Paint().apply {
                color = android.graphics.Color.CYAN
                textSize = 12.dp.toPx()
                isAntiAlias = true
                setShadowLayer(2f, 0f, 0f, android.graphics.Color.BLACK)
              },
            )
          }
        }
      }
    }
  }
}
