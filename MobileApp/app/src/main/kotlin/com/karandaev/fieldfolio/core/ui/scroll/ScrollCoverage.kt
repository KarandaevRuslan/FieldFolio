package com.karandaev.fieldfolio.core.ui.scroll

import kotlin.math.*

data class ScrollItemRect(val index: Int, val top: Float, val height: Float, val key: Any? = null)

/** Each item contributes continuously at both viewport edges. Unseen earlier items are complete. */
fun coverageWindow(
  items: List<ScrollItemRect>,
  count: Int,
  start: Float,
  end: Float,
  gap: Float = 0f,
  backward: Boolean = true,
  forward: Boolean = true,
  lanes: Int = 1,
): ScrollWindow {
  if (count <= 0 || end <= start) return ScrollWindow(0f, 0f, false)
  val relevant = items.filter { it.index in 0 until count && it.top - gap / 2 < end }
  if (relevant.isEmpty()) return ScrollWindow(if (backward) 1f else 0f, 0f, backward || forward)
  val complete = (relevant.maxOf { it.index } + 1 - relevant.size).coerceAtLeast(0).toFloat()
  fun covered(y: Float) =
    complete +
      relevant
        .sumOf {
          ((y - (it.top - gap / 2)) / (it.height + gap).coerceAtLeast(1f))
            .coerceIn(0f, 1f)
            .toDouble()
        }
        .toFloat()
  val before = covered(start).coerceIn(0f, count.toFloat())
  val after = (count - covered(end)).coerceIn(0f, count.toFloat())
  val progress =
    when {
      !backward -> 0f
      !forward -> 1f
      else -> (before / (before + after).coerceAtLeast(.001f)).coerceIn(.0001f, .9999f)
    }
  return ScrollWindow(
    progress.coerceIn(0f, 1f),
    0f,
    backward || forward,
    relevant.first().height + gap,
    count,
    (count - before - after).coerceAtLeast(.01f),
    (before + after) * relevant.map { it.height + gap }.sorted()[(relevant.size - 1) / 2] /
      lanes.coerceAtLeast(1),
  )
}
