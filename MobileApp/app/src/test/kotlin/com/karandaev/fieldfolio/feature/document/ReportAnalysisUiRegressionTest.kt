package com.karandaev.fieldfolio.feature.document

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.firebase.*
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.scroll.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.DocumentElementPreviewDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentFileBrowser
import com.karandaev.fieldfolio.feature.document.editor.DocumentNodeDialog
import com.karandaev.fieldfolio.feature.document.editor.NodeIdentityDialog
import com.karandaev.fieldfolio.feature.document.route.DocumentRoute
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import com.karandaev.fieldfolio.feature.library.*
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
class ReportAnalysisUiRegressionTest {
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

  private fun waitUntil(test: () -> Boolean) =
    compose.waitUntil(15000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      test()
    }

  private fun image() =
    java.io
      .ByteArrayOutputStream()
      .also { out ->
        android.graphics.Bitmap.createBitmap(100, 60, android.graphics.Bitmap.Config.ARGB_8888)
          .apply { eraseColor(android.graphics.Color.GREEN) }
          .let {
            it.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            it.recycle()
          }
      }
      .toByteArray()

  private fun snapshot(name: String) {
    compose.waitForIdle()
    compose.runOnIdle {
      val view = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
      val bitmap =
        android.graphics.Bitmap.createBitmap(
          view.width,
          view.height,
          android.graphics.Bitmap.Config.ARGB_8888,
        )
      view.draw(android.graphics.Canvas(bitmap))
      val file = java.io.File("build/previews-v16/$name.png")
      file.parentFile!!.mkdirs()
      file.outputStream().use {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
      }
      bitmap.recycle()
    }
  }

  @Test
  fun locationHasTwoBoundedPanesAndTopBarToggles() {
    val node =
      DocumentNode(name = "Location", kind = BlockKind.LOCATION, latitude = 10.0, longitude = 20.0)
    compose.setContent { MaterialTheme { DocumentNodeDialog(node, emptyMap(), {}) { _, _ -> } } }
    val name = compose.onNodeWithTag("resource-name").fetchSemanticsNode().boundsInRoot
    val preview = compose.onNodeWithTag("element-preview").fetchSemanticsNode().boundsInRoot
    val toggle = compose.onNodeWithTag("resource-visible").fetchSemanticsNode().boundsInRoot
    assertTrue(name.right < preview.left)
    assertTrue(toggle.bottom <= name.top)
    val map = compose.onNodeWithTag("native-map-frame").fetchSemanticsNode().boundsInRoot
    assertTrue(map.height > 250f)
    assertTrue(map.bottom <= 420f)
    compose.onNodeWithContentDescription("Save").assertIsEnabled()
    snapshot("location-landscape")
  }

  @Test
  fun weatherUsesTheSameTwoPanePropertiesAndCenteredPreview() {
    val node =
      DocumentNode(name = "Weather", kind = BlockKind.WEATHER, latitude = 10.0, longitude = 20.0)
    compose.setContent { MaterialTheme { DocumentNodeDialog(node, emptyMap(), {}) { _, _ -> } } }
    val name = compose.onNodeWithTag("resource-name").fetchSemanticsNode().boundsInRoot
    val preview = compose.onNodeWithTag("element-preview").fetchSemanticsNode().boundsInRoot
    assertTrue(name.right < preview.left)
    compose.onNodeWithTag("resource-hide-title").assertExists()
    compose.onNodeWithTag("resource-visible").assertExists()
    snapshot("weather-landscape")
  }

  @Test
  fun renameDoesNotOfferVisibilityButFolderCreationDoes() {
    var creating by mutableStateOf(false)
    val node = DocumentNode(name = "Folder", kind = BlockKind.FOLDER)
    compose.setContent { MaterialTheme { NodeIdentityDialog(node, {}, creating) {} } }
    compose.onNodeWithTag("resource-hide-title").assertDoesNotExist()
    compose.onNodeWithTag("resource-visible").assertDoesNotExist()
    compose.runOnIdle { creating = true }
    compose.onNodeWithTag("resource-visible").assertExists()
  }

  @Test
  fun headingWithEmptyBodyCanSaveAndHasPreview() {
    var saved: DocumentNode? = null
    compose.setContent {
      MaterialTheme {
        DocumentNodeDialog(
          DocumentNode(name = "Title", kind = BlockKind.HEADING),
          emptyMap(),
          {},
        ) { n, _ ->
          saved = n
        }
      }
    }
    compose.onNodeWithContentDescription("Save").assertIsEnabled()
    compose.onNodeWithContentDescription("View").assertIsDisplayed()
    compose.onNodeWithContentDescription("Save").performClick()
    assertEquals("", saved!!.body)
    assertEquals("Title", saved!!.name)
  }

