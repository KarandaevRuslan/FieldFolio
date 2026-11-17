package com.karandaev.fieldfolio.core.image.preprocessing

/** Metadata needed to map model-input boxes back to original image coordinates. */
data class LetterboxTransform(
  val originalWidth: Int,
  val originalHeight: Int,
  val modelWidth: Int,
  val modelHeight: Int,
  val scale: Float,
) {

  val scaledWidth: Float
    get() = originalWidth * scale

  val scaledHeight: Float
    get() = originalHeight * scale

  val padX: Float
    get() = (modelWidth - scaledWidth) / 2f

  val padY: Float
    get() = (modelHeight - scaledHeight) / 2f

  fun modelNormalizedToOriginalNormalizedBox(
    xMin: Float,
    yMin: Float,
    xMax: Float,
    yMax: Float,
  ): OriginalNormalizedBox {
    val modelXMin = xMin * modelWidth
    val modelYMin = yMin * modelHeight
    val modelXMax = xMax * modelWidth
    val modelYMax = yMax * modelHeight

    val originalXMin = ((modelXMin - padX) / scale).coerceIn(0f, originalWidth.toFloat())

    val originalYMin = ((modelYMin - padY) / scale).coerceIn(0f, originalHeight.toFloat())

    val originalXMax = ((modelXMax - padX) / scale).coerceIn(0f, originalWidth.toFloat())

    val originalYMax = ((modelYMax - padY) / scale).coerceIn(0f, originalHeight.toFloat())

    return OriginalNormalizedBox(
      xMin = originalXMin / originalWidth,
      yMin = originalYMin / originalHeight,
      xMax = originalXMax / originalWidth,
      yMax = originalYMax / originalHeight,
    )
  }
}
