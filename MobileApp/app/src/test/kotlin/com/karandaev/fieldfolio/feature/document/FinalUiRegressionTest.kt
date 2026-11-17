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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.google.firebase.*
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.document.resourceSyntaxName
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.AudioPropertiesDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentNodeDialog
import com.karandaev.fieldfolio.feature.document.state.DocumentThumbnail
import com.karandaev.fieldfolio.feature.document.state.DocumentThumbnailCache
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import com.karandaev.fieldfolio.feature.document.viewer.LocationPreviewDialog
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
class FinalUiRegressionTest {
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
          .setProjectId("test")
          .build(),
      )
  }

  private fun waitUntil(condition: () -> Boolean) =
    compose.waitUntil(15000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      condition()
    }

  private fun image(): ByteArray {
    val bitmap =
      Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.GRAY)
      }
    return java.io
      .ByteArrayOutputStream()
      .also {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        bitmap.recycle()
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
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      view.draw(android.graphics.Canvas(bitmap))
      val dir = java.io.File("build/final-previews").apply { mkdirs() }
      java.io.File(dir, "$name.png").outputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
      }
      bitmap.recycle()
    }
  }

  @Test
  fun runStaysInsideToolbarAndFolderFollowsCamera() {
    compose.setContent {
      MaterialTheme {
        AnalysisWorkspace(
          ModelChoice("road-server", "Road damage", "DETECTION", server = RoadDamageServerAdapter),
          {},
          ReportDocument(),
          emptyMap(),
        )
      }
    }
    val toolbar = compose.onNodeWithTag("image-input-toolbar").fetchSemanticsNode().boundsInRoot
    val run = compose.onNodeWithContentDescription("Run model").fetchSemanticsNode().boundsInRoot
    val camera =
      compose.onNodeWithContentDescription("Take photo").fetchSemanticsNode().boundsInRoot
    val folder =
      compose
        .onNodeWithContentDescription("Choose an image block")
        .fetchSemanticsNode()
        .boundsInRoot
    val crop = compose.onNodeWithContentDescription("Crop image").fetchSemanticsNode().boundsInRoot
    assertTrue(
      run.left >= toolbar.left && run.right <= toolbar.right && run.bottom <= toolbar.bottom
    )
    assertTrue(folder.left >= camera.right && crop.left >= folder.right)
    snapshot("analysis-toolbar")
  }

  @Test
  fun cropResetSharesTheApplyRow() {
    compose.setContent { MaterialTheme { AnalysisCropDialog(image(), {}, {}) } }
    val reset = compose.onNodeWithContentDescription("Reset zoom").fetchSemanticsNode().boundsInRoot
    val apply = compose.onNodeWithContentDescription("Apply").fetchSemanticsNode().boundsInRoot
    assertEquals(reset.center.y, apply.center.y, 1f)
  }

  @Test
  fun largeLocationPreviewFitsWithoutAnyVerticalScroll() {
    compose.setContent {
      MaterialTheme {
        LocationPreviewDialog(
          DocumentNode(
            name = "Location",
            kind = BlockKind.LOCATION,
            latitude = 50.0,
            longitude = 90.0,
          ),
          null,
          {},
          {},
        )
      }
    }
    val workspace = compose.onNodeWithTag("overlay-workspace").fetchSemanticsNode().boundsInRoot
    val map = compose.onNodeWithTag("native-map-frame").fetchSemanticsNode().boundsInRoot
    assertTrue(map.bottom <= workspace.bottom && map.height > 300)
    compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
    snapshot("location-preview")
  }

  @Test
  fun imageBlockPickerHasTwoColumnsAndThumbnails() {
    val bytes = image()
    val nodes =
      (0..3).map {
        DocumentNode(
          id = "image$it",
          name = "Image $it",
          kind = BlockKind.IMAGE,
          asset = "resources/$it.png",
          order = it,
        )
      }
    compose.setContent {
      MaterialTheme {
        ModelInputPicker(
          ReportDocument(nodes = nodes),
          false,
          {},
          nodes.associate { it.asset!! to bytes },
        ) {}
      }
    }
    waitUntil {
      compose
        .onAllNodesWithTag("thumbnail-image0", useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    val a = compose.onNodeWithText("Image 0").fetchSemanticsNode().boundsInRoot
    val b = compose.onNodeWithText("Image 1").fetchSemanticsNode().boundsInRoot
    assertEquals(a.top, b.top, 1f)
    assertTrue(b.left > a.right)
    snapshot("image-picker")
  }

  @Test
  fun headingDraftRequiresExplicitDiscardAndCancelKeepsTheDraft() {
    var closed = false
    compose.setContent {
      MaterialTheme {
        DocumentNodeDialog(
          DocumentNode(name = "Heading", kind = BlockKind.HEADING),
          emptyMap(),
          { closed = true },
        ) { _, _ ->
        }
      }
    }
    compose.onNode(hasSetTextAction() and hasText("Heading")).performTextReplacement("Changed")
    compose.onNodeWithContentDescription("Close").performClick()
    assertFalse(closed)
    compose.onNodeWithText("Cancel").performClick()
    compose.onNode(hasSetTextAction() and hasText("Changed")).assertExists()
    compose.onNodeWithContentDescription("Close").performClick()
    compose.onNodeWithText("Discard").performClick()
    assertTrue(closed)
  }

  @Test
  fun audioPropertiesSaveAndResetOverrideAndTagVisibility() {
    var saved: DocumentNode? = null
    val node =
      DocumentNode(
        name = "sound.m4a",
        kind = BlockKind.AUDIO,
        audio = AudioPresentation(overrides = mapOf("artist" to "Old override")),
      )
    compose.setContent {
      MaterialTheme { AudioPropertiesDialog(node, emptyMap(), {}) { next, _ -> saved = next } }
    }
    compose
      .onNodeWithContentDescription("Expand or collapse metadata")
      .performScrollTo()
      .performClick()
    compose
      .onNode(hasSetTextAction() and hasText("Old override"))
      .performScrollTo()
      .performTextReplacement("New artist")
    compose.onNodeWithContentDescription("Save").performClick()
    assertEquals("New artist", saved!!.audio.overrides["artist"])
  }

  @Test
  fun audioCoverInEditorUsesTheSameFixedThumbnailAsImages() {
    val node =
      DocumentNode(
        id = "sound",
        name = "sound.m4a",
        kind = BlockKind.AUDIO,
        asset = "resources/sound.m4a",
        audio = AudioPresentation(showCover = false, coverAsset = "resources/cover.png"),
      )
    compose.setContent {
      MaterialTheme {
        DocumentThumbnail(
          node,
          mapOf("resources/cover.png" to image()),
          remember { DocumentThumbnailCache() },
        ) {
          Text("missing")
        }
      }
    }
    waitUntil { compose.onAllNodesWithTag("thumbnail-sound").fetchSemanticsNodes().isNotEmpty() }
    val cover = compose.onNodeWithTag("thumbnail-sound").fetchSemanticsNode().boundsInRoot
    assertEquals(44f, cover.width, 1f)
    assertEquals(44f, cover.height, 1f)
  }

  @Test
  fun creatingDestinationFolderDoesNotCrashBeforeTheTreeFlowUpdates() {
    val id = runBlocking {
      app.reportLibrary.saveDocument(0, null, "Move me", "", ReportDocument(), emptyMap())
    }
    val vm = LibraryViewModel(app.reportLibrary)
    var target: Long? = null
    compose.setContent {
      MaterialTheme {
        LibraryDestinationDialog(vm, LibraryClipboard(listOf(id), true), {}, { target = it })
      }
    }
    waitUntil { !vm.loading }
    assertTrue(vm.locked(Long.MAX_VALUE))
    compose.onNodeWithContentDescription("New folder").performClick()
    compose.onNodeWithTag("library-name").performTextInput("Created destination")
    compose.onNodeWithText("Save").performClick()
    waitUntil { vm.nodes.any { it.name == "Created destination" } && !vm.busy }
    val created = vm.nodes.single { it.name == "Created destination" }.id
    compose.onNodeWithTag("library-move-here").performClick()
    assertEquals(created, target)
  }

  @Test
  fun unlockingPathDestinationContinuesTheWholePathAndClosesThePathDialog() {
    val parent = runBlocking { app.reportLibrary.createFolder("Locked", null) }
    val child = runBlocking { app.reportLibrary.createFolder("Child", parent) }
    runBlocking { app.reportLibrary.setPassword(parent, "password123Aa1!".toCharArray()) }
    app.reportLibrary.lockAll()
    val vm = LibraryViewModel(app.reportLibrary)
    compose.setContent { MaterialTheme { LibraryBrowserDialogs(vm, {}) } }
    waitUntil { !vm.loading }
    compose.runOnIdle { vm.goPath = true }
    compose.onNodeWithTag("library-path").performTextReplacement("/Locked/Child")
    compose.onNodeWithContentDescription("Open").performClick()
    waitUntil { vm.unlock == parent }
    compose
      .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
      .performTextInput("password123Aa1!")
    compose.onNodeWithContentDescription("Continue").performClick()
    waitUntil { vm.folder == child && !vm.goPath }
    compose.onNodeWithTag("library-path").assertDoesNotExist()
  }

  @Test
  fun replacementAnalysisPreservesNodeIdentityPositionAndAnnotation() = runBlocking {
    val vm = DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
    val parent = vm.add(BlockKind.FOLDER, "Folder")
    vm.navigate(parent.id)
    val node = vm.add(BlockKind.MODEL_RESULT, "Existing")
    vm.update(
      node.copy(
        body = "Annotation",
        result =
          ModelResultData(
            modelId = "old",
            modelName = "Old",
            provider = "LOCAL",
            task = "DETECTION",
          ),
      )
    )
    vm.stageModelProperties(vm.tree.node(node.id))
    vm.stageAnalysisResult(
      ModelExecution(
        ModelResultData(
          modelId = "new",
          modelName = "New",
          provider = "LOCAL",
          task = "CLASSIFICATION",
        ),
        emptyMap(),
      ),
      image(),
    )
    vm.commitModelDraft(vm.modelDraft!!)
    val after = vm.tree.node(node.id)
    assertEquals("Existing", after.name)
    assertEquals("Annotation", after.body)
    assertEquals(parent.id, after.parentId)
    assertEquals(node.order, after.order)
    assertEquals("new", after.result!!.modelId)
    assertNotNull(vm.files[after.result.inputAsset])
    assertEquals(2, vm.document.nodes.size)
  }

  @Test
  fun fixedFingerPositionDoesNotRetargetWhenTheVisibleRowsHaveDifferentHeights() {
    var state: androidx.compose.foundation.lazy.LazyListState? = null
    compose.setContent {
      MaterialTheme {
        val scroll = rememberLazyListState()
        state = scroll
        com.karandaev.fieldfolio.core.ui.scroll.AppLazyColumn(
          Modifier.fillMaxSize().testTag("stable-scroll"),
          state = scroll,
        ) {
          items(80) { index ->
            Text("Item $index", Modifier.height(if (index % 2 == 0) 44.dp else 105.dp))
          }
        }
      }
    }
    compose.onNodeWithTag("stable-scroll").performTouchInput {
      down(Offset(width - 6f, 20f))
      moveTo(Offset(width - 6f, height * .6f), 100)
    }
    compose.waitForIdle()
    val first = state!!.firstVisibleItemIndex
    val offset = state!!.firstVisibleItemScrollOffset
    compose.onNodeWithTag("stable-scroll").performTouchInput {
      moveTo(Offset(width - 6f, height * .6f), 100)
    }
    compose.waitForIdle()
    assertEquals(first, state!!.firstVisibleItemIndex)
    assertEquals(offset, state!!.firstVisibleItemScrollOffset)
    compose.onNodeWithTag("stable-scroll").performTouchInput { up() }
  }

  @Test
  fun textSettingsAreInTheLeftColumnAndEditorPreferencesPersist() {
    compose.setContent {
      MaterialTheme {
        com.karandaev.fieldfolio.feature.settings.contents.SettingsTwoPaneContent(
          null,
          com.karandaev.fieldfolio.core.domain.model.ThemeMode.LIGHT,
          "en",
          "http://localhost:8080",
          com.karandaev.fieldfolio.feature.settings.state.ServerAvailabilityState.Unknown,
          "1.2.2",
          false,
          false,
          false,
          {},
          {},
          {},
          {},
          {},
          {},
          {},
          {},
          {},
        )
      }
    }
    compose.onNodeWithTag("acode-font-size-selector").performScrollTo()
    val field = compose.onNodeWithTag("acode-font-size-selector").fetchSemanticsNode().boundsInRoot
    assertTrue(field.right < 500f)
    val wrap = compose.onNodeWithText("Wrap lines in Acode").fetchSemanticsNode().boundsInRoot
    assertTrue(wrap.left < 450f)
    val audio = compose.onNodeWithText("Audio").fetchSemanticsNode().boundsInRoot
    val map = compose.onNodeWithText("Map").fetchSemanticsNode().boundsInRoot
    assertTrue(map.left > 450f)
    assertTrue(map.top > audio.top)
    snapshot("text-settings-landscape")
  }

  @Test
  fun changingAcodeTextSettingsWritesTheSamePreferencesUsedByTheEditor() {
    val original = com.karandaev.fieldfolio.acode.settings.EditorPreferences(compose.activity)
    original.updateWordWrap(true)
    original.updateFontSize(14)
    compose.setContent {
      MaterialTheme {
        Column(Modifier.fillMaxSize()) {
          com.karandaev.fieldfolio.feature.settings.components.sections.SettingsTextSection()
        }
      }
    }
    compose.onNode(isToggleable()).performClick()
    compose.onNodeWithTag("acode-font-size-selector").performClick()
    compose.onNodeWithText("20").performScrollTo().performClick()
    val stored = com.karandaev.fieldfolio.acode.settings.EditorPreferences(compose.activity)
    assertFalse(stored.wordWrap)
    assertEquals(20, stored.fontSize)
  }

  @Test
  fun repeatedlyOpeningACropEnablesApplyAfterBackgroundDecode() {
    var bytes by mutableStateOf<ByteArray?>(image())
    compose.setContent {
      MaterialTheme {
        com.karandaev.fieldfolio.feature.media.ImageInputToolbar(
          bytes,
          onImage = { next, _ -> bytes = next },
        )
      }
    }
    repeat(6) {
      compose.onNodeWithContentDescription("Crop image").performClick()
      waitUntil {
        compose
          .onAllNodes(hasContentDescription("Apply") and isEnabled())
          .fetchSemanticsNodes()
          .isNotEmpty()
      }
      compose.onNodeWithContentDescription("Close").performClick()
    }
  }

  @Test
  fun extensionlessAndAliasNamesKeepTheUnderlyingResourceSyntax() {
    assertEquals("resources/logic.ts", resourceSyntaxName("Logical title", "resources/logic.ts"))
    assertEquals("resources/logic.ts", resourceSyntaxName("Logical.title", "resources/logic.ts"))
    assertEquals("New.css", resourceSyntaxName("New.css", "resources/logic.ts"))
  }

  @Test
  fun nlpToolbarKeepsTextBlockSelectionWhenTheImageFolderActionMoves() {
    compose.setContent {
      MaterialTheme {
        AnalysisWorkspace(
          ModelChoice(
            "nlp-test",
            "Text model",
            "TEXT",
            local = LocalModel("nlp-test", "Text model", ModelAdapter.TEXT_CLASSIFICATION),
          ),
          {},
          ReportDocument(),
        )
      }
    }
    compose.onNodeWithContentDescription("Choose a text block").assertIsDisplayed()
    compose.onNodeWithContentDescription("Take photo").assertDoesNotExist()
    compose.onNodeWithContentDescription("Run model").assertIsDisplayed()
  }
}
