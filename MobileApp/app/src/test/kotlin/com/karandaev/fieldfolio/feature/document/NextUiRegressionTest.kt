package com.karandaev.fieldfolio.feature.document

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
import com.karandaev.fieldfolio.core.library.saveDocument
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.names.ResourceIdentity
import com.karandaev.fieldfolio.core.ui.components.fields.ResourceIdentityDialog
import com.karandaev.fieldfolio.core.ui.scroll.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.AudioPropertiesDialog
import com.karandaev.fieldfolio.feature.document.editor.ClockPropertiesDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentNodeDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentNodeEditor
import com.karandaev.fieldfolio.feature.document.editor.ReportPropertiesDialog
import com.karandaev.fieldfolio.feature.document.route.DocumentRoute
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import com.karandaev.fieldfolio.feature.document.viewer.DocumentBlockBody
import com.karandaev.fieldfolio.feature.document.viewer.icon
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
class NextUiRegressionTest {
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

  @Test
  fun nativeNodePropertiesEditTheFullIdentity() {
    var saved: DocumentNode? = null
    val node = DocumentNode(name = "Logical", kind = BlockKind.TEXT, body = "Body")
    compose.setContent {
      MaterialTheme { DocumentNodeDialog(node, emptyMap(), {}) { next, _ -> saved = next } }
    }
    compose.onNodeWithTag("resource-display-name").performTextReplacement("Alias")
    compose.onNodeWithTag("resource-hide-title").performClick()
    compose.onNodeWithTag("resource-visible").performClick()
    compose.onNodeWithContentDescription("Save").performClick()
    assertEquals("Alias", saved!!.displayName)
    assertEquals("Logical", saved!!.name)
    assertTrue(saved!!.hideTitle)
    assertFalse(saved!!.visible)
  }

  @Test
  fun emptyDisplayTitleNeverAllowsAnEmptyLogicalName() {
    compose.setContent {
      MaterialTheme { ResourceIdentityDialog(ResourceIdentity("Keep"), {}, presentation = true) {} }
    }
    compose.onNodeWithTag("resource-hide-title").performClick()
    compose.onNodeWithTag("library-name").performScrollTo().performTextReplacement(" ")
    compose.onNodeWithText("Save").assertIsNotEnabled()
  }

  @Test
  fun audioAndClockUseTheSamePresentationControls() {
    var saved: DocumentNode? = null
    var clock by mutableStateOf(false)
    compose.setContent {
      MaterialTheme {
        val node =
          DocumentNode(
            id = "one",
            name = "Logical",
            kind = if (clock) BlockKind.CLOCK else BlockKind.AUDIO,
            deviceLocation = true,
          )
        if (clock) ClockPropertiesDialog(node, {}) { next, _ -> saved = next }
        else AudioPropertiesDialog(node, emptyMap(), {}) { next, _ -> saved = next }
      }
    }
    compose.onNodeWithTag("resource-display-name").performTextReplacement("Track alias")
    compose.onNodeWithContentDescription("Save").performClick()
    assertEquals("Track alias", saved!!.displayName)
    compose.runOnIdle { clock = true }
    compose.onNodeWithTag("resource-hide-title").performClick()
    compose.onNodeWithTag("resource-visible").performClick()
    compose.onNodeWithContentDescription("Save").performClick()
    assertTrue(saved!!.hideTitle)
    assertFalse(saved!!.visible)
  }

  @Test
  fun creatingAFolderDoesNotPresentItAsRename() {
    compose.setContent {
      MaterialTheme {
        DocumentNodeEditor(
          DocumentNode(name = "Folder", kind = BlockKind.FOLDER),
          emptyMap(),
          {},
          creating = true,
        ) { _, _ ->
        }
      }
    }
    compose.onNodeWithText("Add · Folder").assertIsDisplayed()
    compose.onNodeWithText("Rename").assertDoesNotExist()
    compose.onNodeWithTag("resource-display-name").assertExists()
  }

