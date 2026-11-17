package com.karandaev.fieldfolio.feature.document

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.viewer.AudioPlayerView
import com.karandaev.fieldfolio.feature.document.viewer.DocumentBlockBody
import com.karandaev.fieldfolio.feature.document.viewer.DocumentText
import com.karandaev.fieldfolio.feature.document.viewer.ImageResultDialog
import com.karandaev.fieldfolio.feature.models.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(
  sdk = [35],
  application = MyApplication::class,
  qualifiers = "en-rUS-w412dp-h915dp-port-mdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FollowupUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @After
  fun reset() {
    DocumentTextPreferences.preferences(compose.activity).edit().clear().commit()
  }

  @Test
  fun waveformSeeksContinuouslyBeforeFingerReleaseAndStepsExactlyFiveMilliseconds() {
    var position by mutableIntStateOf(1000)
    val seeks = mutableListOf<Int>()
    compose.setContent {
      MaterialTheme {
        AudioPlayerView(
          10000,
          position,
          false,
          true,
          false,
          floatArrayOf(.1f, .8f, .2f),
          {
            position = seekPosition(it, 10000)
            seeks += position
          },
          {},
          {},
        )
      }
    }
    compose.onNodeWithContentDescription("Forward 5 seconds (hold to repeat)").performClick()
    assertEquals(6000, position)
    compose.onNodeWithContentDescription("Back 5 seconds (hold to repeat)").performClick()
    assertEquals(1000, position)
    compose.onNodeWithTag("audio-waveform").performTouchInput {
      down(Offset(width * .1f, centerY))
      moveTo(Offset(width * .6f, centerY), 100)
    }
    assertTrue("Seeking must happen while the finger is still down", seeks.last() > 5000)
    compose.onNodeWithTag("audio-waveform").performTouchInput {
      moveTo(Offset(width * .8f, centerY), 100)
      up()
    }
    assertTrue(kotlin.math.abs(position - 8000) <= 2)
  }

  @Test
  fun holdingSeekRepeatsAndStopsWhenReleased() {
    var position by mutableIntStateOf(1000)
    compose.setContent {
      MaterialTheme {
        AudioPlayerView(
          10000,
          position,
          false,
          true,
          false,
          floatArrayOf(.5f),
          { position = seekPosition(it, 10000) },
          {},
          {},
        )
      }
    }
    compose.mainClock.autoAdvance = false
    compose.onNodeWithContentDescription("Forward 5 seconds (hold to repeat)").performTouchInput {
      down(center)
    }
    compose.mainClock.advanceTimeBy(750)
    val held = position
    assertTrue("Hold must repeat more than one 5 second step", held > 1005)
    compose.onNodeWithContentDescription("Forward 5 seconds (hold to repeat)").performTouchInput {
      up()
    }
    compose.mainClock.advanceTimeBy(500)
    assertEquals(held, position)
    compose.mainClock.autoAdvance = true
  }

  @Test
  fun sharedTextLimitBoundsTheActualPreviewAndChangesPageCount() {
    val source = "X".repeat(1200)
    DocumentTextPreferences.set(compose.activity, 500)
    compose.setContent { MaterialTheme { Column { DocumentText(source, "Text resource") } } }
    compose.onNodeWithText(source).assertDoesNotExist()
    compose.onNodeWithTag("collapsed-document-text").assertTextEquals("X".repeat(500) + "…")
    compose.onNodeWithContentDescription("View").performClick()
    compose.onNodeWithTag("document-text-page-count").assertTextEquals("1 / 3")
    compose.onNodeWithContentDescription("Next page").performClick()
    compose.onNodeWithTag("document-text-page").assertTextEquals("X".repeat(500))
    compose.runOnIdle { DocumentTextPreferences.set(compose.activity, 1000) }
    compose.onNodeWithTag("document-text-page-count").assertTextEquals("2 / 2")
    compose.onNodeWithTag("document-text-page").assertTextEquals("X".repeat(200))
  }

  @Test
  fun modelContextMenuUsesTextActionsAndClosesAfterSelection() {
    var exported = false
    val local = LocalModel("m", "Model", ModelAdapter.CLASSIFICATION)
    compose.setContent {
      MaterialTheme {
        ModelCatalogBrowser(
          catalogChoices(listOf(local), emptyList()),
          {},
          management = true,
          actions = { listOf(ModelMenuAction("Export") { exported = true }) },
        )
      }
    }
    compose.onNodeWithContentDescription("Actions").performClick()
    compose.onNodeWithText("Export").assertIsDisplayed()
    compose.onNodeWithContentDescription("Export").assertDoesNotExist()
    compose.onNodeWithText("Export").performClick()
    assertTrue(exported)
    compose.onNodeWithText("Export").assertDoesNotExist()
  }

  @Test
  fun imageViewerContainsAnalysisBelowImageWithoutNestedDetailsActions() {
    val bitmap =
      android.graphics.Bitmap.createBitmap(40, 20, android.graphics.Bitmap.Config.ARGB_8888)
    val bytes =
      java.io
        .ByteArrayOutputStream()
        .also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        .toByteArray()
    bitmap.recycle()
    val result =
      ModelResultData(
        modelId = "m",
        modelName = "Road",
        provider = "SERVER",
        task = "DETECTION",
        boxes = listOf(ResultBox("Crack", .8, .1, .1, .6, .8)),
      )
    compose.setContent { MaterialTheme { ImageResultDialog(bytes, result, null, dismiss = {}) } }
    compose.onNodeWithContentDescription("Analysis details").assertDoesNotExist()
    compose.onNodeWithTag("analysis-details-window").assertDoesNotExist()
    compose.onNodeWithTag("analysis-details-content").performScrollTo().assertExists()
    compose.onNodeWithText("40 × 20 px").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Boxes").performScrollTo().assertIsDisplayed()
    compose.onAllNodesWithContentDescription("Close").assertCountEquals(1)
  }

  @Test
  fun editorLocationPreviewIsLargeAndViewerMapRemainsCompact() {
    var editing by mutableStateOf(false)
    val node =
      DocumentNode(
        id = "point",
        name = "Point",
        kind = BlockKind.LOCATION,
        deviceLocation = false,
        latitude = 10.0,
        longitude = 20.0,
      )
    compose.setContent {
      MaterialTheme {
        Column {
          DocumentBlockBody(
            node,
            emptyMap(),
            null,
            0,
            DocumentAudioSession(),
            {},
            editorPreview = editing,
          )
        }
      }
    }
    val small = compose.onNodeWithTag("native-map-frame").fetchSemanticsNode().boundsInRoot.height
    assertEquals(180f, small, 1f)
    compose.runOnIdle { editing = true }
    assertTrue(
      compose.onNodeWithTag("native-map-frame").fetchSemanticsNode().boundsInRoot.height > small * 2
    )
    compose.onNodeWithContentDescription("Go to focus target").assertExists()
  }
}
