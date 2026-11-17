package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusFixAgeTest {
  @Test fun oldFixAgeChangesAtNextMinuteEvenWithoutExternalGpsEvent() {
    val fixed = 1_000_000L
    assertEquals(5L, fixAgeSeconds(fixed, fixed + 5 * 60_000L + 59_000L) / 60)
    assertEquals(1_000L, nextFixAgeTickDelayMillis(fixed, fixed + 5 * 60_000L + 59_000L, stale = true))
    assertEquals(6L, fixAgeSeconds(fixed, fixed + 6 * 60_000L) / 60)
  }

  @Test fun recentFixUpdatesEverySecond() {
    val fixed = 1_000_000L
    assertEquals(3L, fixAgeSeconds(fixed, fixed + 3_450L))
    assertEquals(550L, nextFixAgeTickDelayMillis(fixed, fixed + 3_450L, stale = false))
  }

  @Test fun futureFixAgeDoesNotGoNegative() {
    val fixed = 1_000_000L
    assertEquals(0L, fixAgeSeconds(fixed, fixed - 2_000L))
    assertEquals(60_000L, nextFixAgeTickDelayMillis(fixed, fixed - 2_000L, stale = true))
  }
}
