package com.karandaev.fieldfolio.feature.document

import android.graphics.*
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.google.firebase.*
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.components.actions.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.AudioPropertiesDialog
import com.karandaev.fieldfolio.feature.document.route.DocumentRoute
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.viewer.DocumentAudio
import com.karandaev.fieldfolio.feature.document.viewer.DocumentText
import com.karandaev.fieldfolio.feature.document.viewer.TextFilePreview
import com.karandaev.fieldfolio.feature.media.ReportImageInputDialog
import com.karandaev.fieldfolio.feature.models.*
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
class FinishUiRegressionTest {
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

  private fun image(color: Int = Color.RED) =
    java.io
      .ByteArrayOutputStream()
      .also { out ->
        Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
          .apply { eraseColor(color) }
          .let {
            it.compress(Bitmap.CompressFormat.PNG, 100, out)
            it.recycle()
          }
      }
      .toByteArray()

  private fun waitUntil(test: () -> Boolean) =
    compose.waitUntil(15000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      test()
    }

  private fun screenshot(name: String) {
    compose.waitForIdle()
    compose.runOnIdle {
      val view =
        org.robolectric.shadows.ShadowDialog.getShownDialogs()
          .lastOrNull { it.isShowing }
          ?.window
          ?.decorView ?: compose.activity.window.decorView
      val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      view.draw(Canvas(b))
      val dir = java.io.File("build/finish-previews").apply { mkdirs() }
      java.io.File(dir, "$name.png").outputStream().use {
        b.compress(Bitmap.CompressFormat.PNG, 100, it)
      }
      b.recycle()
    }
  }

  @Test
  fun audioActionsAreDirectToolbarButtons() {
    compose.setContent {
      MaterialTheme {
        AudioPropertiesDialog(
          DocumentNode(
            name = "audio.mp3",
            kind = BlockKind.AUDIO,
            audio = AudioPresentation(coverAsset = "cover", overrides = mapOf("title" to "Track")),
          ),
          mapOf("cover" to image()),
          {},
        ) { _, _ ->
        }
      }
    }
    compose.onNodeWithContentDescription("Change cover").assertIsDisplayed()
    compose.onNodeWithContentDescription("Restore embedded cover").assertIsDisplayed()
    compose.onNodeWithContentDescription("Reset all overrides").assertIsDisplayed()
    compose.onNodeWithContentDescription("Cover and metadata actions").assertDoesNotExist()
    screenshot("audio-toolbar")
  }