  @Test
  fun modelResultStartsWithDocumentPropertiesAndExecutionHasNoMetadataFields() {
    compose.setContent {
      MaterialTheme {
        DocumentRoute(
          editing = true,
          owner = compose.activity,
          onBack = {},
          onSaved = {},
          onEdit = {},
          onFolder = {},
          onDelete = {},
        )
      }
    }
    compose.onNodeWithTag("report-logical-name").performTextInput("Report")
    compose.onNodeWithContentDescription("Done").performClick()
    compose.onNodeWithContentDescription("Add").performClick()
    compose.onNodeWithText("Model result").performScrollTo().performClick()
    compose.onNodeWithTag("resource-name").assertIsDisplayed()
    compose.onNodeWithTag("resource-display-name").performTextReplacement("My analysis")
    compose.onNodeWithTag("resource-hide-title").performClick()
    compose.onNodeWithTag("resource-visible").performClick()
    compose
      .onNode(hasContentDescription("Save") and hasAnyAncestor(hasTestTag("node-properties")))
      .assertIsNotEnabled()
    compose.onNodeWithContentDescription("Choose model").performScrollTo().performClick()
    compose.onNodeWithTag("model-catalog").assertExists()
    compose.onNodeWithTag("model-card-road-local").performClick()
    compose.onNodeWithTag("analysis-workspace").assertExists()
    compose.onNodeWithTag("resource-name").assertDoesNotExist()
    compose.onNodeWithTag("resource-display-name").assertDoesNotExist()
    compose.onNodeWithTag("resource-visible").assertDoesNotExist()
    compose
      .onNode(hasContentDescription("Back") and hasAnyAncestor(hasTestTag("analysis-workspace")))
      .performClick()
    compose.onNodeWithTag("resource-display-name").assertTextContains("My analysis")
    compose.onNodeWithTag("resource-hide-title").assertIsOn()
    compose.onNodeWithTag("resource-visible").assertIsOff()
    val vm =
      androidx.lifecycle
        .ViewModelProvider(
          compose.activity,
          androidx.lifecycle.ViewModelProvider.NewInstanceFactory(),
        )["document-0-null", DocumentViewModel::class.java]
    assertNull(vm.document.nodes.single().displayName)
    assertEquals("My analysis", vm.modelDraft!!.displayName)
  }

  @Test
  fun stagedModelResultsStayPrivateUntilSaveAndCancelPreservesThePreviousSnapshot() = runBlocking {
    val vm = DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
    val n = vm.add(BlockKind.MODEL_RESULT, "Analysis")
    val before = vm.document
    vm.stageModelProperties(
      n.copy(displayName = "Alias", body = "Annotation", visible = false, hideTitle = true)
    )
    val input = byteArrayOf(1, 2, 3)
    vm.stageAnalysisResult(
      ModelExecution(
        ModelResultData(
          modelId = "model",
          modelName = "Model",
          provider = "LOCAL",
          task = "DETECTION",
        )
      ),
      input,
    )
    assertEquals(before, vm.document)
    val path = vm.modelDraft!!.result!!.inputAsset!!
    input.fill(0)
    assertArrayEquals(byteArrayOf(1, 2, 3), vm.modelFiles[path])
    vm.commitModelDraft(vm.modelDraft!!)
    val stored = vm.document
    assertEquals("Annotation", stored.nodes.single().body)
    assertFalse(stored.nodes.single().visible)
    assertTrue(stored.nodes.single().hideTitle)
    vm.stageModelProperties(stored.nodes.single().copy(body = "Discard"))
    vm.stageAnalysisResult(
      ModelExecution(
        ModelResultData(modelId = "other", modelName = "Other", provider = "LOCAL", task = "TEXT")
      ),
      null,
    )
    vm.discardModelDraft()
    assertEquals(stored, vm.document)
    vm.discardForLock()
  }

