@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.karandaev.fieldfolio.feature.document

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.ui.scroll.*
import com.karandaev.fieldfolio.core.ui.text.*
import kotlinx.coroutines.runBlocking
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
class NativeScrollContinuityTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun check(state: ScrollableState) {
    fun progress() =
      compose.onNodeWithTag("container").fetchSemanticsNode().config[ScrollIndicatorProgress]
    var previous = progress()
    repeat(160) {
      compose.runOnIdle { runBlocking { state.scroll { scrollBy(6f) } } }
      val now = progress()
      assertTrue("Indicator moves backwards down: $previous to $now", now + .00001f >= previous)
      assertTrue("Index jump", now - previous < .025f)
      previous = now
    }
    repeat(160) {
      compose.runOnIdle { runBlocking { state.scroll { scrollBy(-6f) } } }
      val now = progress()
      assertTrue(
        "Indicator moves down during upward content scrolling: $previous to $now",
        now <= previous + .00001f,
      )
      assertTrue("Index jump on return", previous - now < .025f)
      previous = now
    }
    assertEquals(0f, previous, .002f)
  }

  @Test
  fun pinnedToolbarUsesItsUnpinnedPixelOffset() {
    lateinit var state: androidx.compose.foundation.lazy.LazyListState
    compose.setContent {
      MaterialTheme {
        state = rememberLazyListState()
        AppLazyColumn(
          Modifier.height(300.dp).fillMaxWidth().testTag("container"),
          state = state,
          contentPadding = PaddingValues(top = 12.dp, bottom = 30.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          stickyHeader { Text("Path and toolbar", Modifier.fillMaxWidth().height(120.dp)) }
          items(60) { Text("Item $it", Modifier.height((40 + it * 37 % 160).dp)) }
        }
      }
    }
    check(state)
  }

  @Test
  fun unequalTwoColumnRowsScrollContinuously() {
    lateinit var state: androidx.compose.foundation.lazy.grid.LazyGridState
    compose.setContent {
      MaterialTheme {
        state = rememberLazyGridState()
        AppLazyGrid(
          2,
          Modifier.height(300.dp).fillMaxWidth().testTag("container"),
          state = state,
          contentPadding = PaddingValues(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(80) { Text("Item $it", Modifier.height((40 + it * 37 % 190).dp)) }
        }
      }
    }
    check(state)
  }

  @Test
  fun masonryHasNoReversalAsUnequalCardsEnterAndLeave() {
    lateinit var state: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
    compose.setContent {
      MaterialTheme {
        state = rememberLazyStaggeredGridState()
        AppLazyStaggeredGrid(
          2,
          Modifier.height(300.dp).fillMaxWidth().testTag("container"),
          state = state,
        ) {
          items(80) { Text("Item $it", Modifier.height((40 + it * 37 % 290).dp)) }
        }
      }
    }
    check(state)
  }
}