  @Test
  fun newReportPropertiesUseCreationTitleAndAcceptBothNames() {
    val vm = DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
    compose.setContent { MaterialTheme { ReportPropertiesDialog(vm, {}, {}, {}) } }
    compose.onNodeWithText("New report").assertIsDisplayed()
    compose.onNodeWithText("Report properties").assertDoesNotExist()
    compose.onNodeWithTag("report-logical-name").performTextReplacement("Logical report")
    compose.onNodeWithTag("resource-display-name").performTextReplacement("Visible report")
    compose.onNodeWithContentDescription("Done").assertIsEnabled()
    assertEquals("Logical report", vm.logicalName)
    assertEquals("Visible report", vm.displayName)
  }

  @Test
  fun exportUsesTheAdaptiveWindowAndRejectsWeakPasswords() {
    var password: CharArray? = null
    var exported = false
    compose.setContent {
      MaterialTheme {
        ExportArchiveDialog(false, {}) {
          password = it
          exported = true
        }
      }
    }
    compose.onNodeWithContentDescription("Export archive").assertIsEnabled()
    compose.onNode(isToggleable()).performClick()
    val fields = compose.onAllNodes(hasSetTextAction())
    fields[0].performTextReplacement("12345678")
    fields[1].performTextReplacement("12345678")
    compose.onNodeWithContentDescription("Export archive").assertIsNotEnabled()
    fields[0].performTextReplacement("Safer-resource9!")
    fields[1].performTextReplacement("Safer-resource9!")
    compose.onNodeWithContentDescription("Export archive").performClick()
    assertTrue(exported)
    assertEquals("Safer-resource9!", password!!.concatToString())
    password!!.fill('\u0000')
  }

  @Test
  fun configurationHasOneSharedScrollContainer() {
    val model =
      LocalModel(
        "numeric",
        "Numeric",
        ModelAdapter.NUMERIC_REGRESSION,
        inputShape = listOf(1, 3),
        outputShape = listOf(1, 1),
      )
    compose.setContent { MaterialTheme { ModelConfigurationDialog(model, {}) {} } }
    compose.onAllNodesWithTag("adaptive-form-scroll").assertCountEquals(1)
    compose.onNodeWithTag("model-output-index").assertIsDisplayed()
  }

  @Test
  fun modelCardsPackWithoutRowGapsAndCenterTheirIcons() {
    val choices =
      listOf(
          LocalModel("a", "A", ModelAdapter.CLASSIFICATION),
          LocalModel(
            "b",
            "B " + "very long model name ".repeat(15),
            ModelAdapter.CLASSIFICATION,
            assetPath = "models/default.tflite",
          ),
          LocalModel("c", "C", ModelAdapter.CLASSIFICATION),
        )
        .map { ModelChoice(it.id, it.name, it.task, local = it) }
    compose.setContent { MaterialTheme { ModelCatalogBrowser(choices, {}) } }
    val a = compose.onNodeWithTag("model-card-a").fetchSemanticsNode().boundsInRoot
    val b = compose.onNodeWithTag("model-card-b").fetchSemanticsNode().boundsInRoot
    val c = compose.onNodeWithTag("model-card-c").fetchSemanticsNode().boundsInRoot
    assertTrue(b.height > a.height)
    assertTrue("Third card must follow the shorter first card", c.top < b.bottom)
    assertEquals(a.bottom + 12f, c.top, 1f)
    val icon =
      compose
        .onNodeWithTag("model-icon-b", useUnmergedTree = true)
        .fetchSemanticsNode()
        .boundsInRoot
    assertEquals(b.center.y, icon.center.y, 1f)
    assertTrue("Long name should not make an unbounded card", b.height < 160f)
  }

  @Test
  fun scrollbarKeepsOuterWidthAndLeavesOnlyANarrowContentGutter() {
    compose.setContent {
      MaterialTheme {
        AppLazyColumn(
          Modifier.width(600.dp).height(260.dp).testTag("outer"),
          state = rememberLazyListState(),
        ) {
          items(30) { Box(Modifier.fillMaxWidth().height(80.dp).testTag("row-$it")) }
        }
      }
    }
    val outer = compose.onNodeWithTag("outer").fetchSemanticsNode().boundsInRoot
    val row = compose.onNodeWithTag("row-0").fetchSemanticsNode().boundsInRoot
    assertEquals(600f, outer.width, .5f)
    assertEquals(14f, outer.width - row.width, .5f)
    assertEquals(outer.left, row.left, .5f)
  }

