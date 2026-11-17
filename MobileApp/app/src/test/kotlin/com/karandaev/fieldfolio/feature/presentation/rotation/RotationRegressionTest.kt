package com.karandaev.fieldfolio.feature.presentation.rotation

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.withTransaction
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.app.navigation.AppDestination
import com.karandaev.fieldfolio.app.scaffold.FieldfolioScaffold
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.adaptive.AdaptiveLayoutType
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.*
import com.karandaev.fieldfolio.feature.document.route.*
import com.karandaev.fieldfolio.feature.document.state.*
import com.karandaev.fieldfolio.feature.document.viewer.*
import com.karandaev.fieldfolio.feature.models.*
import kotlinx.coroutines.*
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
class RotationRegressionTest {
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

  @Test
  fun headingDraftAndItsWindowSurviveBothRotations() {
    show {
      DocumentNodeDialog(
        DocumentNode(id = "h", name = "Heading", kind = BlockKind.HEADING, body = "Initial"),
        emptyMap(),
        {},
        { _, _ -> },
      )
    }
    compose
      .onNode(hasSetTextAction() and hasText("Heading"))
      .performTextReplacement("Draft after typing")
    rotate()
    compose.onNode(hasSetTextAction() and hasText("Draft after typing")).assertIsDisplayed()
    compose.onNodeWithContentDescription("Save").assertIsDisplayed()
    rotate()
    compose.onNode(hasSetTextAction() and hasText("Draft after typing")).assertIsDisplayed()
  }

  @Test
  fun modelSearchSourceAndNestedAnalysisWindowSurviveRotation() {
    show {
      val nav = rememberNavController()
      val landscape =
        androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
          Configuration.ORIENTATION_LANDSCAPE
      FieldfolioScaffold(
        AppDestination("models", "Models", false, false, false, false, false),
        if (landscape) AdaptiveLayoutType.TwoPane else AdaptiveLayoutType.SinglePane,
        nav,
      ) { modifier ->
        androidx.navigation.compose.NavHost(nav, "home", modifier) {
          composable("home") { Button({ nav.navigate("models") }) { Text("Open models") } }
          composable("models") { ModelsRoute() }
        }
      }
    }
    compose.onNodeWithText("Open models").performClick()
    compose.onNodeWithTag("models-server-tab").performClick()
    compose.onNodeWithTag("models-search").performTextInput("Road")
    rotate()
    compose.onNodeWithTag("models-import").assertDoesNotExist()
    compose.onNodeWithTag("models-search").assertTextContains("Road")
    compose.onNodeWithText("Road damage").performClick()
    rotate()
    compose.onNodeWithTag("analysis-workspace").assertIsDisplayed()
    compose.onNodeWithContentDescription("Take photo").assertIsDisplayed()
  }

  @Test
  fun adaptiveScaffoldDoesNotDisposeOrResetItsContent() {
    var wide by mutableStateOf(false)
    var disposed = 0
    show {
      val nav = rememberNavController()
      FieldfolioScaffold(
        AppDestination("models", "Models", false, false, false, false, false),
        if (wide) AdaptiveLayoutType.TwoPane else AdaptiveLayoutType.SinglePane,
        nav,
      ) { modifier ->
        var text by remember { mutableStateOf("Initial") }
        DisposableEffect(Unit) { onDispose { disposed++ } }
        OutlinedTextField(text, { text = it }, modifier)
      }
    }
    compose.onNode(hasSetTextAction()).performTextReplacement("State survives layout switch")
    compose.runOnIdle { wide = true }
    compose.onNode(hasSetTextAction() and hasText("State survives layout switch")).assertExists()
    assertEquals(0, disposed)
    compose.runOnIdle { wide = false }
    compose.onNode(hasText("State survives layout switch")).assertExists()
    assertEquals(0, disposed)
  }

