package com.karandaev.fieldfolio.feature.document

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.ui.components.fields.ResourceNameDialog
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.DocumentFileBrowser
import com.karandaev.fieldfolio.feature.document.editor.ReportPropertiesDialog
import com.karandaev.fieldfolio.feature.document.route.DocumentRoute
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import com.karandaev.fieldfolio.feature.document.viewer.ImageResultDialog
import com.karandaev.fieldfolio.feature.library.*
import com.karandaev.fieldfolio.feature.models.AnalysisCropDialog
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
class PolishUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val app
    get() = compose.activity.application as MyApplication

  @Before
  fun firebase() {
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

  private fun waitFor(tag: String) =
    compose.waitUntil(15000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

  private fun image(): ByteArray {
    val b = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
    b.eraseColor(android.graphics.Color.GRAY)
    return java.io
      .ByteArrayOutputStream()
      .also {
        b.compress(Bitmap.CompressFormat.PNG, 100, it)
        b.recycle()
      }
      .toByteArray()
  }

  private fun snapshot(name: String) {
    compose.waitForIdle()
    compose.runOnIdle {
      val view =
        org.robolectric.shadows.ShadowDialog.getLatestDialog()
          ?.takeIf { it.isShowing }
          ?.window
          ?.decorView ?: compose.activity.window.decorView
      val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      view.draw(android.graphics.Canvas(b))
      val dir = java.io.File("build/polish-previews").apply { mkdirs() }
      java.io.File(dir, "$name.png").outputStream().use {
        b.compress(Bitmap.CompressFormat.PNG, 100, it)
      }
      b.recycle()
    }
  }

  @Test
  fun plainImagePreviewUsesTheWholeWidthAndCropRetainsPinnedLandscapeControls() {
    compose.setContent { MaterialTheme { ImageResultDialog(image(), null, null) {} } }
    val pane = compose.onNodeWithTag("media-image-pane").fetchSemanticsNode().boundsInRoot
    assertTrue(pane.width > 850f)
    compose.onNodeWithContentDescription("Reset zoom").assertIsDisplayed()
    snapshot("plain-image-landscape")
  }

  @Test
  fun cropWindowHasTwoPanesAndVisibleApplyControls() {
    compose.setContent { MaterialTheme { AnalysisCropDialog(image(), {}, {}) } }
    compose.onNodeWithTag("media-landscape").assertExists()
    val left = compose.onNodeWithTag("media-controls-pane").fetchSemanticsNode().boundsInRoot
    val right = compose.onNodeWithTag("media-image-pane").fetchSemanticsNode().boundsInRoot
    assertTrue(right.left >= left.right)
    compose.onNodeWithContentDescription("Apply").assertIsDisplayed()
    snapshot("crop-landscape")
  }

  @Test
  fun sharedRenameCanRemoveExtensionWithoutSavingTheOriginalUntilConfirmed() {
    var saved = "photo.jpg"
    compose.setContent {
      MaterialTheme { ResourceNameDialog(saved, {}, file = true) { saved = it } }
    }
    compose.onNodeWithContentDescription("Remove file extension from name").performClick()
    assertEquals("photo.jpg", saved)
    compose.onNodeWithTag("library-name").assertTextContains("photo")
    compose.onNodeWithText("Save").performClick()
    assertEquals("photo", saved)
    val field = compose.onNodeWithTag("library-name").fetchSemanticsNode().boundsInRoot
    assertTrue(field.width > 300f)
  }

  @Test
  fun reportHeaderIsFirstCardBelowBreadcrumbsAndHiddenPreferenceSurvivesReentry() {
    val doc =
      ReportDocument(
        nodes =
          listOf(
            DocumentNode(id = "folder", name = "Data", kind = BlockKind.FOLDER),
            DocumentNode(id = "one", name = "First content", kind = BlockKind.TEXT, body = "a"),
            DocumentNode(
              id = "two",
              order = 1,
              name = "Second content",
              kind = BlockKind.TEXT,
              body = "b",
            ),
          )
      )
    val id = runBlocking {
      app.reportLibrary.saveDocument(0, null, "Header metadata", "Description", doc, emptyMap())
    }
    var entered by mutableStateOf(true)
    compose.setContent {
      MaterialTheme {
        if (entered)
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
    waitFor("report-header")
    compose.onNodeWithContentDescription("Hide report header").performClick()
    compose.onNodeWithTag("report-header").assertDoesNotExist()
    compose.waitUntil(5000) { ReportViewPreferences.headerVisible(app, id) == false }
    compose.runOnIdle { entered = false }
    compose.waitForIdle()
    compose.runOnIdle { entered = true }
    compose.waitForIdle()
    compose.onNodeWithTag("report-header").assertDoesNotExist()
    // Re-entry loads persisted presentation asynchronously; wait for that result, not just
    // composition idleness.
    compose.waitUntil(5000) {
      compose
        .onAllNodesWithContentDescription("Show report header")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    compose.onNodeWithContentDescription("Show report header").performClick()
    compose.onNodeWithTag("report-header").assertExists()
    snapshot("masonry-landscape")
  }

  @Test
  fun propertiesHaveTwoIndependentColumnsAndNameFieldHasNoCodeButton() {
    val vm = DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
    vm.title = "Report"
    vm.addFile(image(), "cover.png", "image/png", BlockKind.IMAGE, true)
    compose.setContent { MaterialTheme { ReportPropertiesDialog(vm, {}, {}, {}) } }
    val title = compose.onNodeWithTag("report-logical-name").fetchSemanticsNode().boundsInRoot
    val cover = compose.onNodeWithTag("document-image-preview").fetchSemanticsNode().boundsInRoot
    assertTrue(cover.left > title.right)
    compose.onNodeWithContentDescription("Edit with Acode").assertIsDisplayed()
    snapshot("properties-landscape")
  }

  @Test
  fun documentContextMenuRenamesFoldersAndFilesAndGoToPathResolvesFolders() {
    val vm = DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
    val folder = vm.add(BlockKind.FOLDER, "Target")
    vm.addFile("abc".toByteArray(), "note.txt", "text/plain", BlockKind.TEXT_FILE)
    compose.setContent { MaterialTheme { DocumentFileBrowser(vm, null, vm::navigate, {}, {}, {}) } }
    compose.onAllNodesWithContentDescription("Actions")[0].performClick()
    compose.onNodeWithText("Rename").performClick()
    compose.onNodeWithTag("library-name").performTextReplacement("Renamed folder")
    compose.onNodeWithText("Save").performClick()
    assertEquals("Renamed folder", vm.tree.node(folder.id).name)
    compose.onNodeWithContentDescription("Go to path").performClick()
    compose.onNodeWithTag("document-path").performTextReplacement("/Renamed%20folder")
    compose.onNodeWithContentDescription("Open").performClick()
    assertEquals(folder.id, vm.folder)
  }

  @Test
  fun editorChangesAnImportedCopyAndLeavesBothExternalFileAndOriginalAssetUntouched() {
    val external =
      java.io.File(app.cacheDir, "external-edit-regression.txt").apply {
        writeText("source\r\ntext\r\n")
      }
    val vm = DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
    vm.addFile(external.readBytes(), external.name, "text/plain", BlockKind.TEXT_FILE)
    val node = vm.document.nodes.single()
    vm.editTextFile(node, "changed\r\ntext\r\n")
    compose.waitUntil(10000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      !vm.busy
    }
    assertEquals("source\r\ntext\r\n", external.readText())
    assertEquals("source\r\ntext\r\n", vm.files[node.asset]!!.toString(Charsets.UTF_8))
    val changed = vm.document.nodes.single()
    assertNotEquals(node.asset, changed.asset)
    assertEquals("changed\r\ntext\r\n", vm.files[changed.asset]!!.toString(Charsets.UTF_8))
  }

  @Test
  fun masonryPlacesTheNextCellDirectlyUnderTheShorterColumn() {
    compose.setContent {
      MaterialTheme {
        com.karandaev.fieldfolio.core.ui.scroll.AppLazyStaggeredGrid(2, Modifier.fillMaxSize()) {
          item { Box(Modifier.height(300.dp).fillMaxWidth().testTag("tall")) }
          item { Box(Modifier.height(70.dp).fillMaxWidth().testTag("short")) }
          item { Box(Modifier.height(80.dp).fillMaxWidth().testTag("next")) }
        }
      }
    }
    val short = compose.onNodeWithTag("short").fetchSemanticsNode().boundsInRoot
    val next = compose.onNodeWithTag("next").fetchSemanticsNode().boundsInRoot
    assertEquals(short.left, next.left, .5f)
    assertEquals(short.bottom + 12f, next.top, .5f)
    assertTrue(next.top < compose.onNodeWithTag("tall").fetchSemanticsNode().boundsInRoot.bottom)
  }

  @Test
  fun cropCanAlwaysRestoreTheUntouchedSourceAfterApplyingACrop() {
    val initial = image()
    val expected = initial.copyOf()
    var bytes by mutableStateOf<ByteArray?>(initial)
    compose.setContent {
      MaterialTheme {
        com.karandaev.fieldfolio.feature.media.ImageInputToolbar(
          bytes,
          onImage = { next, _ ->
            bytes?.fill(0)
            bytes = next
          },
        )
      }
    }
    compose.onNodeWithContentDescription("Crop image").performClick()
    try {
      compose.waitUntil(15000) {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        compose
          .onAllNodes(hasContentDescription("Apply") and isEnabled())
          .fetchSemanticsNodes()
          .isNotEmpty()
      }
    } catch (e: Throwable) {
      compose.onAllNodes(isRoot()).printToLog("CROP_FAILURE")
      snapshot("crop-failure")
      throw e
    }
    compose.onNodeWithContentDescription("Apply").performClick()
    compose.waitUntil(10000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      compose.onAllNodesWithContentDescription("Apply").fetchSemanticsNodes().isEmpty()
    }
    compose.onNodeWithContentDescription("Restore original image").performClick()
    compose.waitUntil(10000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      bytes?.contentEquals(expected) == true
    }
    assertArrayEquals(expected, bytes)
  }

  @Test
  fun reportRowUsesABoundedCoverAndProvidesDirectEditorAction() {
    val cover = image()
    val id = runBlocking {
      app.reportLibrary.saveDocument(
        0,
        null,
        "Covered report",
        "",
        ReportDocument(cover = "resources/cover.png"),
        mapOf("resources/cover.png" to cover),
      )
    }
    val vm = LibraryViewModel(app.reportLibrary)
    var edited: Long? = null
    compose.setContent { MaterialTheme { LibraryBrowser(vm, {}, { edited = it }, {}) } }
    compose.waitUntil(15000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      compose
        .onAllNodesWithTag("library-cover-$id", useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    val image =
      compose
        .onNodeWithTag("library-cover-$id", useUnmergedTree = true)
        .fetchSemanticsNode()
        .boundsInRoot
    assertEquals(44f, image.width, .5f)
    assertEquals(44f, image.height, .5f)
    compose
      .onNode(hasContentDescription("Edit") and hasAnyAncestor(hasTestTag("library-node-$id")))
      .performClick()
    assertEquals(id, edited)
  }

  @Test
  fun wideScrollbarHitTargetDoesNotStealTrailingActionButtons() {
    var clicked = false
    compose.setContent {
      MaterialTheme {
        com.karandaev.fieldfolio.core.ui.scroll.AppLazyColumn(Modifier.fillMaxSize()) {
          items(20) { index ->
            Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.End) {
              IconButton({ clicked = true }, Modifier.testTag("edge-action-$index")) { Text("+") }
            }
          }
        }
      }
    }
    compose.onNodeWithTag("edge-action-0").performClick()
    assertTrue(clicked)
  }
}
