package com.karandaev.fieldfolio.feature.shared.components.detectionpreview.overlay

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import com.karandaev.fieldfolio.core.domain.model.detection.Detection
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.geometry.calculateFittedImageRect
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.geometry.toCanvasRect
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.geometry.toOffset
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.geometry.toSize

/**
 * Draws detection bounding boxes over the displayed image or placeholder area.
 *
 * Detection bbox coordinates are normalized in 0.0..1.0 range.
 *
 * @param containerSize Preview container size.
 * @param imageAspectRatio Aspect ratio used to calculate fitted image area.
 * @param detections Detections to draw.
 * @param modifier Modifier applied to canvas.
 */
@Composable
internal fun DetectionOverlay(
  containerSize: IntSize,
  imageAspectRatio: Float,
  detections: List<Detection>,
  modifier: Modifier = Modifier,
) {
  val style = rememberDetectionOverlayStyle()

  Canvas(modifier = modifier) {
    val imageRect =
      calculateFittedImageRect(containerSize = containerSize, imageAspectRatio = imageAspectRatio)

    val textPaint =
      Paint().apply {
        isAntiAlias = true
        textSize = style.labelTextPx
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        this.style = Paint.Style.FILL
      }

    detections.forEach { detection ->
      val color = detectionClassColor(detection.clazz)
      val bboxRect = detection.bbox.toCanvasRect(imageRect)

      drawRect(
        color = color,
        topLeft = bboxRect.toOffset(),
        size = bboxRect.toSize(),
        style = Stroke(width = style.strokePx),
      )

      drawDetectionLabel(
        label = detection.toLabel(),
        color = color,
        bboxRect = bboxRect,
        imageRect = imageRect,
        textPaint = textPaint,
        style = style,
      )
    }
  }
}
