package com.karandaev.fieldfolio.feature.document

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.*
import com.google.firebase.*
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.library.saveDocument
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.DocumentFileBrowser
import com.karandaev.fieldfolio.feature.document.editor.TextPropertiesDialog
import com.karandaev.fieldfolio.feature.document.route.DocumentRoute
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import com.karandaev.fieldfolio.feature.document.viewer.DocumentElementCard
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
class TextWorkspaceUiTest {
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
          .setProjectId("local-test")
          .build(),
      )
  }

  private fun waitUntil(check: () -> Boolean) =
    compose.waitUntil(15000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      check()
    }

  private fun snapshot(name: String, dialog: Boolean = true) {
    compose.waitForIdle()
    compose.runOnIdle {
      val view =
        if (dialog) org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        else compose.activity.window.decorView
      val bitmap =
        android.graphics.Bitmap.createBitmap(
          view.width,
          view.height,
          android.graphics.Bitmap.Config.ARGB_8888,
        )
      view.draw(android.graphics.Canvas(bitmap))
      val file = java.io.File("build/previews-v18/$name.png")
      file.parentFile!!.mkdirs()
      file.outputStream().use {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
      }
      bitmap.recycle()
    }
  }

  private fun image(): ByteArray =
    java.io
      .ByteArrayOutputStream()
      .also { out ->
        val b =
          android.graphics.Bitmap.createBitmap(100, 60, android.graphics.Bitmap.Config.ARGB_8888)
        b.eraseColor(android.graphics.Color.GREEN)
        b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        b.recycle()
      }
      .toByteArray()

  private fun route(id: Long = 0, editing: Boolean = false, onFolder: (String?) -> Unit = {}) {
    compose.setContent {
      MaterialTheme {
        DocumentRoute(
          id,
          editing = editing,
          owner = compose.activity,
          onBack = {},
          onSaved = {},
          onEdit = {},
          onFolder = onFolder,
          onDelete = {},
        )
      }
    }
  }

  @Test
  fun headingCanBeClearedAndReenteredAndSyntaxIsSavedWithoutChangingNames() {
    var saved: DocumentNode? = null
    compose.setContent {
      MaterialTheme {
        TextPropertiesDialog(
          DocumentNode(name = "Title", kind = BlockKind.HEADING),
          emptyMap(),
          {},
        ) { n, _ ->
          saved = n
        }
      }
    }
    compose.onNodeWithText("Content source").assertDoesNotExist()
    compose.onNodeWithTag("text-heading-level").performScrollTo().performTextClearance()
    compose.onNodeWithContentDescription("Save").assertIsNotEnabled()
    compose.onNodeWithTag("text-heading-level").performTextInput("3")
    compose.onNodeWithTag("text-syntax-extension").performScrollTo().performTextInput(".js")
    compose.onNodeWithText("Acode").performScrollTo().performClick().assertIsSelected()
    compose.onNodeWithContentDescription("Save").performClick()
    snapshot("text-properties-landscape")
    assertEquals(3, saved!!.headingLevel)
    assertEquals("js", saved!!.syntaxExtension)
    assertEquals("Title", saved!!.name)
  }

  @Test
  fun editorReceivesExplicitSyntaxAndShowsItsExtensionHint() {
    compose.setContent {
      MaterialTheme {
        TextPropertiesDialog(
          DocumentNode(
            name = "Script",
            kind = BlockKind.TEXT,
            syntaxExtension = "js",
            body = "const value=1",
          ),
          emptyMap(),
          {},
        ) { _, _ ->
        }
      }
    }
    compose.onNodeWithContentDescription("Edit with Acode").performScrollTo().performClick()
    compose.onNodeWithTag("acode-editor-window").assertIsDisplayed()
    compose.onNodeWithText(".js").assertExists()
  }

  @Test
  fun editorItemsKeepTheSameTextStartHeightAndCenteredLeadingSlotWithOrWithoutCover() {
    val vm = DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
    val folder = vm.add(BlockKind.FOLDER, "Folder")
    vm.addFile(image(), "Image.png", "image/png", BlockKind.IMAGE)
    val pic = vm.document.nodes.single { it.kind == BlockKind.IMAGE }
    compose.setContent { MaterialTheme { DocumentFileBrowser(vm, null, vm::navigate, {}, {}, {}) } }
    waitUntil {
      compose
        .onAllNodesWithTag("thumbnail-${pic.id}", useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    val title =
      compose
        .onNode(
          hasTestTag("vfs-item-title") and hasAnyAncestor(hasTestTag("document-node-${folder.id}")),
          useUnmergedTree = true,
        )
        .fetchSemanticsNode()
        .boundsInRoot
    val imageTitle =
      compose
        .onNode(
          hasTestTag("vfs-item-title") and hasAnyAncestor(hasTestTag("document-node-${pic.id}")),
          useUnmergedTree = true,
        )
        .fetchSemanticsNode()
        .boundsInRoot
    assertEquals(title.left, imageTitle.left, .5f)
    val one = compose.onNodeWithTag("document-node-${folder.id}").fetchSemanticsNode().boundsInRoot
    val two = compose.onNodeWithTag("document-node-${pic.id}").fetchSemanticsNode().boundsInRoot
    val cover =
      compose
        .onNodeWithTag("thumbnail-${pic.id}", useUnmergedTree = true)
        .fetchSemanticsNode()
        .boundsInRoot
    assertEquals(one.height, two.height, .5f)
    assertEquals(two.center.y, cover.center.y, .5f)
    snapshot("editor-vfs-rows", false)
  }

  @Test
  fun newReportCoverDoesNotOfferAnUncreatedDocumentFilesystem() {
    route(editing = true)
    compose.onNodeWithContentDescription("Add cover").performClick()
    compose.onNodeWithTag("image-input-workspace").assertExists()
    compose.onNodeWithContentDescription("Choose an image block").assertDoesNotExist()
  }

  @Test
  fun viewerPathOpensFoldersAndKeepsItsDialogForRejectedLeavesAndHiddenFolders() {
    val doc =
      ReportDocument(
        nodes =
          listOf(
            DocumentNode(id = "f", name = "Folder", kind = BlockKind.FOLDER),
            DocumentNode(id = "t", name = "Text", kind = BlockKind.TEXT, body = "Body"),
            DocumentNode(id = "h", name = "Hidden", kind = BlockKind.FOLDER, visible = false),
          )
      )
    val id =
      kotlinx.coroutines.runBlocking {
        app.reportLibrary.saveDocument(0, null, "Report", "", doc, emptyMap())
      }
    var target: String? = "unchanged"
    route(id, onFolder = { target = it })
    waitUntil {
      compose.onAllNodesWithContentDescription("Go to path").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithContentDescription("Go to path").performClick()
    for (path in listOf("/Text", "/Hidden")) {
      compose.onNodeWithTag("viewer-path").performTextReplacement(path)
      compose.onNodeWithContentDescription("Open").performClick()
      assertEquals("unchanged", target)
      compose.onNodeWithTag("viewer-path").assertExists()
    }
    compose.onNodeWithTag("viewer-path").performTextReplacement("/Folder")
    compose.onNodeWithContentDescription("Open").performClick()
    assertEquals("f", target)
    compose.onNodeWithTag("viewer-path").assertDoesNotExist()
  }

  @Test
  fun recycledTextCardRetainsItsHydratedContentWithoutAnEmptyLayoutFrame() {
    var mounted by mutableStateOf(true)
    val session = DocumentAudioSession()
    val n =
      DocumentNode(name = "Text", kind = BlockKind.TEXT, body = "A long text preview. ".repeat(200))
    compose.setContent {
      MaterialTheme {
        com.karandaev.fieldfolio.core.ui.state.ViewerSession("stable-cell") {
          if (mounted) DocumentElementCard(n, emptyMap(), null, 0, session, {})
        }
      }
    }
    val before =
      compose.onNodeWithTag("report-cell-${n.id}").fetchSemanticsNode().boundsInRoot.height
    compose.runOnIdle { mounted = false }
    compose.waitForIdle()
    compose.runOnIdle { mounted = true }
    val after =
      compose.onNodeWithTag("report-cell-${n.id}").fetchSemanticsNode().boundsInRoot.height
    assertEquals(before, after, .5f)
    session.close()
  }

  @Test
  fun reportsUseTheSameTextStartHeightAndCoverCenterAsFolders() {
    val ids =
      kotlinx.coroutines.runBlocking {
        val folder = app.reportLibrary.createFolder("Folder", null)
        val file =
          app.reportLibrary.saveDocument(
            0,
            null,
            "Image report",
            "",
            ReportDocument(cover = "resources/doc/cover.png"),
            mapOf("resources/doc/cover.png" to image()),
          )
        folder to file
      }
    val vm = com.karandaev.fieldfolio.feature.library.LibraryViewModel(app.reportLibrary)
    compose.setContent {
      MaterialTheme { com.karandaev.fieldfolio.feature.library.LibraryBrowser(vm, {}, {}, {}) }
    }
    waitUntil {
      compose
        .onAllNodesWithTag("library-cover-${ids.second}", useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    fun title(id: Long) =
      compose
        .onNode(
          hasTestTag("vfs-item-title") and hasAnyAncestor(hasTestTag("library-node-$id")),
          useUnmergedTree = true,
        )
        .fetchSemanticsNode()
        .boundsInRoot
    val one = compose.onNodeWithTag("library-node-${ids.first}").fetchSemanticsNode().boundsInRoot
    val two = compose.onNodeWithTag("library-node-${ids.second}").fetchSemanticsNode().boundsInRoot
    val cover =
      compose
        .onNodeWithTag("library-cover-${ids.second}", useUnmergedTree = true)
        .fetchSemanticsNode()
        .boundsInRoot
    assertEquals(title(ids.first).left, title(ids.second).left, .5f)
    assertEquals(one.height, two.height, .5f)
    assertEquals(two.center.y, cover.center.y, .5f)
    snapshot("reports-vfs-rows", false)
  }

  @Test
  fun realViewerMasonryReturnsToTheSameHydratedFilePositionsAfterScrolling() {
    val nodes =
      (0..45).map { i ->
        DocumentNode(
          id = "n$i",
          name = "File $i",
          kind = BlockKind.TEXT_FILE,
          asset = "resources/doc/$i.txt",
          order = i,
        )
      }
    val files =
      nodes
        .mapIndexed { i, n ->
          n.asset!! to (0..i % 6).joinToString("\n") { "File $i line $it" }.toByteArray()
        }
        .toMap()
    val id =
      kotlinx.coroutines.runBlocking {
        app.reportLibrary.saveDocument(0, null, "Report", "", ReportDocument(nodes = nodes), files)
      }
    ReportViewPreferences.setHeaderVisible(compose.activity, id, false)
    route(id)
    fun hydrated(i: Int) =
      compose
        .onAllNodes(
          hasTestTag("collapsed-document-text") and hasAnyAncestor(hasTestTag("report-cell-n$i"))
        )
        .fetchSemanticsNodes()
        .isNotEmpty()
    waitUntil { hydrated(0) && hydrated(1) }
    fun bounds(i: Int) = compose.onNodeWithTag("report-cell-n$i").fetchSemanticsNode().boundsInRoot
    snapshot("viewer-masonry", false)
    val first = bounds(0)
    val second = bounds(1)
    repeat(2) {
      compose.onNodeWithTag("report-view-grid").performScrollToIndex(30)
      waitUntil { hydrated(30) }
      compose.onNodeWithTag("report-view-grid").performScrollToIndex(0)
      waitUntil { hydrated(0) && hydrated(1) }
      for ((i, old) in listOf(0 to first, 1 to second)) {
        val now = bounds(i)
        assertEquals(old.left, now.left, 1f)
        assertEquals(old.top, now.top, 1f)
        assertEquals(old.height, now.height, 1f)
      }
    }
  }
}
