package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.component

/** Pure time helpers; the UI tick itself runs only while a focus-menu row is composed. */
internal fun fixAgeSeconds(measuredAtMillis: Long, nowMillis: Long): Long =
  ((nowMillis - measuredAtMillis).coerceAtLeast(0L) / 1_000L)

/** Wake up at the next visible age change: once/second for a fresh fix, once/minute for old. */
internal fun nextFixAgeTickDelayMillis(
  measuredAtMillis: Long,
  nowMillis: Long,
  stale: Boolean,
): Long {
  val unit = if (stale) 60_000L else 1_000L
  val age = (nowMillis - measuredAtMillis).coerceAtLeast(0L)
  return (unit - age % unit).coerceAtLeast(1L)
}
