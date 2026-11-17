package com.karandaev.fieldfolio.core.ui.scroll

/**
 * Continuous monotone reparameterization keeps a lazily estimated thumb anchored after dragging.
 */
data class ScrollCalibration(val actual: Float = .5f, val shown: Float = .5f) {
  fun map(value: Float): Float {
    val p = value.coerceIn(0f, 1f)
    if (p == 0f || p == 1f) return p
    return (if (p <= actual && actual > 0f) shown * p / actual
      else if (actual < 1f) shown + (1f - shown) * (p - actual) / (1f - actual) else p)
      .coerceIn(0f, 1f)
  }
}