  @Test
  fun viewerOmitsHiddenAndEmptyCellsButKeepsUntitledContent() {
    val doc =
      ReportDocument(
        nodes =
          listOf(
            DocumentNode(id = "empty", name = "Empty", kind = BlockKind.TEXT, hideTitle = true),
            DocumentNode(
              id = "body",
              name = "Logical body",
              displayName = "Not rendered",
              kind = BlockKind.TEXT,
              body = "Actual content",
              hideTitle = true,
            ),
            DocumentNode(id = "hidden", name = "Hidden", kind = BlockKind.FOLDER, visible = false),
            DocumentNode(
              id = "child",
              parentId = "hidden",
              name = "Hidden child",
              kind = BlockKind.TEXT,
              body = "Invisible content",
            ),
          )
      )
    val id = runBlocking { app.reportLibrary.saveDocument(0, null, "Viewer", "", doc, emptyMap()) }
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
    waitUntil { compose.onAllNodesWithTag("report-cell-body").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("report-cell-empty").assertDoesNotExist()
    compose.onNodeWithTag("report-cell-hidden").assertDoesNotExist()
    compose.onNodeWithText("Actual content").assertIsDisplayed()
    compose.onNodeWithText("Not rendered").assertDoesNotExist()
  }

  @Test
  fun editorPreviewExplainsWhyAnEmptyCellWillNotRender() {
    val node = DocumentNode(name = "Logical", kind = BlockKind.TEXT, hideTitle = true)
    val audio = DocumentAudioSession()
    compose.setContent {
      MaterialTheme {
        Column { DocumentBlockBody(node, emptyMap(), null, 0, audio, {}, editorPreview = true) }
      }
    }
    waitUntil {
      compose
        .onAllNodesWithText(
          "This element has no visible content. It will not take up space in the report viewer."
        )
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    audio.close()
  }

  @Test
  fun modelResultPropertiesOfferPresentationAndGuardUnsavedChanges() {
    var closed = false
    val node = DocumentNode(name = "Analysis", kind = BlockKind.MODEL_RESULT)
    compose.setContent {
      MaterialTheme {
        DocumentNodeDialog(node, emptyMap(), { closed = true }, reanalyze = {}) { _, _ -> }
      }
    }
    compose.onNodeWithTag("resource-display-name").performTextReplacement("Alias")
    compose.onNodeWithTag("resource-hide-title").performClick()
    compose.onNodeWithTag("resource-visible").performClick()
    compose.onNodeWithContentDescription("Close").performClick()
    compose.onNodeWithText("Discard").assertIsDisplayed()
    assertFalse(closed)
  }

  @Test
  fun imageCreationOffersTheSameIdentityFieldsAsTheOtherNodes() {
    val bytes =
      java.io
        .ByteArrayOutputStream()
        .also { out ->
          android.graphics.Bitmap.createBitmap(20, 20, android.graphics.Bitmap.Config.ARGB_8888)
            .let {
              it.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
              it.recycle()
            }
        }
        .toByteArray()
    var identity: ResourceIdentity? = null
    compose.setContent {
      MaterialTheme {
        com.karandaev.fieldfolio.feature.media.ReportImageInputDialog(
          "create",
          "Choose image",
          {},
          initialBytes = bytes,
          onIdentity = { identity = it },
        ) { _, _ ->
          true
        }
      }
    }
    compose.onNodeWithTag("resource-display-name").performTextReplacement("Photo alias")
    compose.onNodeWithTag("resource-hide-title").performClick()
    compose.onNodeWithTag("resource-visible").performClick()
    compose.onNodeWithContentDescription("Save").performClick()
    waitUntil { identity != null }
    assertEquals("Photo alias", identity!!.displayName)
    assertTrue(identity!!.hideTitle)
    assertFalse(identity!!.visible)
  }
}
