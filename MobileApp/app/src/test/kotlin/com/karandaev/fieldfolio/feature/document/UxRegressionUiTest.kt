package com.karandaev.fieldfolio.feature.document

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.FileTransferIcons
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.AcodeBlockEditor
import com.karandaev.fieldfolio.feature.document.editor.AcodeDraftViewModel
import com.karandaev.fieldfolio.feature.document.editor.DocumentFileBrowser
import com.karandaev.fieldfolio.feature.document.editor.DocumentNodeDialog
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import com.karandaev.fieldfolio.feature.document.viewer.AudioPlayerView
import com.karandaev.fieldfolio.feature.document.viewer.DocumentLiveWidget
import com.karandaev.fieldfolio.feature.document.viewer.DocumentText
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
  qualifiers = "en-rUS-w412dp-h915dp-port-mdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UxRegressionUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun vm(): DocumentViewModel {
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
    return DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
  }

  @Test
  fun catalogSeparatesServerFromImportAndEqualizesTabs() {
    var picked: ModelChoice? = null
    val choices =
      listOf(
        ModelChoice("local", "Local text", "TEXT"),
        ModelChoice("remote", "Road damage", "DETECTION", server = RoadDamageServerAdapter),
      )
    compose.setContent {
      MaterialTheme { ModelCatalogBrowser(choices, { picked = it }, onImport = {}) }
    }
    compose.onNodeWithTag("models-import").assertIsDisplayed()
    val local = compose.onNodeWithTag("models-local-tab").fetchSemanticsNode().boundsInRoot
    val server = compose.onNodeWithTag("models-server-tab").fetchSemanticsNode().boundsInRoot
    assertEquals(local.width, server.width, .5f)
    assertEquals(local.height, server.height, .5f)
    compose.onNodeWithTag("models-server-tab").performClick()
    compose.onNodeWithTag("models-import").assertDoesNotExist()
    compose.onNodeWithText("Local text").assertDoesNotExist()
    compose.onNodeWithText("Object detection").assertIsDisplayed()
    compose.onNodeWithText("Road damage").performClick()
    assertEquals("remote", picked?.id)
    compose.onNodeWithTag("models-search").performTextInput("missing")
    compose.onNodeWithText("Road damage").assertDoesNotExist()
  }

  @Test
  fun audioWaveformSeeksAndControlsRemainAccessibleOnNarrowScreens() {
    var seek = -1f
    var toggles = 0
    var stops = 0
    compose.setContent {
      MaterialTheme {
        Box(Modifier.width(320.dp)) {
          AudioPlayerView(
            120000,
            10000,
            false,
            true,
            false,
            floatArrayOf(.1f, .8f, .2f),
            { seek = it },
            { toggles++ },
            { stops++ },
          )
        }
      }
    }
    compose.onNodeWithTag("audio-waveform").performSemanticsAction(SemanticsActions.SetProgress) {
      assertTrue(it(.75f))
    }
    assertEquals(.75f, seek, 0f)
    compose.onNodeWithText("0:10.000 / 2:00.000").assertIsDisplayed()
    compose.onNodeWithContentDescription("Play").performClick()
    assertEquals(1, toggles)
    compose.onNodeWithContentDescription("Stop").performClick()
    assertEquals(1, stops)
  }

  @Test
  fun collapsedTextHasFourLinesAndCanOpenFullResource() {
    val text = (1..100).joinToString("\n") { "Line $it" }
    compose.setContent { MaterialTheme { Column { DocumentText(text, "Long resource") } } }
    assertTrue(
      compose.onNodeWithTag("collapsed-document-text").fetchSemanticsNode().boundsInRoot.height <
        120f
    )
    compose.onNodeWithContentDescription("View").performClick()
    compose.onNodeWithText("Long resource").assertIsDisplayed()
    compose.onNodeWithContentDescription("Close").assertIsDisplayed()
  }

  @Test
  fun browserHistoryAndSearchKeepDocumentOrder() {
    val vm = vm()
    val folder = vm.add(BlockKind.FOLDER, "Folder")
    val text = vm.add(BlockKind.TEXT, "Notes")
    compose.setContent {
      MaterialTheme { DocumentFileBrowser(vm, vm.folder, vm::navigate, {}, {}, {}) }
    }
    compose.onNodeWithText("Folder", substring = false).performClick()
    assertEquals(folder.id, vm.folder)
    compose.onNodeWithContentDescription("Back in history").performClick()
    assertNull(vm.folder)
    compose.onNodeWithContentDescription("Forward in history").performClick()
    assertEquals(folder.id, vm.folder)
    compose.onNodeWithContentDescription("Parent folder").performClick()
    compose.onNodeWithTag("document-file-search").performTextInput("Notes")
    compose.onNodeWithText("Folder", substring = false).assertDoesNotExist()
    assertEquals(listOf(folder.id, text.id), vm.tree.children(null).map { it.id })
  }

  @Test
  fun longPressDragChangesPersistedSiblingOrder() {
    val vm = vm()
    val first = vm.add(BlockKind.TEXT, "First")
    val second = vm.add(BlockKind.TEXT, "Second")
    vm.add(BlockKind.TEXT, "Third")
    compose.setContent {
      MaterialTheme { DocumentFileBrowser(vm, vm.folder, vm::navigate, {}, {}, {}) }
    }
    val a = compose.onNodeWithTag("drag-${first.id}").fetchSemanticsNode().boundsInRoot
    val b = compose.onNodeWithTag("drag-${second.id}").fetchSemanticsNode().boundsInRoot
    compose.onNodeWithTag("drag-${first.id}").performTouchInput {
      down(center)
      advanceEventTime(700)
      moveBy(Offset(0f, b.center.y - a.center.y), 300)
      advanceEventTime(300)
      up()
    }
    compose.waitForIdle()
    assertEquals(second.id, vm.tree.children(null).first().id)
  }

  @Test
  fun transferIconsUseIncomingAndOutgoingDirections() {
    assertTrue(FileTransferIcons.Import.name.endsWith("FileDownload"))
    assertTrue(FileTransferIcons.Export.name.endsWith("FileUpload"))
  }

  @Test
  fun cvInputPickerExcludesTextAndFindsImagesInsideFolders() {
    val doc =
      ReportDocument(
        nodes =
          listOf(
            DocumentNode(id = "f", name = "Photos", kind = BlockKind.FOLDER),
            DocumentNode(id = "t", name = "Text input", kind = BlockKind.TEXT),
            DocumentNode(id = "p", parentId = "f", name = "Road photo", kind = BlockKind.IMAGE),
          )
      )
    var picked: String? = null
    compose.setContent { MaterialTheme { ModelInputPicker(doc, false, {}, { picked = it.id }) } }
    compose.onNodeWithText("Choose an image block").assertIsDisplayed()
    compose.onNodeWithText("Text input").assertDoesNotExist()
    compose.onNodeWithText("Photos").performClick()
    compose.onNodeWithText("Road photo").performClick()
    assertEquals("p", picked)
  }

  @Test
  fun headingLevelUsesTheAvailableFormWidth() {
    compose.setContent {
      MaterialTheme {
        DocumentNodeDialog(
          DocumentNode(name = "Section", kind = BlockKind.HEADING),
          emptyMap(),
          {},
          { _, _ -> },
        )
      }
    }
    val name =
      compose.onNode(hasSetTextAction() and hasText("Section")).fetchSemanticsNode().boundsInRoot
    val level =
      compose.onNode(hasSetTextAction() and hasText("1")).fetchSemanticsNode().boundsInRoot
    assertEquals(name.width, level.width, 1f)
  }

  @Test
  fun clockReusesTheExistingWidgetWithRequestedZone() {
    compose.setContent {
      MaterialTheme {
        Column {
          DocumentLiveWidget(
            DocumentNode(
              name = "Tokyo",
              kind = BlockKind.CLOCK,
              deviceLocation = false,
              zoneId = "Asia/Tokyo",
            ),
            null,
            0,
            {},
          )
        }
      }
    }
    compose.onNodeWithTag("document-clock-widget").assertIsDisplayed()
    compose.onNodeWithText("Asia/Tokyo").assertIsDisplayed()
  }

  @Test
  fun regressionDetailsDisplayRawSignedValuesInPortrait() {
    val result =
      ModelResultData(
        modelId = "r",
        modelName = "Offset",
        provider = "LOCAL",
        task = "REGRESSION",
        values = listOf(ResultValue("Offset", -2.5, "m")),
      )
    compose.setContent { MaterialTheme { AnalysisDetailsDialog(result, null, dismiss = {}) } }
    compose.onNodeWithTag("analysis-details-window").assertIsDisplayed()
    compose.onNodeWithText("Image regression").assertExists()
    compose.onNodeWithText("-2.50000 m", substring = true).assertExists()
    compose.onNodeWithContentDescription("Close").assertIsDisplayed()
  }

  @Test
  fun closingAcodeClearsItsPrivateInMemoryDraft() {
    var shown by mutableStateOf(true)
    compose.setContent {
      MaterialTheme {
        if (shown)
          AcodeBlockEditor(
            DocumentNode(
              id = "secret",
              name = "Private",
              kind = BlockKind.TEXT,
              body = "Secret source",
            ),
            emptyMap(),
            {},
            { _, _ -> },
          )
      }
    }
    val draft =
      androidx.lifecycle
        .ViewModelProvider(
          compose.activity,
          androidx.lifecycle.ViewModelProvider.NewInstanceFactory(),
        )["acode-secret", AcodeDraftViewModel::class.java]
    compose.runOnIdle {
      draft.text = "Private edited content"
      shown = false
    }
    compose.waitForIdle()
    assertFalse(draft.active)
    assertEquals("", draft.text)
    assertEquals("", draft.original)
  }
}
