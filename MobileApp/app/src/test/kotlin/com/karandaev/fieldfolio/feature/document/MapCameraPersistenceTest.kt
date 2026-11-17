package com.karandaev.fieldfolio.feature.document

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.map.AdaptiveOsmMap
import com.karandaev.fieldfolio.core.ui.components.map.camera.OsmMapCameraConfig
import com.karandaev.fieldfolio.core.ui.components.map.controls.model.OsmMapControls
import com.karandaev.fieldfolio.core.ui.text.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapCameraPersistenceTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun scrollingMapOutOfCompositionRestoresUserCameraInsteadOfTarget() {
    var latest: MapView? = null
    var focusClicks = 0
    compose.setContent {
      MaterialTheme {
        LazyColumn(Modifier.fillMaxSize().testTag("list")) {
          item("map") {
            AdaptiveOsmMap(
              emptyList(),
              Modifier.fillMaxWidth().height(180.dp),
              camera = OsmMapCameraConfig(MapPoint(10.0, 20.0)),
              focusTarget = MapPoint(10.0, 20.0),
              controls = OsmMapControls(onFocusClick = { focusClicks++ }),
              mapRef = {
                it.setUseDataConnection(false)
                latest = it
              },
            )
          }
          items(40) { Text("Item $it", Modifier.fillMaxWidth().height(70.dp)) }
        }
      }
    }
    repeat(6) { iteration ->
      val latitude = 33.5 + iteration * .1
      val longitude = 42.75 + iteration * .1
      compose.runOnIdle {
        latest!!.controller.setZoom(14.0)
        latest!!.controller.setCenter(GeoPoint(latitude, longitude))
      }
      compose.waitForIdle()
      val old = latest!!
      compose.onNodeWithTag("list").performScrollToIndex(30)
      compose.onNodeWithTag("native-map-frame").assertDoesNotExist()
      compose.onNodeWithTag("list").performScrollToIndex(0)
      compose.waitForIdle()
      assertNotSame(old, latest)
      assertEquals(latitude, latest!!.mapCenter.latitude, .001)
      assertEquals(longitude, latest!!.mapCenter.longitude, .001)
      assertEquals(14.0, latest!!.zoomLevelDouble, .01)
    }
    val mapBounds = compose.onNodeWithTag("native-map-frame").fetchSemanticsNode().boundsInRoot
    val focusBounds =
      compose.onNodeWithContentDescription("Go to focus target").fetchSemanticsNode().boundsInRoot
    assertTrue(focusBounds.left > mapBounds.center.x)
    assertTrue(focusBounds.top > mapBounds.center.y)
    compose.onNodeWithContentDescription("Go to focus target").performTouchInput { longClick() }
    compose.onNodeWithText("Current map center").assertDoesNotExist()
    compose.onNodeWithContentDescription("Go to focus target").performClick()
    assertEquals(1, focusClicks)
  }
}
