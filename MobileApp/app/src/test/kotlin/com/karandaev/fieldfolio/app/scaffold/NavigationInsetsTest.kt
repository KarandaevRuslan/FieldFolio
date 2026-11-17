package com.karandaev.fieldfolio.app.scaffold

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.karandaev.fieldfolio.app.navigation.AppDestination
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w900dp-h420dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationInsetsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun bounds(tag: String): Rect {
    val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    return Rect(
      node.positionOnScreen.x,
      node.positionOnScreen.y,
      node.positionOnScreen.x + node.size.width,
      node.positionOnScreen.y + node.size.height,
    )
  }

  @Test
  fun onlyTheSideOccupiedBySystemNavigationIsReserved() {
    compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
    compose.setContent {
      MaterialTheme {
        MapNotesTwoPaneScaffold(
          AppDestination("home", "Reports", false, false, true, false, true),
          {},
          {},
        ) { modifier ->
          Box(modifier.testTag("app-content"))
        }
      }
    }
    for ((left, right, cutoutLeft, cutoutRight) in
      listOf(listOf(0, 48, 80, 0), listOf(48, 0, 0, 80))) {
      compose.runOnIdle {
        val insets =
          WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
            .setVisible(WindowInsetsCompat.Type.statusBars(), true)
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(left, 0, right, 0))
            .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
            .setInsets(
              WindowInsetsCompat.Type.displayCutout(),
              Insets.of(cutoutLeft, 0, cutoutRight, 0),
            )
            .build()
        ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets)
      }
      compose.waitForIdle()
      val rail = bounds("app-navigation-rail")
      val content = bounds("app-content")
      assertEquals(left.toFloat(), rail.left, 1f)
      assertEquals("Unexpected opposite-side inset", 80f, rail.width, 1f)
      assertEquals(rail.right, content.left, 1f)
      assertEquals(900f - right, content.right, 1f)
      assertEquals(24f, content.top, 1f)
      assertEquals(420f, content.bottom, 1f)
    }
  }
}
