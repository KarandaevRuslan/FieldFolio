package com.karandaev.fieldfolio.feature.document

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.map.AdaptiveOsmMap
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.DocumentPointPicker
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.osmdroid.views.MapView
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapContainmentTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun nativeMapClipsToItsOwnFrameBelowHeader() {
    var map: MapView? = null
    compose.setContent {
      MaterialTheme {
        Column {
          Text("Header")
          AdaptiveOsmMap(
            emptyList(),
            Modifier.fillMaxWidth().height(180.dp),
            mapRef = {
              it.setUseDataConnection(false)
              map = it
            },
          )
          Text("Footer")
        }
      }
    }
    compose.waitForIdle()
    val frame = compose.onNodeWithTag("native-map-frame").fetchSemanticsNode().boundsInRoot
    assertTrue(
      compose.onNodeWithText("Header").fetchSemanticsNode().boundsInRoot.bottom <= frame.top
    )
    assertTrue(
      frame.bottom <= compose.onNodeWithText("Footer").fetchSemanticsNode().boundsInRoot.top
    )
    compose.runOnIdle {
      val parent = map!!.parent as FrameLayout
      assertTrue(parent.clipChildren)
      assertTrue(parent.clipToPadding)
      assertEquals(180, parent.height)
      assertEquals(parent.height, map!!.height)
    }
  }

  private fun picker() {
    compose.setContent { MaterialTheme { DocumentPointPicker(MapPoint(10.0, 20.0), {}, {}) } }
    val map = compose.onNodeWithTag("native-map-frame").fetchSemanticsNode().boundsInRoot
    assertTrue(
      compose.onNodeWithText("Choose on map").fetchSemanticsNode().boundsInRoot.bottom <= map.top
    )
    assertTrue(
      compose.onNodeWithContentDescription("Select").fetchSemanticsNode().boundsInRoot.bottom <=
        map.top
    )
    compose.onNodeWithContentDescription("Map center and points").performClick()
    compose.onNodeWithText("Current map center").assertExists()
  }

  @Test
  fun portraitMapPickerKeepsHeaderOutsideNativeView() {
    picker()
  }

  @Test
  @Config(qualifiers = "en-rUS-w900dp-h420dp-land-mdpi")
  fun landscapeMapPickerKeepsHeaderOutsideNativeView() {
    picker()
  }
}
