package com.karandaev.fieldfolio.feature.presentation.rotation

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.library.saveDocument
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.*
import com.karandaev.fieldfolio.feature.document.route.*
import com.karandaev.fieldfolio.feature.document.state.*
import com.karandaev.fieldfolio.feature.document.state.ViewerImageWindows
import com.karandaev.fieldfolio.feature.document.viewer.*
import com.karandaev.fieldfolio.feature.models.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(
  sdk = [35],
  application = MyApplication::class,
  qualifiers = "en-rUS-w412dp-h915dp-port-mdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ViewerRotationV16Test {
  @get:Rule val compose = createEmptyComposeRule()
  private lateinit var scenario: androidx.test.core.app.ActivityScenario<ComponentActivity>
  private lateinit var activity: ComponentActivity
  private lateinit var callback: androidx.test.runner.lifecycle.ActivityLifecycleCallback

  private fun show(content: @Composable () -> Unit) {
    val app = org.robolectric.RuntimeEnvironment.getApplication() as MyApplication
    if (FirebaseApp.getApps(app).isEmpty())
      FirebaseApp.initializeApp(
        app,
        FirebaseOptions.Builder()
          .setApplicationId("1:123:android:test")
          .setApiKey("test-key")
          .setProjectId("local-test")
          .build(),
      )
    callback =
      androidx.test.runner.lifecycle.ActivityLifecycleCallback { current, stage ->
        if (current is ComponentActivity && stage == androidx.test.runner.lifecycle.Stage.CREATED) {
          activity = current
          current.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { content() } } }
        }
      }
    androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
      .addLifecycleCallback(callback)
    scenario = androidx.test.core.app.ActivityScenario.launch(ComponentActivity::class.java)
    compose.waitForIdle()
  }

  private fun rotate() {
    val old = activity
    val landscape =
      activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    compose.runOnUiThread {
      org.robolectric.RuntimeEnvironment.setQualifiers(
        if (landscape) "en-rUS-w900dp-h420dp-land-mdpi" else "en-rUS-w412dp-h915dp-port-mdpi"
      )
    }
    scenario.recreate()
    compose.waitForIdle()
    assertNotSame("The test must actually recreate the Activity", old, activity)
  }

  @After
  fun cleanup() {
    if (::scenario.isInitialized) scenario.close()
    if (::callback.isInitialized)
      androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
        .removeLifecycleCallback(callback)
  }

  private fun image() =
    java.io
      .ByteArrayOutputStream()
      .also { out ->
        android.graphics.Bitmap.createBitmap(120, 80, android.graphics.Bitmap.Config.ARGB_8888)
          .apply { eraseColor(android.graphics.Color.BLUE) }
          .let {
            it.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            it.recycle()
          }
      }
      .toByteArray()

  private fun waitUntil(test: () -> Boolean) =
    compose.waitUntil(15000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      test()
    }

  private fun app(): MyApplication {
    val app = (org.robolectric.RuntimeEnvironment.getApplication() as MyApplication)
    if (FirebaseApp.getApps(app).isEmpty())
      FirebaseApp.initializeApp(
        app,
        FirebaseOptions.Builder()
          .setApplicationId("1:123:android:test")
          .setApiKey("test-key")
          .setProjectId("test")
          .build(),
      )
    return app
  }

  private fun open(
    node: DocumentNode,
    files: Map<String, ByteArray> = emptyMap(),
    cover: String? = null,
  ) {
    val app = app()
    fun path(value: String?) =
      value?.let { if (it.startsWith("resources/")) it else "resources/doc/$it" }
    val canonical =
      node.copy(
        asset = path(node.asset),
        audio = node.audio.copy(coverAsset = path(node.audio.coverAsset)),
      )
    val id = runBlocking {
      app.reportLibrary.saveDocument(
        0,
        null,
        "Actual report",
        "",
        ReportDocument(cover = path(cover), nodes = listOf(canonical)),
        files.mapKeys { path(it.key)!! },
      )
    }
    show {
      DocumentRoute(
        id,
        editing = false,
        owner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
        onBack = {},
        onSaved = {},
        onEdit = {},
        onFolder = {},
        onDelete = {},
      )
    }
    waitUntil {
      compose.onAllNodesWithTag("report-cell-${node.id}").fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun openImageInCard(id: String) {
    compose
      .onNode(
        hasTestTag("document-image-preview") and hasAnyAncestor(hasTestTag("report-cell-$id"))
      )
      .performScrollTo()
      .performClick()
    compose.onNodeWithTag("image-result-window").assertIsDisplayed()
    compose.onNodeWithTag("media-image-pane").performTouchInput { doubleClick() }
    assertEquals(
      2f,
      compose.onNodeWithTag("image-result-window").fetchSemanticsNode().config[ImageViewerScale],
      .01f,
    )
  }

  @Test
  fun realViewerImageWindowAndZoomSurviveBothRotations() {
    open(
      DocumentNode(id = "image", name = "Image", kind = BlockKind.IMAGE, asset = "image"),
      mapOf("image" to image()),
    )
    openImageInCard("image")
    repeat(2) {
      rotate()
      compose.onNodeWithTag("image-result-window").assertIsDisplayed()
      assertEquals(
        2f,
        compose.onNodeWithTag("image-result-window").fetchSemanticsNode().config[ImageViewerScale],
        .01f,
      )
    }
  }

  @Test
  fun realViewerTextWindowAndPageSurviveBothRotations() {
    open(
      DocumentNode(
        id = "text",
        name = "Text",
        kind = BlockKind.TEXT,
        body = "A long readable line of text.\n".repeat(3000),
      )
    )
    compose
      .onNode(hasContentDescription("View") and hasAnyAncestor(hasTestTag("report-cell-text")))
      .performScrollTo()
      .performClick()
    waitUntil {
      compose
        .onAllNodes(hasContentDescription("Next page") and isEnabled())
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    compose.onNodeWithContentDescription("Next page").performClick()
    repeat(2) {
      rotate()
      waitUntil {
        compose.onAllNodesWithTag("document-text-page-count").fetchSemanticsNodes().isNotEmpty()
      }
      compose.onNodeWithTag("document-text-page-count").assertTextContains("2 /", substring = true)
    }
  }

  @Test
  fun realViewerTextFileWindowSurvivesBothRotations() {
    open(
      DocumentNode(id = "file", name = "File.txt", kind = BlockKind.TEXT_FILE, asset = "text"),
      mapOf("text" to "Long text file\n".repeat(3000).toByteArray()),
    )
    compose
      .onNode(hasContentDescription("View") and hasAnyAncestor(hasTestTag("report-cell-file")))
      .performScrollTo()
      .performClick()
    waitUntil {
      compose.onAllNodesWithTag("document-text-page-count").fetchSemanticsNodes().isNotEmpty()
    }
    repeat(2) {
      rotate()
      compose.onNodeWithTag("document-text-page-count").assertExists()
    }
  }

  @Test
  fun realViewerSongCoverWindowAndZoomSurviveBothRotations() {
    val wave =
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
        }
        .array()
    open(
      DocumentNode(
        id = "audio",
        name = "Track.wav",
        kind = BlockKind.AUDIO,
        asset = "audio",
        audio = AudioPresentation(coverAsset = "cover"),
      ),
      mapOf("audio" to wave, "cover" to image()),
    )
    waitUntil { compose.onAllNodesWithTag("audio-player-cover").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("audio-player-cover").performScrollTo().performClick()
    compose.onNodeWithTag("media-image-pane").performTouchInput { doubleClick() }
    repeat(2) {
      rotate()
      compose.onNodeWithTag("image-result-window").assertExists()
      assertEquals(
        2f,
        compose.onNodeWithTag("image-result-window").fetchSemanticsNode().config[ImageViewerScale],
        .01f,
      )
    }
  }

  private fun map(view: android.view.View): org.osmdroid.views.MapView? {
    if (view is org.osmdroid.views.MapView) return view
    if (view is android.view.ViewGroup)
      for (i in 0 until view.childCount) map(view.getChildAt(i))?.let {
        return it
      }
    return null
  }

  @Test
  fun realViewerMapCameraDoesNotReturnToFocusTargetAfterRotation() {
    open(
      DocumentNode(
        id = "map",
        name = "Map",
        kind = BlockKind.LOCATION,
        latitude = 10.0,
        longitude = 20.0,
      )
    )
    compose.onNodeWithTag("native-map-frame").performScrollTo()
    compose.runOnIdle {
      val map = map(activity.window.decorView)!!
      map.setUseDataConnection(false)
      map.controller.setZoom(14.0)
      map.controller.setCenter(org.osmdroid.util.GeoPoint(33.5, 42.75))
    }
    compose.waitForIdle()
    repeat(2) {
      rotate()
      compose.onNodeWithTag("native-map-frame").performScrollTo()
      compose.waitForIdle()
      val map = map(activity.window.decorView)!!
      assertEquals(33.5, map.mapCenter.latitude, .01)
      assertEquals(42.75, map.mapCenter.longitude, .01)
      assertEquals(14.0, map.zoomLevelDouble, .01)
    }
  }

  @Test
  fun viewerStateAlsoSurvivesTemporaryRemovalOfTheWholeLazyItem() {
    var visible by mutableStateOf(true)
    val session = DocumentAudioSession()
    show {
      if (visible)
        com.karandaev.fieldfolio.core.ui.state.ViewerSession("report:temporary:item:text") {
          Column {
            DocumentBlockBody(
              DocumentNode(
                id = "text",
                name = "Text",
                kind = BlockKind.TEXT,
                body = "Private long text. ".repeat(2000),
              ),
              emptyMap(),
              null,
              0,
              session,
              {},
              editorPreview = false,
            )
          }
        }
    }
    compose.onNodeWithContentDescription("View").performClick()
    waitUntil {
      compose.onAllNodesWithTag("document-text-page-count").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithTag("document-text-page-count").assertExists()
    compose.runOnIdle { visible = false }
    compose.waitForIdle()
    compose.runOnIdle { visible = true }
    compose.waitForIdle()
    waitUntil {
      compose.onAllNodesWithTag("document-text-page-count").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithTag("document-text-page-count").assertExists()
    session.close()
  }

  @Test fun imageWindowSurvivesSourceCellDisposalAndBothRotations() = detachedImage(false)

  @Test fun songCoverWindowSurvivesSourceCellDisposalAndBothRotations() = detachedImage(true)

  private fun detachedImage(cover: Boolean) {
    var mounted by mutableStateOf(true)
    val session = DocumentAudioSession()
    val bytes = image()
    show {
      val windows = com.karandaev.fieldfolio.core.ui.state.rememberSession { ViewerImageWindows() }
      CompositionLocalProvider(LocalViewerImages provides windows) {
        com.karandaev.fieldfolio.core.ui.state.ViewerSession(
          "report:detached:item:${if(cover) "audio" else "image"}"
        ) {
          if (mounted) {
            if (cover)
              DocumentAudio(
                byteArrayOf(1, 2),
                session,
                "track",
                options = AudioPresentation(coverAsset = "cover"),
                resources = mapOf("cover" to bytes),
              )
            else
              DocumentBlockBody(
                DocumentNode(
                  id = "picture",
                  name = "Picture",
                  kind = BlockKind.IMAGE,
                  asset = "image",
                ),
                mapOf("image" to bytes),
                null,
                0,
                session,
                {},
              )
          }
        }
      }
      ViewerImageWindowHost(windows)
    }
    if (cover) {
      waitUntil {
        compose.onAllNodesWithTag("audio-player-cover").fetchSemanticsNodes().isNotEmpty()
      }
      compose.onNodeWithTag("audio-player-cover").performClick()
    } else compose.onNodeWithTag("document-image-preview").performClick()
    compose.onNodeWithTag("image-result-window").assertIsDisplayed()
    compose.onNodeWithTag("media-image-pane").performTouchInput { doubleClick() }
    compose.runOnIdle { mounted = false }
    repeat(2) {
      rotate()
      compose.onNodeWithTag("image-result-window").assertIsDisplayed()
      assertEquals(
        2f,
        compose.onNodeWithTag("image-result-window").fetchSemanticsNode().config[ImageViewerScale],
        .01f,
      )
    }
    session.close()
  }

  @Test
  fun pagedTextReaderStaysOpenAfterItsCellDisappearsAndBothRotations() =
    detachedText(TextOpenMode.PAGED)

  @Test
  fun acodeTextReaderStaysOpenAfterItsCellDisappearsAndBothRotations() =
    detachedText(TextOpenMode.ACODE)

  private fun detachedText(mode: TextOpenMode) {
    var mounted by mutableStateOf(true)
    val session = DocumentAudioSession()
    val n =
      DocumentNode(
        id = "text",
        name = "Text",
        kind = BlockKind.TEXT,
        body = "Private text to read. ".repeat(2000),
        textOpenMode = mode,
      )
    show {
      val windows = com.karandaev.fieldfolio.core.ui.state.rememberSession { ViewerImageWindows() }
      CompositionLocalProvider(LocalViewerImages provides windows) {
        com.karandaev.fieldfolio.core.ui.state.ViewerSession("report:detached:text:$mode") {
          if (mounted) Column { DocumentBlockBody(n, emptyMap(), null, 0, session, {}) }
        }
      }
      ViewerImageWindowHost(windows)
    }
    compose.onNodeWithContentDescription("View").performClick()
    if (mode == TextOpenMode.PAGED) {
      waitUntil {
        compose
          .onAllNodes(hasContentDescription("Next page") and isEnabled())
          .fetchSemanticsNodes()
          .isNotEmpty()
      }
      compose.onNodeWithContentDescription("Next page").performClick()
    }
    compose.runOnIdle { mounted = false }
    repeat(2) {
      rotate()
      if (mode == TextOpenMode.ACODE)
        compose.onNodeWithTag("acode-readonly-window").assertIsDisplayed()
      else
        compose
          .onNodeWithTag("document-text-page-count")
          .assertTextContains("2 /", substring = true)
    }
    session.close()
  }
}
