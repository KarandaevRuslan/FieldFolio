package com.karandaev.fieldfolio.feature.document

import android.graphics.*
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.google.firebase.*
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.image.crop.*
import com.karandaev.fieldfolio.core.inspectionapi.InspectionProvider
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.scroll.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.AudioPropertiesDialog
import com.karandaev.fieldfolio.feature.document.editor.ClockPropertiesDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentAddDialog
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.viewer.DocumentAudio
import com.karandaev.fieldfolio.feature.media.*
import com.karandaev.fieldfolio.feature.models.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(
  sdk = [35],
  application = MyApplication::class,
  qualifiers = "en-rUS-w900dp-h420dp-land-mdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaRefinementUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val app
    get() = compose.activity.application as MyApplication

  @Before
  fun setup() {
    if (FirebaseApp.getApps(app).isEmpty())
      FirebaseApp.initializeApp(
        app,
        FirebaseOptions.Builder()
          .setApplicationId("1:123:android:test")
          .setApiKey("test-key")
          .setProjectId("test")
          .build(),
      )
  }

  private fun image() =
    ByteArrayOutputStream()
      .also { out ->
        Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
          .apply { eraseColor(Color.RED) }
          .let {
            it.compress(Bitmap.CompressFormat.PNG, 100, out)
            it.recycle()
          }
      }
      .toByteArray()

  private fun waitUntil(condition: () -> Boolean) =
    compose.waitUntil(15000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      condition()
    }

  private fun screenshot(name: String) {
    compose.waitForIdle()
    compose.runOnIdle {
      val view =
        org.robolectric.shadows.ShadowDialog.getShownDialogs()
          .lastOrNull { it.isShowing }
          ?.window
          ?.decorView ?: compose.activity.window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      view.draw(Canvas(bitmap))
      val dir = java.io.File("build/media-previews").apply { mkdirs() }
      java.io.File(dir, "$name.png").outputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
      }
      bitmap.recycle()
    }
  }

  @Test
  @Config(qualifiers = "en-rUS-w900dp-h300dp-land-mdpi")
  fun allBlockTypesAreReachableInShortLandscape() {
    var selected: BlockKind? = null
    compose.setContent { MaterialTheme { DocumentAddDialog({}) { kind, _ -> selected = kind } } }
    compose.onNodeWithText("Model result").performScrollTo()
    compose.onNodeWithText("Model result").assertIsDisplayed().performClick()
    assertEquals(BlockKind.MODEL_RESULT, selected)
    compose.onNodeWithContentDescription("Close").assertIsDisplayed()
  }

  @Test
  fun modelPickerHasARealNavigationBarAndAnIconClose() {
    compose.setContent { MaterialTheme { ModelPickerDialog({}) {} } }
    compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    compose.onAllNodesWithText("Close").assertCountEquals(0)
    screenshot("model-picker")
  }

  @Test
  fun sharedCoverEditorUsesLandscapeImagePaneAndVfsInput() {
    val bytes = image()
    val node =
      DocumentNode(name = "Existing image", kind = BlockKind.IMAGE, asset = "resources/a.png")
    compose.setContent {
      MaterialTheme {
        ReportImageInputDialog(
          "test-cover",
          "Change cover",
          {},
          initialBytes = bytes,
          initialName = "cover.png",
          document = ReportDocument(nodes = listOf(node)),
          files = mapOf(node.asset!! to bytes),
          trimBorders = true,
        ) { _, _ ->
          true
        }
      }
    }
    waitUntil {
      compose
        .onAllNodes(hasContentDescription("Crop image") and isEnabled())
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    val imagePane = compose.onNodeWithTag("media-image-pane").fetchSemanticsNode().boundsInRoot
    val controlPane = compose.onNodeWithTag("media-controls-pane").fetchSemanticsNode().boundsInRoot
    assertTrue(imagePane.left >= controlPane.right)
    compose.onNodeWithContentDescription("Choose an image block").performClick()
    compose.onNodeWithText("Existing image").assertIsDisplayed().performClick()
    waitUntil {
      compose
        .onAllNodes(hasSetTextAction() and hasText("Existing image"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    assertTrue(bytes.any { it != 0.toByte() })
    screenshot("shared-cover-editor")
  }

  @Test
  fun choosingClockPointResolvesItsZoneWithoutAnotherClick() {
    var saved: DocumentNode? = null
    var requested = 0
    compose.setContent {
      MaterialTheme {
        ClockPropertiesDialog(
          DocumentNode(
            name = "Clock",
            kind = BlockKind.CLOCK,
            latitude = 51.5,
            longitude = -.1,
            zoneId = "UTC",
          ),
          {},
          resolveZone = {
            requested++
            "Europe/London"
          },
        ) { node, _ ->
          saved = node
        }
      }
    }
    compose.onNodeWithContentDescription("Choose on map").performScrollTo().performClick()
    compose.onNodeWithContentDescription("Select").performClick()
    waitUntil {
      compose
        .onAllNodes(hasSetTextAction() and hasText("Europe/London"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    compose.onNodeWithContentDescription("Save").performClick()
    assertEquals(1, requested)
    assertEquals("Europe/London", saved!!.zoneId)
    screenshot("clock-landscape")
  }

  @Test
  fun audioMetadataIsCollapsedAndCoverIsSeparateLandscapePane() {
    val node =
      DocumentNode(
        name = "song.m4a",
        kind = BlockKind.AUDIO,
        audio = AudioPresentation(coverAsset = "cover.png", overrides = mapOf("artist" to "Artist")),
      )
    compose.setContent {
      MaterialTheme { AudioPropertiesDialog(node, mapOf("cover.png" to image()), {}) { _, _ -> } }
    }
    compose.onNode(hasSetTextAction() and hasText("Artist")).assertDoesNotExist()
    compose
      .onNodeWithContentDescription("Expand or collapse metadata")
      .performScrollTo()
      .performClick()
    compose.onNode(hasSetTextAction() and hasText("Artist")).assertExists()
    val pane = compose.onNodeWithTag("audio-cover-pane").fetchSemanticsNode().boundsInRoot
    assertTrue(pane.width > 250f && pane.height > 220f)
    screenshot("audio-properties")
    compose.onNodeWithTag("audio-show-tags").performScrollTo().performClick()
    compose.onNodeWithTag("audio-tag-artist").assertIsNotEnabled()
    compose.onNodeWithTag("audio-show-cover").performScrollTo().performClick()
    screenshot("audio-properties-disabled")
    compose.onNodeWithContentDescription("Change cover").assertIsDisplayed().performClick()
    compose.onNodeWithContentDescription("Trim uniform background").assertExists()
    screenshot("audio-cover-editor")
  }

  @Test
  fun croppingAfterRunReprojectsResultsRestoresLastRunAndNewSourceInvalidates() {
    var calls = 0
    val adapter =
      object : ServerModelAdapter {
        override val id = "media-test"
        override val name = "Test"
        override val task = "DETECTION"

        override suspend fun analyze(
          provider: InspectionProvider,
          image: ByteArray?,
          text: String,
        ): ModelResultData {
          calls++
          return ModelResultData(
            modelId = id,
            modelName = name,
            provider = "SERVER",
            task = task,
            boxes = listOf(ResultBox("box", .9, .25, .25, .75, .75)),
          )
        }
      }
    app.catalogModelRunner.serverModels.register(adapter)
    val vm = AnalysisViewModel(app.catalogModelRunner)
    val choice = ModelChoice(adapter.id, adapter.name, adapter.task, server = adapter)
    compose.setContent { MaterialTheme { Text("Test") } }
    compose.runOnIdle {
      vm.image(image())
      vm.run(choice)
    }
    waitUntil { !vm.busy }
    assertNotNull(vm.result)
    compose.runOnIdle { vm.crop(NormalizedCropBox(.25, 0.0, .75, 1.0)) }
    waitUntil { !vm.busy }
    assertEquals(1, calls)
    assertEquals(0.0, vm.result!!.result.boxes.single().xMin, .00001)
    compose.runOnIdle { vm.run(choice) }
    waitUntil { !vm.busy }
    assertEquals(2, calls)
    compose.runOnIdle { vm.restoreSource() }
    waitUntil { !vm.busy }
    assertEquals(.375, vm.result!!.result.boxes.single().xMin, .00001)
    compose.runOnIdle { vm.restoreRun() }
    waitUntil { !vm.busy }
    assertEquals(.25, vm.frames.bounds.xMin, .00001)
    assertEquals(.25, vm.result!!.result.boxes.single().xMin, .00001)
    compose.runOnIdle { vm.image(image()) }
    assertNull(vm.result)
    assertEquals(2, calls)
    compose.runOnIdle { vm.clear() }
  }

  @Test
  fun contentScrollingMovesIndicatorInBothDirectionsWithoutIndexSteps() {
    lateinit var state: androidx.compose.foundation.lazy.LazyListState
    compose.setContent {
      MaterialTheme {
        state = rememberLazyListState()
        AppLazyColumn(Modifier.height(260.dp).fillMaxWidth().testTag("list"), state = state) {
          items(40) { index -> Text("Item $index", Modifier.height((35 + index * 23 % 140).dp)) }
        }
      }
    }
    fun progress() =
      compose.onNodeWithTag("list").fetchSemanticsNode().config[ScrollIndicatorProgress]
    var previous = progress()
    var changed = 0
    repeat(90) {
      compose.runOnIdle { runBlocking { state.scroll { scrollBy(4f) } } }
      val current = progress()
      assertTrue("Reverse movement down", current >= previous - .00001f)
      if (current > previous) changed++
      previous = current
    }
    assertTrue("Indicator only updates at item boundaries", changed > 75)
    repeat(90) {
      compose.runOnIdle { runBlocking { state.scroll { scrollBy(-4f) } } }
      val current = progress()
      assertTrue("Reverse movement up", current <= previous + .00001f)
      previous = current
    }
  }

  @Test
  fun resultCanBeCroppedThroughTheToolbarAfterRun() {
    val adapter =
      object : ServerModelAdapter {
        override val id = "media-toolbar-test"
        override val name = "Crop test"
        override val task = "DETECTION"

        override suspend fun analyze(
          provider: InspectionProvider,
          image: ByteArray?,
          text: String,
        ) =
          ModelResultData(
            modelId = id,
            modelName = name,
            provider = "SERVER",
            task = task,
            boxes = listOf(ResultBox("Target", .9, .2, .2, .8, .8)),
          )
      }
    app.catalogModelRunner.serverModels.register(adapter)
    val bytes = image()
    compose.setContent {
      MaterialTheme {
        AnalysisWorkspace(
          ModelChoice(adapter.id, adapter.name, adapter.task, server = adapter),
          {},
          initialImage = bytes,
        )
      }
    }
    waitUntil {
      compose
        .onAllNodes(hasContentDescription("Run model") and isEnabled())
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    compose.onNodeWithContentDescription("Run model").performClick()
    val vm =
      androidx.lifecycle
        .ViewModelProvider(
          compose.activity,
          androidx.lifecycle.ViewModelProvider.NewInstanceFactory(),
        )["analysis-true-media-toolbar-test", AnalysisViewModel::class.java]
    waitUntil { vm.result != null && !vm.busy }
    compose.onNodeWithContentDescription("Crop image").assertIsEnabled().performClick()
    waitUntil {
      compose
        .onAllNodes(hasContentDescription("Apply") and isEnabled())
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    compose.onNodeWithText("1:1").performClick()
    compose.onNodeWithContentDescription("Apply").performClick()
    waitUntil { !vm.busy && vm.frames.bounds != FullImageBounds }
    assertNotNull(vm.result)
    val current = vm.image!!
    val dimensions = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(current, 0, current.size, dimensions)
    assertEquals("Square crop must use the chosen ratio", dimensions.outWidth, dimensions.outHeight)
    waitUntil {
      compose
        .onAllNodesWithTag(
          "rendered-image-${dimensions.outWidth}x${dimensions.outHeight}",
          useUnmergedTree = true,
        )
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    screenshot("analysis-after-crop")
  }

  @Test
  fun scrollbarReleaseMatchesTheSoughtPositionForUnequalRows() {
    compose.setContent {
      MaterialTheme {
        AppLazyColumn(Modifier.height(260.dp).fillMaxWidth().testTag("list")) {
          items(50) { index -> Text("Item $index", Modifier.height((40 + index * 51 % 180).dp)) }
        }
      }
    }
    compose.onNodeWithTag("list").performSemanticsAction(
      androidx.compose.ui.semantics.SemanticsActions.SetProgress
    ) {
      it(.7f)
    }
    compose.waitForIdle()
    val actual = compose.onNodeWithTag("list").fetchSemanticsNode().config[ScrollIndicatorProgress]
    assertEquals("Thumb jumps after releasing it", .7f, actual, .015f)
  }

  @Test
  fun playerContainsSquareClickableCoverAndCompactMetadata() {
    val source =
      java.nio.ByteBuffer.allocate(44 + 1600)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        .apply {
          put("RIFF".toByteArray())
          putInt(36 + 1600)
          put("WAVEfmt ".toByteArray())
          putInt(16)
          putShort(1)
          putShort(1)
          putInt(8000)
          putInt(16000)
          putShort(2)
          putShort(16)
          put("data".toByteArray())
          putInt(1600)
          repeat(800) { putShort(0) }
        }
        .array()
    org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider {
      org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(120000, 0)
    }
    val session = DocumentAudioSession()
    val cover = image()
    compose.setContent {
      MaterialTheme {
        Column(Modifier.width(380.dp)) {
          DocumentAudio(
            source,
            session,
            "cover-test",
            AudioPresentation(
              coverAsset = "cover.png",
              overrides =
                mapOf(
                  "title" to "A song with a long title",
                  "artist" to "Artist",
                  "album" to "Album with a long long long name",
                  "year" to "2026",
                  "genre" to "Electronic",
                  "track" to "4/10",
                ),
            ),
            mapOf("cover.png" to cover),
          )
        }
      }
    }
    waitUntil { compose.onAllNodesWithTag("audio-player-cover").fetchSemanticsNodes().isNotEmpty() }
    val art = compose.onNodeWithTag("audio-player-cover").fetchSemanticsNode().boundsInRoot
    val player = compose.onNodeWithTag("audio-player").fetchSemanticsNode().boundsInRoot
    assertEquals(art.width, art.height, .1f)
    assertTrue(
      art.left >= player.left &&
        art.top >= player.top &&
        art.right <= player.right &&
        art.bottom <= player.bottom
    )
    compose.onNodeWithText("A song with a long title").assertIsDisplayed()
    screenshot("audio-player")
    compose.onNodeWithTag("audio-player-cover").performClick()
    compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    compose.runOnIdle { session.close() }
  }
}
