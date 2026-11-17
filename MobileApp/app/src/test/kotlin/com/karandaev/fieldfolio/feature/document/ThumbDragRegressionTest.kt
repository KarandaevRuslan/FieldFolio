package com.karandaev.fieldfolio.feature.document

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.ui.scroll.*
import com.karandaev.fieldfolio.core.ui.text.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(
  sdk = [35],
  application = android.app.Application::class,
  qualifiers = "en-rUS-w600dp-h420dp-land-mdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbDragRegressionTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun drag(position: () -> Double, canForward: () -> Boolean) {
    val node = compose.onNodeWithTag("thumb-container")
    val bounds = node.fetchSemanticsNode().boundsInRoot
    val x = bounds.width - 14f
    node.performTouchInput { down(Offset(x, 28f)) }
    var previous = position()
    for (y in (40..190 step 10)) {
      node.performTouchInput { moveTo(Offset(x, y.toFloat()), delayMillis = 24) }
      compose.mainClock.advanceTimeByFrame()
      compose.waitForIdle()
      val current = position()
      assertTrue(
        "Content reversed during downward thumb drag: $previous -> $current",
        current + 1 >= previous,
      )
      previous = current
    }
    assertTrue("Thumb drag did not move content", previous > 100)
    for (y in (180 downTo 70 step 10)) {
      node.performTouchInput { moveTo(Offset(x, y.toFloat()), delayMillis = 24) }
      compose.mainClock.advanceTimeByFrame()
      compose.waitForIdle()
      val current = position()
      assertTrue(
        "Content reversed during upward thumb drag: $previous -> $current",
        current <= previous + 1,
      )
      previous = current
    }
    node.performTouchInput { up() }
    compose.waitForIdle()
    assertEquals(
      "Releasing the thumb must not start corrective item jumps",
      previous,
      position(),
      1.0,
    )
    val progress = node.fetchSemanticsNode().config[ScrollIndicatorProgress]
    val travel = bounds.height - 8f - 48f
    node.performTouchInput {
      down(Offset(x, 4f + travel * progress + 24f))
      moveTo(Offset(x, bounds.height - 1f), delayMillis = 400)
    }
    compose.waitForIdle()
    assertFalse("Dragging to the end must reach the real end, including tall cells", canForward())
    node.performTouchInput { moveTo(Offset(x, 1f), delayMillis = 400) }
    compose.waitForIdle()
    assertTrue("Dragging to the start must reach the actual start", position() < 1.1)
    node.performTouchInput { up() }
    compose.waitForIdle()
    node.performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
    compose.waitForIdle()
    assertFalse("Accessible progress must reach the actual end too", canForward())
  }

  @Test
  fun listThumbDoesNotOscillateAcrossUnequalRows() {
    lateinit var state: androidx.compose.foundation.lazy.LazyListState
    val heights = (0..149).map { if (it == 149) 700 else 40 + it * 37 % 180 }
    val tops = heights.runningFold(0) { a, b -> a + b + 8 }
    compose.setContent {
      MaterialTheme {
        state = rememberLazyListState()
        AppLazyColumn(
          Modifier.height(300.dp).fillMaxWidth().testTag("thumb-container"),
          state = state,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(heights.size) { Text("Row $it", Modifier.height(heights[it].dp)) }
        }
      }
    }
    drag(
      { tops[state.firstVisibleItemIndex].toDouble() + state.firstVisibleItemScrollOffset },
      { state.canScrollForward },
    )
  }

  @Test
  fun gridThumbDoesNotOscillateAcrossUnequalRows() {
    lateinit var state: androidx.compose.foundation.lazy.grid.LazyGridState
    val heights = (0..149).map { if (it == 149) 700 else 40 + it * 37 % 180 }
    val tops = heights.chunked(2).map { it.max() }.runningFold(0) { a, b -> a + b + 8 }
    compose.setContent {
      MaterialTheme {
        state = rememberLazyGridState()
        AppLazyGrid(
          2,
          Modifier.height(300.dp).fillMaxWidth().testTag("thumb-container"),
          state = state,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(heights.size) { Text("Row $it", Modifier.height(heights[it].dp)) }
        }
      }
    }
    drag(
      { tops[state.firstVisibleItemIndex / 2].toDouble() + state.firstVisibleItemScrollOffset },
      { state.canScrollForward },
    )
  }

  @Test
  fun masonryThumbUsesTheSamePixelDragWithoutHidingContent() {
    lateinit var state: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
    val heights = (0..149).map { if (it == 149) 700 else 40 + it * 37 % 280 }
    val lanes = intArrayOf(0, 0)
    val tops =
      heights.map { h ->
        val lane = if (lanes[0] <= lanes[1]) 0 else 1
        val top = lanes[lane]
        lanes[lane] += h + 12
        top
      }
    compose.setContent {
      MaterialTheme {
        state = rememberLazyStaggeredGridState()
        AppLazyStaggeredGrid(
          2,
          Modifier.height(300.dp).fillMaxWidth().testTag("thumb-container"),
          state = state,
        ) {
          items(heights.size) { Text("Cell $it", Modifier.height(heights[it].dp)) }
        }
      }
    }
    drag(
      { tops[state.firstVisibleItemIndex].toDouble() + state.firstVisibleItemScrollOffset },
      { state.canScrollForward },
    )
  }

  @Test
  fun masonryCanFinishATallEarlierLaneAfterTheLastIndexHasAlreadyEnded() {
    lateinit var state: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
    compose.setContent {
      MaterialTheme {
        state = rememberLazyStaggeredGridState()
        AppLazyStaggeredGrid(
          2,
          Modifier.height(300.dp).fillMaxWidth().testTag("thumb-container"),
          state,
        ) {
          items(4) { Text("Cell $it", Modifier.height(if (it == 0) 2200.dp else 40.dp)) }
        }
      }
    }
    val node = compose.onNodeWithTag("thumb-container")
    val bounds = node.fetchSemanticsNode().boundsInRoot
    node.performTouchInput {
      down(Offset(bounds.width - 14f, 28f))
      moveTo(Offset(bounds.width - 14f, bounds.height - 1f), delayMillis = 400)
    }
    compose.waitForIdle()
    assertFalse(
      "The oldest tall cell in the other lane must also be fully scrollable",
      state.canScrollForward,
    )
    node.performTouchInput { up() }
  }
}