  @Test
  fun sharedImageWorkspaceAllowsTappingTheImageAndRestoringEmbeddedArt() {
    var saved: ByteArray? = null
    val original = image()
    val embedded = image(Color.BLUE)
    compose.setContent {
      MaterialTheme {
        ReportImageInputDialog(
          "tap-cover",
          "Change cover",
          {},
          initialBytes = original,
          initialName = "cover.png",
          embeddedCover = embedded,
        ) { bytes, _ ->
          saved = bytes
          true
        }
      }
    }
    waitUntil {
      compose
        .onAllNodesWithTag("rendered-image-200x100", useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    compose
      .onNode(
        hasTestTag("document-image-preview") and hasAnyAncestor(hasTestTag("image-input-workspace"))
      )
      .performClick()
    compose.onAllNodesWithContentDescription("Reset zoom").onLast().assertIsDisplayed()
    compose.onAllNodesWithContentDescription("Close").onLast().performClick()
    compose.onNodeWithTag("image-restore-embedded").assertIsDisplayed().performClick()
    compose.onNodeWithContentDescription("Save").performClick()
    waitUntil { saved != null }
    assertArrayEquals(embedded, saved)
  }

  @Test
  fun leavingAnalysisWarnsAndCannotJumpToAnotherModelSilently() {
    var closed = false
    compose.setContent {
      MaterialTheme {
        AnalysisWorkspace(
          ModelChoice("road-server", "Road", "DETECTION", server = RoadDamageServerAdapter),
          { closed = true },
          initialImage = image(),
          onChangeModel = { _, _ -> error("No alternate exit") },
          onKeep = { _, _ -> },
        )
      }
    }
    waitUntil {
      compose
        .onAllNodes(hasContentDescription("Run model") and isEnabled())
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    compose.onNodeWithContentDescription("Choose another model").assertDoesNotExist()
    compose.onNodeWithContentDescription("Back").performClick()
    assertFalse(closed)
    compose.onNodeWithText("Cancel").performClick()
    assertFalse(closed)
    compose.onNodeWithContentDescription("Back").performClick()
    compose.onNodeWithText("Discard").performClick()
    assertTrue(closed)
  }

  @Test
  fun importedConfigurationIsAdaptiveAndRejectsInvalidDrafts() {
    var saved = false
    compose.setContent {
      MaterialTheme {
        ModelConfigurationDialog(
          LocalModel(
            "numeric",
            "Numeric model",
            ModelAdapter.NUMERIC_REGRESSION,
            inputShape = listOf(1, 3),
            outputShape = listOf(1, 1),
          ),
          {},
        ) {
          saved = true
        }
      }
    }
    val name =
      compose
        .onNode(hasSetTextAction() and hasText("Numeric model"))
        .fetchSemanticsNode()
        .boundsInRoot
    val output = compose.onNodeWithTag("model-output-index").fetchSemanticsNode().boundsInRoot
    assertTrue(output.left > name.right)
    compose.onNodeWithTag("model-output-index").performTextReplacement("bad")
    compose.onNodeWithContentDescription("Save").assertIsNotEnabled()
    compose.onNodeWithTag("model-output-index").performTextReplacement("0")
    compose.onNode(hasSetTextAction() and hasText("Numeric model")).performTextReplacement(" ")
    compose.onNodeWithContentDescription("Save").assertIsNotEnabled()
    assertFalse(saved)
    screenshot("model-config-landscape")
  }

  @Test
  fun builtInModelIsMarkedAndNumericInputIsNotPresentedAsAnImage() {
    val model =
      LocalModel("seed", "Seed", ModelAdapter.CLASSIFICATION, assetPath = "models/test.tflite")
    compose.setContent {
      MaterialTheme {
        ModelCatalogBrowser(
          listOf(ModelChoice(model.id, model.name, model.task, local = model)),
          {},
        )
      }
    }
    compose.onNodeWithText("Built in · Default").assertExists()
  }

  private fun reading(file: Boolean) {
    val text = "A line of text\n".repeat(500)
    compose.setContent {
      MaterialTheme {
        if (file) TextFilePreview(text.toByteArray(), "Text") else DocumentText(text, "Text")
      }
    }
    compose.onNodeWithContentDescription("View").performClick()
    waitUntil {
      compose.onAllNodesWithTag("document-text-page-count").fetchSemanticsNodes().any {
        it.config
          .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
          .any { t -> t.text.contains("1 /") && !t.text.contains("…") }
      }
    }
    compose.onNodeWithTag("document-text-page-count").performClick()
    compose.onNode(hasSetTextAction()).performTextReplacement("2")
    compose.onNodeWithText("Select").performClick()
    compose.onNodeWithTag("document-text-page-count").assertTextContains("2 /", substring = true)
  }

  @Test
  fun inlineTextUsesTheCommonPageCountAndJump() {
    reading(false)
  }

  @Test
  fun textResourceUsesTheCommonPageCountAndJump() {
    reading(true)
  }

  @Test
  @Config(qualifiers = "en-rUS-w900dp-h300dp-land-mdpi")
  fun contextWindowReallyUsesTwoColumnsAndCanReachLastAction() {
    var chosen = false
    compose.setContent {
      MaterialTheme {
        AdaptiveActionMenu(true, {}, "Actions") {
          repeat(20) { index ->
            AdaptiveActionItem({ Text("Action $index") }, { if (index == 19) chosen = true })
          }
        }
      }
    }
    val first = compose.onNodeWithText("Action 0").fetchSemanticsNode().boundsInRoot
    val second = compose.onNodeWithText("Action 1").fetchSemanticsNode().boundsInRoot
    assertEquals(first.top, second.top, 1f)
    assertTrue(second.left > first.right)
    compose.onNodeWithText("Action 19").performScrollTo().assertIsDisplayed().performClick()
    assertTrue(chosen)
    screenshot("context-grid")
  }

  @Test
  fun newReportCannotAdvanceWithoutAValidName() {
    var cancelled = false
    compose.setContent {
      MaterialTheme {
        DocumentRoute(
          editing = true,
          owner = compose.activity,
          onBack = { cancelled = true },
          onSaved = {},
          onEdit = {},
          onFolder = {},
          onDelete = {},
        )
      }
    }
    compose.onNodeWithContentDescription("Done").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Close").performClick()
    assertTrue(cancelled)
  }

  @Test
  fun numericWorkspaceHasATextInputAndNoImageAcquisitionToolbar() {
    val model =
      LocalModel("numeric", "Numeric", ModelAdapter.NUMERIC_REGRESSION, inputShape = listOf(1, 3))
    compose.setContent {
      MaterialTheme {
        AnalysisWorkspace(ModelChoice(model.id, model.name, model.task, local = model), {})
      }
    }
    compose.onNodeWithContentDescription("Take photo").assertDoesNotExist()
    compose.onNode(hasContentDescription("Acode", substring = true)).assertExists()
  }

  @Test
  fun coverCentersAgainstTallMetadataAndStaysAtTheTopWhenItIsTaller() {
    val bytes =
      java.nio.ByteBuffer.allocate(1644)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        .apply {
          put("RIFF".toByteArray())
          putInt(1636)
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
    var options by
      mutableStateOf(
        AudioPresentation(
          coverAsset = "cover",
          overrides =
            mapOf(
              "title" to "Title",
              "artist" to "Artist",
              "album" to "Album",
              "year" to "2026",
              "genre" to "Genre",
              "track" to "1",
              "composer" to "Last composer",
            ),
        )
      )
    val cover = image()
    compose.setContent {
      MaterialTheme {
        Column(Modifier.width(380.dp)) {
          DocumentAudio(bytes, session, "alignment", options, mapOf("cover" to cover))
        }
      }
    }
    waitUntil { compose.onAllNodesWithTag("audio-player-cover").fetchSemanticsNodes().isNotEmpty() }
    val art = compose.onNodeWithTag("audio-player-cover").fetchSemanticsNode().boundsInRoot
    val title = compose.onNodeWithText("Title").fetchSemanticsNode().boundsInRoot
    val last = compose.onNodeWithText("Last composer").fetchSemanticsNode().boundsInRoot
    assertEquals((title.top + last.bottom) / 2, art.center.y, 2f)
    compose.runOnIdle { options = options.copy(overrides = mapOf("title" to "Only title")) }
    val shortArt = compose.onNodeWithTag("audio-player-cover").fetchSemanticsNode().boundsInRoot
    val player = compose.onNodeWithTag("audio-player").fetchSemanticsNode().boundsInRoot
    assertEquals(player.top + 12f, shortArt.top, 1f)
    screenshot("player-centered-cover")
    compose.runOnIdle { session.close() }
  }
}
