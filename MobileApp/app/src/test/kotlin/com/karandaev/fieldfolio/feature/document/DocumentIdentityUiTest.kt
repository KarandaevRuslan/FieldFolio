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
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.ClockPropertiesDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentElementPreviewDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentNodeDialog
import com.karandaev.fieldfolio.feature.document.editor.NodeIdentityDialog
import com.karandaev.fieldfolio.feature.document.editor.TextPropertiesDialog
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.state.action
import com.karandaev.fieldfolio.feature.document.viewer.TextBlockContent
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
class DocumentIdentityUiTest {
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

  private fun analysis(standalone: Boolean): Triple<AnalysisViewModel, () -> Boolean, ModelChoice> {
    val adapter =
      object : ServerModelAdapter {
        override val id = "test-text"
        override val name = "Text model"
        override val task = "TEXT"
        override val inputKind = ModelInputKind.TEXT

        override suspend fun analyze(
          provider: com.karandaev.fieldfolio.core.inspectionapi.InspectionProvider,
          image: ByteArray?,
          text: String,
        ) =
          ModelResultData(
            modelId = id,
            modelName = name,
            provider = "SERVER",
            task = task,
            text = "Result",
          )
      }
    val choice = ModelChoice(adapter.id, adapter.name, adapter.task, server = adapter)
    val runner =
      CatalogModelRunner(
        app.modelCatalog,
        app.inspectionProvider,
        ServerModelRegistry(listOf(adapter)),
      )
    val vm =
      ViewModelProvider(
        compose.activity,
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(c: Class<T>): T = AnalysisViewModel(runner) as T
        },
      )["analysis-true-test-text", AnalysisViewModel::class.java]
    var dismissed = false
    compose.setContent {
      MaterialTheme { AnalysisWorkspace(choice, { dismissed = true }, standalone = standalone) }
    }
    compose.runOnIdle {
      vm.text("Input")
      vm.run(choice)
    }
    waitUntil { vm.result != null }
    return Triple(vm, { dismissed }, choice)
  }

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
      val file = java.io.File("build/previews-v17/$name.png")
      file.parentFile!!.mkdirs()
      file.outputStream().use {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
      }
      bitmap.recycle()
    }
  }

  @Test
  fun standaloneRunCanExportAndClosesWithoutAnUnsavedPrompt() {
    val (_, closed) = analysis(true)
    compose.onNodeWithContentDescription("Export").assertIsEnabled()
    compose.onNodeWithContentDescription("Back").performClick()
    assertTrue(closed())
    compose.onNodeWithText("Unsaved changes").assertDoesNotExist()
  }

  @Test
  fun documentAnalysisKeepsItsUnsavedGuardAndHasNoStandaloneExport() {
    val (_, closed) = analysis(false)
    compose.onNodeWithContentDescription("Export").assertDoesNotExist()
    compose.onNodeWithContentDescription("Back").performClick()
    assertFalse(closed())
    compose.onNodeWithText("Discard").assertExists()
  }

  @Test
  fun renameIsOnlyNamingAndFolderCreationRetainsItsPresentationOptions() {
    var creating by mutableStateOf(false)
    compose.setContent {
      MaterialTheme {
        NodeIdentityDialog(DocumentNode(name = "Folder", kind = BlockKind.FOLDER), {}, creating) {}
      }
    }
    compose.onNodeWithTag("resource-hide-title").assertDoesNotExist()
    compose.onNodeWithTag("resource-visible").assertDoesNotExist()
    compose.runOnIdle { creating = true }
    compose.onNodeWithTag("resource-hide-title").assertExists()
    compose.onNodeWithTag("resource-visible").assertExists()
  }

  @Test
  fun unifiedTextPropertiesSetHeadingAndReadingModeAndAllowEmptySubtitle() {
    var saved: DocumentNode? = null
    compose.setContent {
      MaterialTheme {
        TextPropertiesDialog(DocumentNode(name = "Title", kind = BlockKind.TEXT), emptyMap(), {}) {
          n,
          _ ->
          saved = n
        }
      }
    }
    compose.onNode(hasText("Heading") and hasClickAction()).performScrollTo().performClick()
    compose.onNodeWithText("Acode", substring = false).performScrollTo().performClick()
    compose.onNodeWithContentDescription("Save").performClick()
    snapshot("text-properties-landscape")
    assertEquals(BlockKind.TEXT, saved!!.kind)
    assertEquals(1, saved!!.headingLevel)
    assertEquals(TextOpenMode.ACODE, saved!!.textOpenMode)
    assertEquals("", saved!!.body)
  }

  @Test
  fun textPreviewIsTheSameCardAsTheReportAndUsesTitleBeforeSubtitle() {
    val session = DocumentAudioSession()
    val n =
      DocumentNode(
        name = "Logical",
        displayName = "Visible title",
        kind = BlockKind.HEADING,
        body = "Subtitle",
      )
    compose.setContent {
      MaterialTheme { DocumentElementPreviewDialog(n, emptyMap(), null, 0, session, {}, {}) }
    }
    compose.onNodeWithTag("report-cell-${n.id}").assertExists()
    val title =
      compose
        .onNode(hasText("Visible title") and hasAnyAncestor(hasTestTag("report-cell-${n.id}")))
        .fetchSemanticsNode()
        .boundsInRoot
    val subtitle = compose.onNodeWithText("Subtitle").fetchSemanticsNode().boundsInRoot
    assertTrue(title.bottom <= subtitle.top)
    snapshot("heading-preview-landscape")
    session.close()
  }

  @Test
  fun audioPreviewIsBoundedInLandscape() {
    val session = DocumentAudioSession()
    val n = DocumentNode(name = "Audio", kind = BlockKind.AUDIO)
    compose.setContent {
      MaterialTheme { DocumentElementPreviewDialog(n, emptyMap(), null, 0, session, {}, {}) }
    }
    val card = compose.onNodeWithTag("report-cell-${n.id}").fetchSemanticsNode().boundsInRoot
    assertTrue(card.width <= 440f)
    assertTrue(card.left > 150f)
    session.close()
  }

  @Test
  fun clockMapActionBelongsToPropertiesInsteadOfThePreviewPane() {
    compose.setContent {
      MaterialTheme {
        ClockPropertiesDialog(
          DocumentNode(
            name = "Clock",
            kind = BlockKind.CLOCK,
            zoneId = "UTC",
            latitude = 10.0,
            longitude = 20.0,
          ),
          {},
        ) { _, _ ->
        }
      }
    }
    compose.onNodeWithContentDescription("Choose on map").performScrollTo()
    val action =
      compose.onNodeWithContentDescription("Choose on map").fetchSemanticsNode().boundsInRoot
    val preview = compose.onNodeWithTag("element-preview").fetchSemanticsNode().boundsInRoot
    assertTrue(action.right <= preview.left)
    snapshot("clock-properties-landscape")
  }

  @Test
  @Config(qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
  fun clockMapActionIsAvailableInPortraitIncludingDeviceMode() {
    compose.setContent {
      MaterialTheme {
        ClockPropertiesDialog(
          DocumentNode(name = "Clock", kind = BlockKind.CLOCK, deviceLocation = true),
          {},
        ) { _, _ ->
        }
      }
    }
    compose
      .onNodeWithContentDescription("Choose on map")
      .performScrollTo()
      .assertIsDisplayed()
      .assertIsNotEnabled()
  }

  @Test
  fun acodeReadingHasSearchButNoSaveRenameOrEditingActions() {
    val n =
      DocumentNode(
        name = "Read",
        kind = BlockKind.TEXT,
        body = "Select and search this text",
        textOpenMode = TextOpenMode.ACODE,
      )
    compose.setContent { MaterialTheme { TextBlockContent(n, emptyMap()) } }
    waitUntil {
      compose.onAllNodesWithContentDescription("View").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithContentDescription("View").performClick()
    compose.onNodeWithTag("acode-readonly-window").assertExists()
    compose.onNodeWithContentDescription("Save").assertDoesNotExist()
    compose.onNodeWithContentDescription("Rename").assertDoesNotExist()
    compose.onNodeWithContentDescription("Search").assertExists()
  }

  @Test
  fun numericModelHasOneCompleteOutputDestinationAndNoImageWindow() {
    val session = DocumentAudioSession()
    val r =
      ModelResultData(
        modelId = "numbers",
        modelName = "Regressor",
        provider = "LOCAL",
        task = "REGRESSION",
        inputKind = "NUMBERS",
        values = (1..5).map { ResultValue("Value $it", it.toDouble()) },
      )
    compose.setContent {
      MaterialTheme {
        DocumentElementPreviewDialog(
          DocumentNode(name = "Result", kind = BlockKind.MODEL_RESULT, result = r),
          emptyMap(),
          null,
          0,
          session,
          {},
          {},
        )
      }
    }
    compose.onNodeWithText("Value 5").assertDoesNotExist()
    compose.onNodeWithContentDescription("View").performClick()
    compose.onNodeWithText("Value 5").assertExists()
    compose.onNodeWithTag("image-result-window").assertDoesNotExist()
    session.close()
  }

  @Test
  fun unfinishedLogicalNamesRemainEditableWithoutCrashingThePreview() {
    compose.setContent {
      MaterialTheme {
        TextPropertiesDialog(
          DocumentNode(name = "Original", kind = BlockKind.TEXT, body = "Draft content"),
          emptyMap(),
          {},
        ) { _, _ ->
        }
      }
    }
    compose.onNodeWithTag("resource-name").performTextReplacement("")
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Save").assertIsNotEnabled()
    compose.onNodeWithTag("resource-name").performTextInput("Restored")
    compose.onNodeWithContentDescription("Save").assertIsEnabled()
  }

  @Test
  fun unfinishedNamesDoNotCrashAnyGeographicPropertiesForm() {
    var kind by mutableStateOf(BlockKind.LOCATION)
    compose.setContent {
      MaterialTheme {
        DocumentNodeDialog(
          DocumentNode(id = kind.name, name = "Original", kind = kind, deviceLocation = true),
          emptyMap(),
          {},
        ) { _, _ ->
        }
      }
    }
    for (k in listOf(BlockKind.LOCATION, BlockKind.WEATHER, BlockKind.CLOCK)) {
      compose.runOnIdle { kind = k }
      compose.onNodeWithTag("resource-name").performTextReplacement("")
      compose.waitForIdle()
      compose.onNodeWithContentDescription("Save").assertIsNotEnabled()
      compose.onNodeWithTag("resource-name").performTextInput("Restored")
      compose.onNodeWithContentDescription("Save").assertIsEnabled()
    }
  }

  @Test
  fun exportSnapshotOwnsItsInputAfterTheAnalysisWindowIsCleared() {
    val (vm, _, choice) = analysis(true)
    val source = byteArrayOf(1, 2, 3)
    compose.runOnIdle {
      vm.image(source)
      vm.run(choice)
    }
    waitUntil { vm.result != null }
    compose.runOnIdle { vm.prepareExport() }
    waitUntil { !vm.exportPreparing }
    val snapshot = vm.takeExportSnapshot()!!
    compose.runOnIdle { vm.clear() }
    assertNotSame(source, snapshot.image)
    assertArrayEquals(byteArrayOf(1, 2, 3), snapshot.image)
    snapshot.release()
  }

  @Test
  fun textFilePickerUsesTheExistingExternalPickerLifecycleGuard() {
    compose.setContent {
      MaterialTheme {
        TextPropertiesDialog(
          DocumentNode(name = "Text", kind = BlockKind.TEXT),
          emptyMap(),
          {},
          importFile = { _, _ -> },
        ) { _, _ ->
        }
      }
    }
    compose.onNodeWithContentDescription("Import text file").performClick()
    assertTrue(app.reportLibrary.externalPicker)
    val started = Shadows.shadowOf(compose.activity).nextStartedActivityForResult
    assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
    compose.runOnIdle {
      compose.activity.activityResultRegistry.dispatchResult(
        started.requestCode,
        android.app.Activity.RESULT_CANCELED,
        null,
      )
    }
    assertFalse(app.reportLibrary.externalPicker)
  }
}
