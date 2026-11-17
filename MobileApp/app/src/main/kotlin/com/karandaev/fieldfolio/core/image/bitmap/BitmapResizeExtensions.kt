package com.karandaev.fieldfolio.core.image.bitmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlin.math.roundToInt

/**
 * Resizes this bitmap so that its largest side is equal to [targetSizePx], while preserving the
 * original aspect ratio.
 *
 * If the largest side is already equal to [targetSizePx], the original bitmap is returned without
 * creating a copy.
 */
internal fun Bitmap.resizeLargestSideTo(targetSizePx: Int): Bitmap {
  val largestSide = maxOf(width, height)

  if (largestSide == targetSizePx) {
    return this
  }

  val scale = targetSizePx.toFloat() / largestSide.toFloat()
  val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
  val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)

  return this.scale(targetWidth, targetHeight)
}

/**
 * Resizes this bitmap into an exact [targetWidth] x [targetHeight] image using black letterbox
 * padding while preserving the original aspect ratio.
 */
internal fun Bitmap.letterboxTo(targetWidth: Int, targetHeight: Int): Bitmap {
  val output = createBitmap(targetWidth, targetHeight)

  val scale =
    minOf(targetWidth.toFloat() / width.toFloat(), targetHeight.toFloat() / height.toFloat())

  val scaledWidth = width * scale
  val scaledHeight = height * scale
  val left = (targetWidth - scaledWidth) / 2f
  val top = (targetHeight - scaledHeight) / 2f

  val destination = RectF(left, top, left + scaledWidth, top + scaledHeight)

  Canvas(output).apply {
    drawColor(Color.BLACK)
    drawBitmap(this@letterboxTo, null, destination, null)
  }

  return output
}

/**
 * Resizes this bitmap into an exact [targetSizePx] x [targetSizePx] square using black letterbox
 * padding while preserving the original aspect ratio.
 */
internal fun Bitmap.letterboxToSquare(targetSizePx: Int): Bitmap {
  return letterboxTo(targetWidth = targetSizePx, targetHeight = targetSizePx)
}
