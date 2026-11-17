package com.karandaev.fieldfolio.feature.document

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.library.saveDocument
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.DocumentDestinationDialog
import com.karandaev.fieldfolio.feature.document.route.DocumentRoute
import com.karandaev.fieldfolio.feature.document.state.action
import com.karandaev.fieldfolio.feature.document.viewer.ImageResultDialog
import kotlinx.coroutines.runBlocking
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
class DocumentUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var folder by mutableStateOf<String?>(null)
  private var editing by mutableStateOf(false)
  private val document =
    ReportDocument(
      nodes =
        listOf(
          DocumentNode(
            id = "intro",
            name = "Introduction",
            kind = BlockKind.TEXT,
            body = "Visible introduction",
          ),
          DocumentNode(id = "group", order = 1, name = "Survey", kind = BlockKind.FOLDER),
          DocumentNode(
            id = "text",
            parentId = "group",
            name = "Notes",
            kind = BlockKind.TEXT,
            body = "First level content",
          ),
          DocumentNode(
            id = "inner",
            parentId = "group",
            order = 1,
            name = "Inner",
            kind = BlockKind.FOLDER,
          ),
          DocumentNode(
            id = "deep",
            parentId = "inner",
            name = "Details",
            kind = BlockKind.TEXT,
            body = "Deep content",
          ),
        )
    )

  private fun show(edit: Boolean = false) {
    editing = edit
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
    val id = runBlocking {
      app.reportLibrary.saveDocument(
        0,
        null,
        "Inspection document",
        "Summary",
        document,
        emptyMap(),
      )
    }
    compose.setContent {
      MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().padding(top = 24.dp, bottom = 24.dp)) {
            DocumentRoute(
              reportId = id,
              editing = editing,
              owner = compose.activity,
              folderScope = folder,
              onBack = {},
              onSaved = {},
              onEdit = { editing = true },
              onFolder = { folder = it },
              onDelete = {},
            )
          }
        }
      }
    }
    waitText(if (edit) "Introduction" else "Visible introduction")
  }

  private fun waitText(text: String) {
    compose.waitUntil(15000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun screenshot(name: String) {
    compose.waitForIdle()
    compose.runOnIdle {
      val v =
        org.robolectric.shadows.ShadowDialog.getLatestDialog()
          ?.takeIf { it.isShowing }
          ?.window
          ?.decorView ?: compose.activity.window.decorView
      val b =
        android.graphics.Bitmap.createBitmap(
          v.width,
          v.height,
          android.graphics.Bitmap.Config.ARGB_8888,
        )
      v.draw(android.graphics.Canvas(b))
      java.io.File("build/previews-documents").mkdirs()
      java.io.File("build/previews-documents/$name.png").outputStream().use {
        b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
      }
      b.recycle()
    }
  }

  @Test
  fun viewerToolbarOffersExportAndPasswordButEditorHasNoPullToRefresh() {
    show()
    compose.onNodeWithTag("report-pull-refresh").assertExists()
    compose.onNodeWithContentDescription("Export").performClick()
    compose.onNodeWithText("Encrypt the entire archive").assertIsDisplayed()
    compose.onNodeWithContentDescription("Close").performClick()
    compose.onNodeWithContentDescription("Set password").performClick()
    compose.onNodeWithText("Repeat password").assertIsDisplayed()
    compose.onNodeWithContentDescription("Close").performClick()
    compose.onNodeWithContentDescription("Edit").performClick()
    compose.onNodeWithTag("report-pull-refresh").assertDoesNotExist()
  }

  @Test
  fun portraitEditorMovesMetadataIntoSeparatePropertiesWindow() {
    show(true)
    compose.onNodeWithText("Summary").assertDoesNotExist()
    compose.onNodeWithContentDescription("Save").assertIsDisplayed()
    compose.onNodeWithContentDescription("Report properties").performClick()
    compose.onNodeWithText("Summary").assertIsDisplayed()
    compose.onNodeWithContentDescription("Done").performClick()
    compose.onNodeWithText("Summary").assertDoesNotExist()
    compose.onNodeWithTag("document-editor-files").assertIsDisplayed()
  }

  @Test
  fun phoneViewerExpandsOnlyOneLevelAndNavigatesDeeperFolders() {
    show()
    compose.onNodeWithText("First level content").assertDoesNotExist()
    compose.onNodeWithText("Survey").performScrollTo().performClick()
    waitText("First level content")
    compose.onNodeWithText("Deep content").assertDoesNotExist()
    screenshot("viewer-phone-expanded")
    compose.onNodeWithText("Inner").performScrollTo().performClick()
    waitText("Deep content")
    assertEquals("inner", folder)
    compose.onNodeWithText("Document", substring = false).performScrollTo().performClick()
    waitText("Visible introduction")
    assertNull(folder)
    compose.onNodeWithContentDescription("Edit").performClick()
    waitText("Editor")
    compose.onNodeWithText("Visible introduction").assertDoesNotExist()
  }

  @Test
  @Config(qualifiers = "en-rUS-w900dp-h420dp-land-mdpi")
  fun landscapeEditorUsesFullWidthBrowserAndSeparatePreview() {
    show(true)
    compose.onNodeWithContentDescription("Save").assertIsDisplayed()
    val browser = compose.onNodeWithTag("document-file-browser").fetchSemanticsNode().boundsInRoot
    assertTrue(browser.width > compose.activity.resources.displayMetrics.widthPixels * .8f)
    compose.onNodeWithTag("document-tool-rail").assertIsDisplayed()
    compose.onNodeWithTag("document-editor-files").performScrollToNode(hasText("Introduction"))
    compose.onNodeWithText("Introduction").performClick()
    // Text now has the same separate preview and properties stages as other elements.
    compose.onNodeWithContentDescription("Edit").performClick()
    compose.onNodeWithTag("text-properties").assertIsDisplayed()
    compose.onNodeWithContentDescription("Edit with Acode").performScrollTo().performClick()
    compose.onNodeWithTag("acode-editor-window").assertIsDisplayed()
    screenshot("editor-landscape")
  }

  @Test
  @Config(qualifiers = "en-rUS-w900dp-h420dp-land-mdpi")
  fun destinationKeepsActionOutsideTheScrollableFolderList() {
    val many =
      ReportDocument(
        nodes =
          (0..30).map {
            DocumentNode(id = "f$it", name = "Folder $it", kind = BlockKind.FOLDER, order = it)
          } + DocumentNode(id = "source", name = "Source", kind = BlockKind.TEXT)
      )
    var moved = false
    compose.setContent {
      MaterialTheme { DocumentDestinationDialog(many, listOf("source"), null, {}) { moved = true } }
    }
    compose.runOnIdle {
      val insets =
        androidx.core.view.WindowInsetsCompat.Builder()
          .setInsets(
            androidx.core.view.WindowInsetsCompat.Type.statusBars(),
            androidx.core.graphics.Insets.of(0, 24, 0, 0),
          )
          .setVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars(), true)
          .setInsets(
            androidx.core.view.WindowInsetsCompat.Type.navigationBars(),
            androidx.core.graphics.Insets.of(0, 0, 48, 0),
          )
          .setVisible(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), true)
          .build()
      androidx.core.view.ViewCompat.dispatchApplyWindowInsets(
        org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView,
        insets,
      )
    }
    compose.waitForIdle()
    val workspace = compose.onNodeWithTag("overlay-workspace").fetchSemanticsNode().boundsInRoot
    val action = compose.onNodeWithText("Move here").fetchSemanticsNode().boundsInRoot
    assertTrue(action.bottom <= workspace.bottom && action.right <= workspace.right)
    compose.onNodeWithText("Move here").assertIsDisplayed().performClick()
    assertTrue(moved)
    screenshot("destination-landscape")
  }

  @Test
  fun storedResultOverlayCanBeHiddenWithoutRunningAModel() {
    val bitmap =
      android.graphics.Bitmap.createBitmap(640, 320, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.DKGRAY)
    canvas.drawRect(
      60f,
      60f,
      550f,
      240f,
      android.graphics.Paint().apply { color = android.graphics.Color.GRAY },
    )
    val bytes =
      java.io
        .ByteArrayOutputStream()
        .also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        .toByteArray()
    bitmap.recycle()
    val result =
      ModelResultData(
        modelId = "test",
        modelName = "Stored road detector",
        provider = "LOCAL",
        task = "DETECTION",
        boxes = listOf(ResultBox("D00", .94, .15, .25, .75, .8)),
      )
    compose.setContent { MaterialTheme { ImageResultDialog(bytes, result, null, dismiss = {}) } }
    compose.onNodeWithTag("result-overlay-toggle").assertIsOn().performClick().assertIsOff()
    compose.onNodeWithTag("result-overlay-toggle").performClick().assertIsOn()
    screenshot("result-overlay")
  }
}