  @Test
  fun privateRetainedDraftIsNotWrittenToAndroidBundle() {
    show {
      var draft by rememberSession { mutableStateOf("") }
      OutlinedTextField(draft, { draft = it })
    }
    compose.onNode(hasSetTextAction()).performTextInput("PRIVATE_ROTATION_FIXTURE_12948")
    val saved = Bundle()
    compose.runOnUiThread {
      org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(
        activity,
        "onSaveInstanceState",
        org.robolectric.util.ReflectionHelpers.ClassParameter.from(Bundle::class.java, saved),
      )
    }
    val parcel = android.os.Parcel.obtain()
    try {
      parcel.writeBundle(saved)
      val bytes = parcel.marshall().toList()
      listOf(Charsets.UTF_8, Charsets.UTF_16LE).forEach { encoding ->
        val secret = "PRIVATE_ROTATION_FIXTURE_12948".toByteArray(encoding).toList()
        assertFalse(bytes.windowed(secret.size).any { it == secret })
      }
    } finally {
      parcel.recycle()
    }
    rotate()
    compose.onNode(hasText("PRIVATE_ROTATION_FIXTURE_12948")).assertExists()
  }

  @Test
  fun clockTimeZoneAndCoordinatesSurviveRotation() {
    show {
      DocumentNodeDialog(
        DocumentNode(
          id = "clock",
          name = "Clock",
          kind = BlockKind.CLOCK,
          deviceLocation = false,
          latitude = 12.0,
          longitude = 30.0,
          zoneId = "UTC",
        ),
        emptyMap(),
        {},
        { _, _ -> },
      )
    }
    compose.onNode(hasSetTextAction() and hasText("UTC")).performTextReplacement("Asia/Tokyo")
    rotate()
    compose.onNode(hasSetTextAction() and hasText("Asia/Tokyo")).assertExists()
    compose.onNode(hasSetTextAction() and hasText("12.0")).assertExists()
    compose.onNodeWithContentDescription("Save").assertIsDisplayed()
  }

  @Test
  fun analysisKeepsItsImageAndNestedCropWindowAcrossRotation() {
    show {
      AnalysisWorkspace(
        ModelChoice("road-server", "Road damage", "DETECTION", server = RoadDamageServerAdapter),
        {},
      )
    }
    val vm =
      androidx.lifecycle
        .ViewModelProvider(activity, androidx.lifecycle.ViewModelProvider.NewInstanceFactory())[
          "analysis-true-road-server", AnalysisViewModel::class.java]
    val bitmap =
      android.graphics.Bitmap.createBitmap(80, 40, android.graphics.Bitmap.Config.ARGB_8888)
    val image =
      java.io
        .ByteArrayOutputStream()
        .also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        .toByteArray()
    bitmap.recycle()
    compose.runOnIdle { vm.image(image) }
    compose.onNodeWithContentDescription("Crop image").performClick()
    rotate()
    compose.onNodeWithText("1:1").assertIsDisplayed()
    compose.onNodeWithContentDescription("Apply").assertIsDisplayed()
    val restored =
      androidx.lifecycle
        .ViewModelProvider(activity, androidx.lifecycle.ViewModelProvider.NewInstanceFactory())[
          "analysis-true-road-server", AnalysisViewModel::class.java]
    assertSame(vm, restored)
    assertArrayEquals(image, restored.image)
    assertTrue(restored.image!!.any { it != 0.toByte() })
  }

  @Test
  fun documentFileSearchSurvivesSwitchBetweenEditorColumns() {
    show {
      DocumentRoute(
        editing = true,
        owner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
        onBack = {},
        onSaved = {},
        onEdit = {},
        onFolder = {},
        onDelete = {},
      )
    }
    compose.onNodeWithContentDescription("Done").performClick()
    val vm =
      androidx.lifecycle
        .ViewModelProvider(activity, androidx.lifecycle.ViewModelProvider.NewInstanceFactory())[
          "document-0-null", DocumentViewModel::class.java]
    compose.runOnIdle {
      vm.add(BlockKind.TEXT, "Keep this")
      vm.add(BlockKind.TEXT, "Other")
    }
    compose.onNodeWithTag("document-file-search").performTextInput("Keep")
    rotate()
    compose.onNodeWithTag("document-file-search").assertTextContains("Keep")
    compose.onNodeWithText("Other").assertDoesNotExist()
    assertEquals(2, vm.document.nodes.size)
  }

