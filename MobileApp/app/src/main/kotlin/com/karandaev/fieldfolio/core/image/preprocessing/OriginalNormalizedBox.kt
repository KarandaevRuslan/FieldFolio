package com.karandaev.fieldfolio.core.image.preprocessing

/** Normalized box in original source image coordinates. */
data class OriginalNormalizedBox(
  val xMin: Float,
  val yMin: Float,
  val xMax: Float,
  val yMax: Float,
)
