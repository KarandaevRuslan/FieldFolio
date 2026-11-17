package com.karandaev.fieldfolio.feature.document

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.library.saveDocument
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.scroll.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.route.DocumentRoute
import com.karandaev.fieldfolio.feature.document.viewer.AudioPlayerView
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
  qualifiers = "en-rUS-w900dp-h420dp-land-mdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun snapshot(name: String) {
    compose.waitForIdle()
    compose.runOnIdle {
      val view =
        org.robolectric.shadows.ShadowDialog.getLatestDialog()
          ?.takeIf { it.isShowing }
          ?.window
          ?.decorView ?: compose.activity.window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      view.draw(android.graphics.Canvas(bitmap))
      val directory = java.io.File("build/adaptive-previews").apply { mkdirs() }
      java.io.File(directory, "$name.png").outputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
      }
      bitmap.recycle()
    }
  }

  private fun image(): ByteArray {
    val b =
      Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.DKGRAY)
      }
    return java.io
      .ByteArrayOutputStream()
      .also {
        b.compress(Bitmap.CompressFormat.PNG, 100, it)
        b.recycle()
      }
      .toByteArray()
  }

  private fun firebase() {
    val app = compose.activity.application as MyApplication
    if (FirebaseApp.getApps(app).isEmpty())
      FirebaseApp.initializeApp(
        app,
        FirebaseOptions.Builder()
          .setApplicationId("1:123:android:test")
          .setApiKey("test-key")
          .setProjectId("local-test")
          .build(),
      )
  }

  @Test
  fun landscapeCatalogHasVerticalSourceTabsAndTwoGridColumns() {
    val list =
      (0..5).map {
        ModelChoice(
          "m$it",
          "Model $it",
          "CLASSIFICATION",
          local = LocalModel("m$it", "Model $it", ModelAdapter.CLASSIFICATION),
        )
      }
    compose.setContent { MaterialTheme { ModelCatalogBrowser(list, {}) } }
    val first = compose.onNodeWithTag("models-local-tab").fetchSemanticsNode().boundsInRoot
    val second = compose.onNodeWithTag("models-server-tab").fetchSemanticsNode().boundsInRoot
    assertEquals(first.left, second.left, .5f)
    assertTrue(second.top >= first.bottom)
    val a = compose.onNodeWithText("Model 0").fetchSemanticsNode().boundsInRoot
    val b = compose.onNodeWithText("Model 1").fetchSemanticsNode().boundsInRoot
    assertEquals(a.top, b.top, 1f)
    assertTrue(b.left > a.right)
    snapshot("models-landscape")
  }

  @Test
  fun overlayImageStaysInsideTheViewportWhileDetailsScrollAndControlsStayVisible() {
    val result =
      ModelResultData(
        modelId = "x",
        modelName = "Output",
        provider = "LOCAL",
        task = "CLASSIFICATION",
        scores = (0..40).map { ResultScore("Class $it", .8) },
      )
    compose.setContent { MaterialTheme { ImageResultDialog(image(), result, null, dismiss = {}) } }
    compose.onNodeWithTag("media-landscape").assertExists()
    val image = compose.onNodeWithTag("media-image-pane").fetchSemanticsNode().boundsInRoot
    val panel = compose.onNodeWithTag("media-controls-pane").fetchSemanticsNode().boundsInRoot
    assertTrue(image.left >= panel.right)
    assertTrue(image.bottom <= compose.activity.resources.displayMetrics.heightPixels + 1)
    compose.onNodeWithText("Class 19").performScrollTo().assertIsDisplayed()
    compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    compose.onNodeWithTag("result-overlay-toggle").assertIsDisplayed()
    assertEquals(image, compose.onNodeWithTag("media-image-pane").fetchSemanticsNode().boundsInRoot)
    snapshot("overlay-landscape")
  }

  @Test
  fun modelExecutionUsesTheSamePinnedTwoPaneLayout() {
    firebase()
    compose.setContent {
      MaterialTheme {
        AnalysisWorkspace(
          ModelChoice("road-server", "Road damage", "DETECTION", server = RoadDamageServerAdapter),
          {},
        )
      }
    }
    compose.onNodeWithTag("media-landscape").assertExists()
    compose.onNodeWithContentDescription("Run model").assertIsDisplayed()
    compose.onNodeWithContentDescription("Take photo").assertIsDisplayed()
    assertTrue(
      compose.onNodeWithTag("media-image-pane").fetchSemanticsNode().boundsInRoot.left > 300
    )
  }

  @Test
  fun scrollbarIsHiddenForShortListsAndDraggingItMovesAnOverflowingList() {
    var long by mutableStateOf(false)
    val stateHolder = arrayOfNulls<androidx.compose.foundation.lazy.LazyListState>(1)
    compose.setContent {
      MaterialTheme {
        val state = rememberLazyListState()
        stateHolder[0] = state
        AppLazyColumn(Modifier.fillMaxSize().testTag("scroll-list"), state = state) {
          items(if (long) 60 else 1) { Text("Row $it", Modifier.height(64.dp).fillMaxWidth()) }
        }
      }
    }
    compose
      .onNodeWithTag("scroll-list")
      .assert(SemanticsMatcher.expectValue(ScrollIndicatorVisible, false))
    compose.runOnIdle { long = true }
    compose
      .onNodeWithTag("scroll-list")
      .assert(SemanticsMatcher.expectValue(ScrollIndicatorVisible, true))
    compose.onNodeWithTag("scroll-list").performTouchInput {
      swipe(Offset(width - 5f, 20f), Offset(width - 5f, height - 20f), 500)
    }
    assertTrue(stateHolder[0]!!.firstVisibleItemIndex > 20)
  }

  @Test
  fun viewerFillsRowsWithTwoCellsAndKeepsExpandedFolderContentsInTheirCell() {
    firebase()
    val app = compose.activity.application as MyApplication
    val bytes = image()
    val document =
      ReportDocument(
        nodes =
          listOf(
            DocumentNode(id = "folder", name = "Survey", kind = BlockKind.FOLDER),
            DocumentNode(
              id = "nested",
              parentId = "folder",
              name = "Note",
              kind = BlockKind.TEXT,
              body = "Inside folder",
            ),
            DocumentNode(
              id = "right",
              order = 1,
              name = "Right cell",
              kind = BlockKind.TEXT,
              body = "Short text",
            ),
            DocumentNode(
              id = "photo",
              order = 2,
              name = "Photo",
              kind = BlockKind.IMAGE,
              asset = "resources/tall.png",
            ),
            DocumentNode(
              id = "map",
              order = 3,
              name = "Location",
              kind = BlockKind.LOCATION,
              deviceLocation = false,
              latitude = 20.0,
              longitude = 30.0,
            ),
          )
      )
    val id =
      kotlinx.coroutines.runBlocking {
        app.reportLibrary.saveDocument(
          0,
          null,
          "Grid report",
          "",
          document,
          mapOf("resources/tall.png" to bytes),
        )
      }
    compose.setContent {
      MaterialTheme {
        DocumentRoute(
          id,
          editing = false,
          owner = compose.activity,
          onBack = {},
          onSaved = {},
          onEdit = {},
          onFolder = {},
          onDelete = {},
        )
      }
    }
    compose.waitUntil(15000) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      compose.onAllNodesWithText("Survey").fetchSemanticsNodes().isNotEmpty()
    }
    val left = compose.onNodeWithTag("report-cell-folder").fetchSemanticsNode().boundsInRoot
    val right = compose.onNodeWithTag("report-cell-right").fetchSemanticsNode().boundsInRoot
    val header = compose.onNodeWithTag("report-header").fetchSemanticsNode().boundsInRoot
    assertTrue(header.top <= left.top)
    assertTrue(left.left >= header.right || left.top >= header.bottom)
    assertTrue(right.width > 300f)
    compose.onNodeWithText("Survey").performClick()
    val folder = compose.onNodeWithTag("report-cell-folder").fetchSemanticsNode().boundsInRoot
    val text = compose.onNodeWithText("Inside folder").fetchSemanticsNode().boundsInRoot
    assertTrue(text.top >= folder.top && text.bottom <= folder.bottom)
    compose.onNodeWithTag("report-view-grid").performScrollToIndex(4)
    val photo = compose.onNodeWithTag("report-cell-photo").fetchSemanticsNode().boundsInRoot
    val map = compose.onNodeWithTag("report-cell-map").fetchSemanticsNode().boundsInRoot
    assertTrue(photo.width > 300f && map.width > 300f)
    assertTrue(photo.height < map.height)
    assertEquals(
      160f,
      compose.onNodeWithTag("document-image-preview").fetchSemanticsNode().boundsInRoot.height,
      1f,
    )
    assertEquals(
      180f,
      compose.onNodeWithTag("native-map-frame").fetchSemanticsNode().boundsInRoot.height,
      1f,
    )
  }

  @Test
  fun reportsHaveAVerticalToolRailAndTheFileListKeepsMostOfTheWidth() {
    firebase()
    val app = compose.activity.application as MyApplication
    val vm = com.karandaev.fieldfolio.feature.library.LibraryViewModel(app.reportLibrary)
    val store = androidx.lifecycle.ViewModelStore().apply { put("library", vm) }
    try {
      compose.setContent {
        MaterialTheme { com.karandaev.fieldfolio.feature.library.LibraryBrowser(vm, {}, {}, {}) }
      }
      val rail = compose.onNodeWithTag("library-tool-rail").fetchSemanticsNode().boundsInRoot
      val files = compose.onNodeWithTag("library-browser").fetchSemanticsNode().boundsInRoot
      assertTrue(rail.right <= files.left)
      assertTrue(files.width > compose.activity.resources.displayMetrics.widthPixels * .8f)
      snapshot("reports-landscape")
    } finally {
      store.clear()
    }
  }

  @Test
  fun audioStepPreferenceUpdatesThePlayerControls() {
    val context = compose.activity
    AudioPreferences.set(context, 10)
    var position by mutableIntStateOf(0)
    compose.setContent {
      MaterialTheme {
        val seconds by rememberAudioSeekSeconds()
        AudioPlayerView(
          120000,
          position,
          false,
          true,
          false,
          floatArrayOf(.5f),
          { position = seekPosition(it, 120000) },
          {},
          {},
          stepMillis = seconds * 1000,
        )
      }
    }
    compose.onNodeWithContentDescription("Forward 10 seconds (hold to repeat)").performClick()
    assertEquals(10000, position)
    compose.runOnIdle { AudioPreferences.set(context, 2) }
    compose.onNodeWithContentDescription("Back 2 seconds (hold to repeat)").performClick()
    assertEquals(8000, position)
  }
}