  @Test
  fun editorToolbarCanCancelSelectionRenameToggleAndClearClipboard() {
    val vm = DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
    vm.add(BlockKind.TEXT, "First")
    vm.add(BlockKind.TEXT, "Second")
    compose.setContent { MaterialTheme { DocumentFileBrowser(vm, null, vm::navigate, {}, {}, {}) } }
    compose.onNodeWithTag("vfs-select_all").performClick()
    compose.onNodeWithTag("vfs-select_all").assertExists()
    compose.onNodeWithTag("vfs-clear_selection").assertExists()
    compose.onNodeWithTag("vfs-rename").assertDoesNotExist()
    compose.onNodeWithTag("vfs-clear_selection").performClick()
    compose.onNodeWithText("First").performTouchInput { longClick() }
    compose.onNodeWithTag("vfs-rename").assertExists()
    compose.onNodeWithTag("vfs-title").performClick()
    compose.onNodeWithTag("vfs-visible").performClick()
    assertTrue(vm.document.nodes.first().hideTitle)
    assertFalse(vm.document.nodes.first().visible)
    compose.onNodeWithTag("vfs-copy").performScrollTo().performClick()
    compose.onNodeWithTag("vfs-clear_clipboard").performScrollTo().performClick()
    assertNull(vm.clipboard)
    compose.onNodeWithTag("vfs-clear_clipboard").assertDoesNotExist()
  }

  @Test
  fun reportsToolbarKeepsSelectAllAndHasRenameAndClearClipboard() {
    val ids = runBlocking {
      listOf(
        app.reportLibrary.createFolder("First", null),
        app.reportLibrary.createFolder("Second", null),
      )
    }
    val vm = LibraryViewModel(app.reportLibrary)
    compose.setContent { MaterialTheme { LibraryBrowser(vm, {}, {}, {}) } }
    waitUntil { !vm.loading && vm.nodes.any { it.id == ids.last() } }
    compose.onNodeWithTag("vfs-select_all").performClick()
    compose.onNodeWithTag("vfs-select_all").assertExists()
    compose.onNodeWithTag("vfs-clear_selection").performClick()
    compose.runOnIdle { vm.selected = setOf(ids.first()) }
    compose.onNodeWithTag("vfs-rename").assertExists()
    compose.onNodeWithTag("vfs-copy").performScrollTo().performClick()
    compose.onNodeWithTag("vfs-clear_clipboard").performScrollTo().performClick()
    assertNull(vm.clipboard)
  }

  @Test
  fun imagePreviewHasRenameAndPresentationButtons() {
    var node by
      mutableStateOf(DocumentNode(name = "Image", kind = BlockKind.IMAGE, asset = "image"))
    val session = DocumentAudioSession()
    compose.setContent {
      MaterialTheme {
        DocumentElementPreviewDialog(
          node,
          mapOf("image" to image()),
          null,
          0,
          session,
          {},
          {},
          update = { node = it },
        )
      }
    }
    compose.onNodeWithContentDescription("Rename").assertExists()
    compose.onNodeWithTag("resource-hide-title").performClick()
    compose.onNodeWithTag("resource-visible").performClick()
    assertTrue(node.hideTitle)
    assertFalse(node.visible)
    session.close()
  }

  @Test
  fun modelPreviewUsesTheReportCardForImagesAndReadableOutputForNumbers() {
    val bytes = image()
    var result by
      mutableStateOf(
        ModelResultData(
          modelId = "one",
          modelName = "Classifier",
          provider = "LOCAL",
          task = "CLASSIFICATION",
          inputAsset = "image",
          scores = listOf(ResultScore("Class A", .8)),
        )
      )
    val session = DocumentAudioSession()
    compose.setContent {
      MaterialTheme {
        DocumentElementPreviewDialog(
          DocumentNode(name = "Result", kind = BlockKind.MODEL_RESULT, result = result),
          mapOf("image" to bytes),
          null,
          0,
          session,
          {},
          {},
        )
      }
    }
    compose.onNodeWithTag("document-image-preview").assertExists()
    compose.onNodeWithTag("media-landscape").assertDoesNotExist()
    compose.onAllNodesWithContentDescription("View").assertCountEquals(0)
    snapshot("model-result-card-landscape")
    compose.runOnIdle {
      result =
        ModelResultData(
          modelId = "two",
          modelName = "Regressor",
          provider = "LOCAL",
          task = "REGRESSION",
          inputKind = "NUMBERS",
          values = listOf(ResultValue("Estimate", -2.5, "m")),
        )
    }
    compose.onNodeWithText("Estimate").assertExists()
    compose.onNodeWithText("-2.50000 m").assertExists()
    compose.onNodeWithTag("media-image-pane").assertDoesNotExist()
    session.close()
  }
}
