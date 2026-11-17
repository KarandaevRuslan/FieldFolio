package com.karandaev.fieldfolio.core.ui.scroll

import kotlin.math.*

/** Retain measured extents instead of extrapolating every hidden row from the current viewport. */
internal class LazyExtentCache {
  private val heights = mutableMapOf<Int, Float>()
  private var count = -1
  private val keys = mutableMapOf<Int, Any?>()
  private var geometry: List<Any>? = null
  private var cached = 1f

  fun range(
    items: List<ScrollItemRect>,
    total: Int,
    viewport: Float,
    gap: Float,
    padding: Float = 0f,
    lanes: Int = 1,
  ): Float {
    var changed = false
    if (count != total || items.any { it.index in keys && it.key != keys[it.index] }) {
      heights.clear()
      keys.clear()
      count = total
      changed = true
    }
    items.forEach {
      if (it.index in 0 until total) {
        val height = it.height.coerceAtLeast(1f)
        if (heights[it.index] != height) changed = true
        heights[it.index] = height
        keys[it.index] = it.key
      }
    }
    val next = listOf(viewport, gap, padding, lanes)
    if (!changed && geometry == next) return cached
    geometry = next
    val measured = heights.values.sorted()
    // An oversized first cell must not be multiplied by the number of unseen cells.
    val unknown =
      (measured.getOrNull((measured.size - 1).coerceAtLeast(0) / 2) ?: 80f).coerceAtMost(
        (viewport / 2).coerceAtLeast(1f)
      )
    val columns = FloatArray(lanes.coerceAtLeast(1))
    for (index in 0 until total) {
      var lane = 0
      for (candidate in 1 until columns.size) if (columns[candidate] < columns[lane])
        lane = candidate
      columns[lane] += (heights[index] ?: unknown) + gap
    }
    cached =
      ((columns.maxOrNull() ?: 0f) - if (total > 0) gap else 0f)
        .plus(padding)
        .minus(viewport)
        .coerceAtLeast(1f)
    return cached
  }
}
