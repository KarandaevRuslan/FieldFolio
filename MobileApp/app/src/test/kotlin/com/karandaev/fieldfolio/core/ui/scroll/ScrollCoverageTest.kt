package com.karandaev.fieldfolio.core.ui.scroll

import org.junit.Assert.*
import org.junit.Test

class ScrollCoverageTest {
  /** Simulate the lazy layout reporting only the items crossing the viewport. */
  private fun trace(
    placements: List<Pair<Int, Int>>,
    viewport: Int,
    gap: Int = 0,
  ): List<ScrollWindow> {
    val extent = placements.maxOf { it.first + it.second }
    return (0..extent - viewport).map { pixel ->
      val items =
        placements.mapIndexedNotNull { index, (top, size) ->
          if (top + size > pixel && top < pixel + viewport)
            ScrollItemRect(index, (top - pixel).toFloat(), size.toFloat())
          else null
        }
      coverageWindow(
        items,
        placements.size,
        0f,
        viewport.toFloat(),
        gap.toFloat(),
        pixel > 0,
        pixel < extent - viewport,
      )
    }
  }

  private fun stable(trace: List<ScrollWindow>) {
    assertEquals(0f, trace.first().progress, 0f)
    assertEquals(1f, trace.last().progress, 0f)
    trace.zipWithNext().forEachIndexed { pixel, (a, b) ->
      assertTrue(
        "Scrollbar reverses at pixel $pixel: ${a.progress} -> ${b.progress}",
        b.progress + 0.000001f >= a.progress,
      )
      assertTrue("Scrollbar jumps at pixel $pixel", b.progress - a.progress < .025f)
      assertEquals("Lazy thumb changes size", a.visibleFraction, b.visibleFraction, 0f)
    }
    trace.asReversed().zipWithNext().forEach { (a, b) ->
      assertTrue(b.progress <= a.progress + .000001f)
    }
  }

  @Test
  fun unequalListRowsMoveContinuouslyInBothDirections() {
    var at = 0
    stable(
      trace(
        (0..79).map {
          val height = 35 + (it * 79 % 290)
          Pair(at, height).also { at += height }
        },
        340,
      )
    )
  }

  @Test
  fun unequalRowsWithSpacingDoNotStepOrReverse() {
    var at = 0
    stable(
      trace(
        (0..59).map {
          val height = 40 + (it * 37 % 220)
          Pair(at, height).also { at += height + 12 }
        },
        340,
        12,
      )
    )
  }

  @Test
  fun masonryDoesNotBreatheOrReverseWhenTallCellsEnterAndExit() {
    val lanes = intArrayOf(0, 0)
    val cells =
      (0..59).map { index ->
        val lane = if (lanes[0] <= lanes[1]) 0 else 1
        val height = 64 + (index * 71 % 380)
        Pair(lanes[lane], height).also { lanes[lane] += height + 12 }
      }
    stable(trace(cells, 340, 12))
  }

  @Test
  fun shortContentHasNoThumb() {
    val w =
      coverageWindow(
        listOf(ScrollItemRect(0, 0f, 40f)),
        1,
        0f,
        400f,
        backward = false,
        forward = false,
      )
    assertFalse(w.scrollable)
  }
}