  @Test
  fun savedCoverRemainsRestorableAfterReplacementAndRealActivityRecreation() {
    fun image(color: Int) =
      java.io
        .ByteArrayOutputStream()
        .also { out ->
          val bitmap =
            android.graphics.Bitmap.createBitmap(100, 60, android.graphics.Bitmap.Config.ARGB_8888)
          bitmap.eraseColor(color)
          bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
          bitmap.recycle()
        }
        .toByteArray()
    val original = image(android.graphics.Color.RED)
    show {
      com.karandaev.fieldfolio.feature.media.ReportImageInputDialog(
        "saved-cover-rotation",
        "Cover",
        {},
        initialBytes = original,
        initialName = "cover.png",
      ) { _, _ ->
        true
      }
    }
    val vm =
      androidx.lifecycle
        .ViewModelProvider(activity, androidx.lifecycle.ViewModelProvider.NewInstanceFactory())[
          "image-input-saved-cover-rotation",
          com.karandaev.fieldfolio.feature.media.ImageInputDraft::class.java]
    compose.waitUntil(15000) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      vm.hasSavedImage && !vm.busy
    }
    compose.runOnIdle { vm.set(image(android.graphics.Color.BLUE), "replacement.png") }
    rotate()
    compose.onNodeWithContentDescription("Restore saved image").assertIsEnabled().performClick()
    compose.waitUntil(15000) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      !vm.busy && vm.bytes?.contentEquals(original) == true
    }
    assertEquals("cover.png", vm.name)
    assertFalse(vm.changed)
    rotate()
    assertArrayEquals(original, vm.bytes)
    compose.onNodeWithContentDescription("Restore saved image").assertIsEnabled()
  }

  @Test
  fun commonImageInputRetainsImageAndItsEditedName() {
    show {
      com.karandaev.fieldfolio.feature.media.ReportImageInputDialog(
        "report",
        "Add image",
        {},
        { _, _ -> true },
      )
    }
    val vm =
      androidx.lifecycle
        .ViewModelProvider(activity, androidx.lifecycle.ViewModelProvider.NewInstanceFactory())[
          "image-input-report", com.karandaev.fieldfolio.feature.media.ImageInputDraft::class.java]
    val bitmap =
      android.graphics.Bitmap.createBitmap(40, 20, android.graphics.Bitmap.Config.ARGB_8888)
    val image =
      java.io
        .ByteArrayOutputStream()
        .also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        .toByteArray()
    bitmap.recycle()
    compose.runOnIdle { vm.set(image, "photo.png") }
    compose
      .onNode(hasSetTextAction() and hasText("photo.png"))
      .performTextReplacement("Retained image.png")
    rotate()
    compose.onNode(hasSetTextAction() and hasText("Retained image.png")).assertIsDisplayed()
    assertArrayEquals(image, vm.bytes)
    assertTrue(vm.bytes!!.any { it != 0.toByte() })
  }

  @Test
  fun acodeRetainsUnsavedSourceSelectionAndWindow() {
    show {
      AcodeBlockEditor(
        DocumentNode(
          id = "rotation-text",
          name = "Initial.txt",
          kind = BlockKind.TEXT,
          body = "Initial source",
        ),
        emptyMap(),
        {},
        { _, _ -> },
      )
    }
    val vm =
      androidx.lifecycle
        .ViewModelProvider(activity, androidx.lifecycle.ViewModelProvider.NewInstanceFactory())[
          "acode-rotation-text", AcodeDraftViewModel::class.java]
    compose.runOnIdle {
      vm.text = "Unsaved source after typing"
      vm.name = "Draft.kt"
      vm.anchor = 3
      vm.head = 9
    }
    rotate()
    compose.onNodeWithTag("acode-editor-window").assertIsDisplayed()
    compose.onNodeWithText("Draft.kt").assertIsDisplayed()
    assertTrue(vm.active)
    assertEquals("Unsaved source after typing", vm.text)
    assertEquals(9, vm.head)
  }

  @Test
  fun savingAcrossRotationDeliversCompletionToTheRestoredWindowOnlyOnce() {
    val completions = mutableListOf<Long>()
    show {
      DocumentRoute(
        editing = true,
        owner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
        onBack = {},
        onSaved = { completions += it },
        onEdit = {},
        onFolder = {},
        onDelete = {},
      )
    }
    compose.onNodeWithTag("report-logical-name").performTextInput("Saved across rotation")
    compose.onNodeWithContentDescription("Done").performClick()
    val app = activity.application as MyApplication
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val writer =
      writerScope.launch {
        app.database.withTransaction {
          entered.complete(Unit)
          release.await()
        }
      }
    runBlocking { withTimeout(10000) { entered.await() } }
    try {
      compose.onNodeWithContentDescription("Save").performClick()
      rotate()
      assertTrue("The transaction must still be waiting during rotation", completions.isEmpty())
    } finally {
      release.complete(Unit)
      runBlocking { writer.join() }
      writerScope.cancel()
    }
    compose.waitUntil(15000) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      completions.size == 1
    }
    assertEquals(
      "Saved across rotation",
      runBlocking { app.reportLibrary.getInspectionReportById(completions.single()) }!!.title,
    )
    rotate()
    assertEquals(1, completions.size)
  }

  @Test
  fun mapCameraSurvivesRepeatedRealActivityRecreationWithoutFocusingTheTarget() {
    var native: org.osmdroid.views.MapView? = null
    show {
      com.karandaev.fieldfolio.core.ui.components.map.AdaptiveOsmMap(
        emptyList(),
        Modifier.fillMaxSize(),
        focusTarget = com.karandaev.fieldfolio.core.domain.model.location.MapPoint(10.0, 20.0),
        mapRef = {
          it.setUseDataConnection(false)
          native = it
        },
      )
    }
    compose.runOnIdle {
      native!!.controller.setZoom(12.0)
      native!!.controller.setCenter(org.osmdroid.util.GeoPoint(55.25, 92.75))
    }
    repeat(4) {
      rotate()
      assertEquals(55.25, native!!.mapCenter.latitude, .001)
      assertEquals(92.75, native!!.mapCenter.longitude, .001)
      assertEquals(12.0, native!!.zoomLevelDouble, .01)
    }
  }

  @Test
  fun audioTransportContinuesAcrossRotationInsteadOfBeingRecreated() {
    val players = mutableListOf<android.media.MediaPlayer>()
    org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider {
      org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(120000, 0)
    }
    org.robolectric.shadows.ShadowMediaPlayer.setCreateListener { player, _ -> players += player }
    try {
      show {
        DocumentRoute(
          editing = false,
          owner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
          onBack = {},
          onSaved = {},
          onEdit = {},
          onFolder = {},
          onDelete = {},
        )
      }
      val vm =
        androidx.lifecycle
          .ViewModelProvider(activity, androidx.lifecycle.ViewModelProvider.NewInstanceFactory())[
            "document-0-null", DocumentViewModel::class.java]
      compose.runOnIdle {
        vm.addFile(byteArrayOf(1, 2, 3, 4), "music.mp3", "audio/mpeg", BlockKind.AUDIO)
      }
      compose.waitUntil(10000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        compose
          .onAllNodes(hasContentDescription("Play") and isEnabled())
          .fetchSemanticsNodes()
          .isNotEmpty()
      }
      compose.onNodeWithContentDescription("Play").performClick()
      assertTrue(players.single().isPlaying)
      val player = players.single()
      rotate()
      assertEquals(1, players.size)
      assertTrue(player.isPlaying)
      compose.onNodeWithContentDescription("Pause").assertExists()
      compose.onNodeWithContentDescription("Pause").performClick()
      assertFalse(player.isPlaying)
    } finally {
      org.robolectric.shadows.ShadowMediaPlayer.setCreateListener(null)
      org.robolectric.shadows.ShadowMediaPlayer.resetStaticState()
    }
  }

  @Test
  fun externalTextReadingModeAndPageSurviveBothRotationsWithReloadedBytes() {
    show {
      TextFilePreview(("first page ".repeat(1000) + "LAST PAGE").toByteArray(), "reading.txt")
    }
    compose.waitUntil(10000) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      compose.onAllNodesWithContentDescription("View").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithContentDescription("View").performClick()
    compose.onNodeWithTag("document-text-page-count").performClick()
    compose.onNode(hasSetTextAction()).performTextReplacement("2")
    compose.onNodeWithText("Select").performClick()
    compose.onNodeWithTag("document-text-page-count").assertTextContains("2 /", substring = true)
    repeat(2) {
      rotate()
      compose.onNodeWithContentDescription("Close").assertIsDisplayed()
      compose.onNodeWithTag("document-text-page-count").assertTextContains("2 /", substring = true)
    }
  }

  @Test
  fun audioTagDraftAndUnsavedGuardSurviveBothRotations() {
    var saved: DocumentNode? = null
    show {
      AudioPropertiesDialog(
        DocumentNode(id = "audio-rotate", name = "sound.m4a", kind = BlockKind.AUDIO),
        emptyMap(),
        {},
      ) { node, _ ->
        saved = node
      }
    }
    compose.onNodeWithContentDescription("Expand or collapse metadata").performClick()
    compose
      .onNode(hasSetTextAction() and hasText("Artist"))
      .performTextReplacement("Retained artist")
    repeat(2) {
      rotate()
      compose
        .onNode(hasSetTextAction() and hasText("Retained artist"))
        .performScrollTo()
        .assertExists()
    }
    compose.onNodeWithContentDescription("Close").performClick()
    compose.onNodeWithText("Cancel").performClick()
    compose.onNodeWithContentDescription("Save").performClick()
    assertEquals("Retained artist", saved!!.audio.overrides["artist"])
  }

  @Test
  fun reportPropertiesCoverWorkspaceAndCropSurviveBothRotations() {
    show {
      val owner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!
      DocumentRoute(
        editing = true,
        owner = owner,
        onBack = {},
        onSaved = {},
        onEdit = {},
        onFolder = {},
        onDelete = {},
      )
    }
    val vm =
      androidx.lifecycle
        .ViewModelProvider(activity, androidx.lifecycle.ViewModelProvider.NewInstanceFactory())[
          "document-0-null", DocumentViewModel::class.java]
    val bitmap =
      android.graphics.Bitmap.createBitmap(120, 80, android.graphics.Bitmap.Config.ARGB_8888)
    val bytes =
      java.io
        .ByteArrayOutputStream()
        .also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        .toByteArray()
    bitmap.recycle()
    compose.runOnIdle {
      vm.title = "Report"
      vm.addFile(bytes, "cover.png", "image/png", BlockKind.IMAGE, true)
    }
    compose.onNodeWithContentDescription("Add cover").performClick()
    compose.waitUntil(10000) {
      compose.onAllNodesWithTag("image-input-workspace").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithTag("resource-name").assertDoesNotExist()
    compose.onNodeWithContentDescription("Crop image").performClick()
    repeat(2) {
      rotate()
      compose.onNodeWithText("1:1").assertIsDisplayed()
      compose.onNodeWithContentDescription("Apply").assertIsDisplayed()
    }
    compose.onAllNodesWithContentDescription("Close").onLast().performClick()
    compose.onNodeWithTag("image-input-workspace").assertIsDisplayed()
    compose.onNodeWithTag("resource-name").assertDoesNotExist()
  }

  @Test
  fun importedModelConfigurationRetainsInvalidAndUnsavedDraftsAcrossBothRotations() {
    show {
      ModelConfigurationDialog(
        LocalModel("rotate-config", "Original model", ModelAdapter.NUMERIC_REGRESSION),
        {},
      ) {}
    }
    compose
      .onNode(hasSetTextAction() and hasText("Original model"))
      .performTextReplacement("Retained model")
    compose.onNodeWithTag("model-output-index").performTextReplacement("bad")
    repeat(2) {
      rotate()
      compose.onNode(hasSetTextAction() and hasText("Retained model")).assertExists()
      compose.onNodeWithTag("model-output-index").assertTextContains("bad")
      compose.onNodeWithContentDescription("Save").assertIsNotEnabled()
    }
    compose.onNodeWithContentDescription("Close").performClick()
    compose.onNodeWithText("Discard").assertIsDisplayed()
  }

  @Test
  fun nodeAliasVisibilityAndEmptyTitleSurviveRealActivityRecreation() {
    var saved: DocumentNode? = null
    val node =
      DocumentNode(id = "presentation", name = "Logical", kind = BlockKind.TEXT, body = "Body")
    show { DocumentNodeDialog(node, emptyMap(), {}) { next, _ -> saved = next } }
    compose.onNodeWithTag("resource-display-name").performScrollTo().performTextReplacement("Alias")
    compose.onNodeWithTag("resource-hide-title").performClick()
    compose.onNodeWithTag("resource-visible").performClick()
    repeat(2) {
      rotate()
      compose.onNodeWithTag("resource-display-name").assertTextContains("Alias")
      compose.onNodeWithTag("resource-hide-title").assertIsOn()
      compose.onNodeWithTag("resource-visible").assertIsOff()
    }
    compose.onNodeWithContentDescription("Save").performClick()
    assertTrue(saved!!.hideTitle)
    assertFalse(saved!!.visible)
    assertEquals("Alias", saved!!.displayName)
  }

  @Test
  fun interactiveDocumentIdentitySurvivesBothRotations() {
    val node =
      DocumentNode(
        id = "notebook",
        name = "Document",
        kind = BlockKind.DOCUMENT,
        body = com.karandaev.fieldfolio.core.document.runtime.DefaultNotebookDocument,
      )
    var saved: DocumentNode? = null
    show { NotebookDocumentProperties(node, emptyMap(), {}) { next, _ -> saved = next } }
    compose
      .onNodeWithTag("resource-display-name")
      .performScrollTo()
      .performTextReplacement("Interactive report")
    compose.onNodeWithTag("resource-hide-title").performClick()
    compose.onNodeWithTag("resource-visible").performClick()
    repeat(2) {
      rotate()
      compose.onNodeWithTag("document-properties").assertExists()
      compose.onNodeWithTag("resource-display-name").assertTextContains("Interactive report")
      compose.onNodeWithTag("resource-hide-title").assertIsOn()
      compose.onNodeWithTag("resource-visible").assertIsOff()
    }
    compose
      .onNodeWithContentDescription("Save")
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()
    compose.waitForIdle()
    assertTrue(saved!!.hideTitle)
    assertFalse(saved!!.visible)
    assertEquals("Interactive report", saved!!.displayName)
    assertEquals(node.body, saved!!.body)
  }

  @Test
  fun interactiveDocumentDoesNotCrashWhenLogicalNameIsClearedDuringEditing() {
    val node =
      DocumentNode(
        id = "notebook-empty",
        name = "Document",
        kind = BlockKind.DOCUMENT,
        body = com.karandaev.fieldfolio.core.document.runtime.DefaultNotebookDocument,
      )
    show { NotebookDocumentProperties(node, emptyMap(), {}) { _, _ -> } }
    compose.onNode(hasSetTextAction() and hasText("Document")).performTextReplacement("")
    compose.onNodeWithContentDescription("Save").assertIsNotEnabled()
    rotate()
    compose.onNodeWithContentDescription("Save").assertIsNotEnabled()
  }

  @Test
  fun interactiveDocumentFullscreenRemainsOpenAfterActivityRecreation() {
    val node =
      DocumentNode(
        id = "full-doc",
        name = "Document",
        kind = BlockKind.DOCUMENT,
        body = com.karandaev.fieldfolio.core.document.runtime.DefaultNotebookDocument,
      )
    show {
      val windows = rememberSession {
        com.karandaev.fieldfolio.core.document.runtime.NotebookWindows()
      }
      val access = rememberSession {
        com.karandaev.fieldfolio.core.document.runtime.DocumentAccessContext(
          "full-report",
          ReportDocument(nodes = listOf(node)),
          emptyMap(),
        )
      }
      CompositionLocalProvider(
        com.karandaev.fieldfolio.core.document.runtime.LocalNotebookWindows provides windows,
        com.karandaev.fieldfolio.core.document.runtime.LocalDocumentAccess provides access,
      ) {
        NotebookDocumentView(node, emptyMap())
        NotebookWindowHost(windows, access)
      }
    }
    compose.onNodeWithContentDescription("Expand document").performClick()
    repeat(2) {
      rotate()
      compose.onNodeWithTag("document-fullscreen").assertIsDisplayed()
    }
  }
}
