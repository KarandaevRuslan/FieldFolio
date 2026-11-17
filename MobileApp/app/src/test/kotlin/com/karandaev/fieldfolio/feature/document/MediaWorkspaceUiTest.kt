package com.karandaev.fieldfolio.feature.document

import android.graphics.*
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.*
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.image.crop.NormalizedCropBox
import com.karandaev.fieldfolio.core.library.saveDocument
import com.karandaev.fieldfolio.core.names.ResourceIdentity
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.AudioPropertiesDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentElementPreviewDialog
import com.karandaev.fieldfolio.feature.document.route.DocumentRoute
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import com.karandaev.fieldfolio.feature.document.viewer.RepeatingAudioButton
import com.karandaev.fieldfolio.feature.media.*
import java.io.ByteArrayOutputStream
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(
  sdk = [35],
  application = MyApplication::class,
  qualifiers = "en-rUS-w412dp-h915dp-port-mdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaWorkspaceUiTest {
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

  private fun image(color: Int) =
    ByteArrayOutputStream()
      .also { out ->
        Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888).also {
          it.eraseColor(color)
          it.compress(Bitmap.CompressFormat.PNG, 100, out)
          it.recycle()
        }
      }
      .toByteArray()

  private fun waitUntil(check: () -> Boolean) =
    compose.waitUntil(15000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      check()
    }

  private fun draft(key: String) =
    ViewModelProvider(compose.activity, ViewModelProvider.NewInstanceFactory())[
      "image-input-$key", ImageInputDraft::class.java]

  private fun snapshot(name: String) {
    compose.waitForIdle()
    compose.runOnIdle {
      val view =
        org.robolectric.shadows.ShadowDialog.getShownDialogs()
          .lastOrNull { it.isShowing }
          ?.window
          ?.decorView ?: compose.activity.window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      view.draw(Canvas(bitmap))
      val file = java.io.File("build/previews-v19/$name.png")
      file.parentFile!!.mkdirs()
      file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      bitmap.recycle()
    }
  }

  @Test
  fun savedImageSurvivesReplacementCropAndRepeatedRestorationWithoutWipingTheCaller() {
    val saved = image(Color.RED)
    val expected = saved.copyOf()
    compose.setContent {
      MaterialTheme {
        ReportImageInputDialog(
          "saved",
          "Cover",
          {},
          initialBytes = saved,
          initialName = "saved.png",
        ) { _, _ ->
          true
        }
      }
    }
    val vm = draft("saved")
    waitUntil { vm.hasSavedImage && !vm.busy }
    val replacement = image(Color.BLUE)
    compose.runOnIdle { vm.set(replacement, "new.png") }
    compose.runOnIdle { vm.crop(NormalizedCropBox(.2, .2, .8, .8)) }
    waitUntil { !vm.busy }
    compose.onNodeWithContentDescription("Restore saved image").assertIsEnabled().performClick()
    waitUntil { !vm.busy && vm.bytes?.contentEquals(expected) == true }
    assertEquals("saved.png", vm.name)
    assertFalse(vm.changed)
    compose.runOnIdle { vm.set(image(Color.GREEN), "another.png") }
    compose.onNodeWithContentDescription("Restore saved image").performClick()
    waitUntil { !vm.busy && vm.bytes?.contentEquals(expected) == true }
    compose.runOnIdle { vm.clear() }
    assertArrayEquals(expected, saved)
    assertNull(vm.bytes)
  }

  @Test
  fun firstAcquiredImageHasNoSavedStateButCanStillUndoItsCrop() {
    compose.setContent {
      MaterialTheme { ReportImageInputDialog("new", "Image", {}) { _, _ -> true } }
    }
    val vm = draft("new")
    waitUntil { !vm.busy }
    compose.onNodeWithContentDescription("Restore saved image").assertIsNotEnabled()
    compose.runOnIdle { vm.set(image(Color.GREEN), "first.png") }
    compose.onNodeWithContentDescription("Restore saved image").assertIsNotEnabled()
    compose.runOnIdle { vm.crop(NormalizedCropBox(.2, .2, .8, .8)) }
    waitUntil { !vm.busy }
    compose.runOnIdle { vm.reset() }
    waitUntil { !vm.busy }
    val bitmap = BitmapFactory.decodeByteArray(vm.bytes, 0, vm.bytes!!.size)
    assertEquals(100, bitmap.width)
    assertEquals(60, bitmap.height)
    bitmap.recycle()
    assertTrue(vm.changed)
    assertFalse(vm.hasSavedImage)
  }

  @Test
  fun imagePreviewOffersTheSameEditorAsTheFilesystem() {
    val vm = DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
    vm.title = "Report"
    vm.addFile(image(Color.RED), "Kept name.png", "image/png", BlockKind.IMAGE)
    val node = vm.document.nodes.single()
    var edited = false
    val session = DocumentAudioSession()
    compose.setContent {
      MaterialTheme {
        DocumentElementPreviewDialog(
          node,
          vm.files,
          null,
          0,
          session,
          {},
          {},
          edit = { edited = true },
        )
      }
    }
    compose.onNodeWithContentDescription("Edit").assertIsDisplayed().performClick()
    assertTrue(edited)
    session.close()
  }

  @Test
  fun actualImageEditorRouteReplacesItsAssetAndKeepsTheOtherImageIntact() {
    val old = image(Color.RED)
    val replacement = image(Color.BLUE)
    val node =
      DocumentNode(
        id = "image",
        name = "Original.png",
        displayName = "Alias",
        kind = BlockKind.IMAGE,
        asset = "resources/original.png",
      )
    val source =
      DocumentNode(
        id = "source",
        name = "Source.png",
        kind = BlockKind.IMAGE,
        asset = "resources/source.png",
        order = 1,
      )
    val id =
      kotlinx.coroutines.runBlocking {
        app.reportLibrary.saveDocument(
          0,
          null,
          "Report",
          "",
          ReportDocument(nodes = listOf(node, source)),
          mapOf(node.asset!! to old, source.asset!! to replacement),
        )
      }
    compose.setContent {
      MaterialTheme {
        DocumentRoute(
          id,
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
    waitUntil {
      compose.onAllNodesWithTag("document-node-image").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithTag("document-node-image").performClick()
    compose.onNodeWithContentDescription("Edit").performClick()
    compose.onNodeWithTag("image-input-workspace").assertExists()
    waitUntil { draft("image-node-$id-image").hasSavedImage && !draft("image-node-$id-image").busy }
    compose.onNodeWithContentDescription("Choose an image block").performClick()
    compose.onAllNodesWithText("Source.png").onLast().performClick()
    waitUntil { draft("image-node-$id-image").bytes?.contentEquals(replacement) == true }
    compose.onAllNodesWithContentDescription("Save").onLast().performClick()
    waitUntil { compose.onAllNodesWithTag("image-input-workspace").fetchSemanticsNodes().isEmpty() }
    val vm =
      ViewModelProvider(compose.activity, ViewModelProvider.NewInstanceFactory())[
        "document-$id-null", DocumentViewModel::class.java]
    val updated = vm.tree.node(node.id)
    assertEquals(node.name, updated.name)
    assertEquals(node.displayName, updated.displayName)
    assertNotEquals(node.asset, updated.asset)
    assertArrayEquals(replacement, vm.files.getValue(updated.asset!!))
    assertArrayEquals(replacement, vm.files.getValue(source.asset!!))
    assertTrue(old.any { it != 0.toByte() })
  }

  @Test
  fun replacingFromTheReportFilesystemKeepsNamesAndPresentationAndSavesNewPixels() {
    val original = image(Color.RED)
    val replacement = image(Color.BLUE)
    val source =
      DocumentNode(name = "Source.png", kind = BlockKind.IMAGE, asset = "resources/source.png")
    val initial = ResourceIdentity("Kept name.png", "Alias", hideTitle = true, visible = false)
    var identity: ResourceIdentity? = null
    var saved: ByteArray? = null
    compose.setContent {
      MaterialTheme {
        ReportImageInputDialog(
          "replace",
          "Image",
          {},
          initialBytes = original,
          initialName = initial.name,
          initialIdentity = initial,
          preserveNameOnAcquire = true,
          document = ReportDocument(nodes = listOf(source)),
          files = mapOf(source.asset!! to replacement),
          onIdentity = { identity = it },
        ) { bytes, _ ->
          saved = bytes
          true
        }
      }
    }
    waitUntil { draft("replace").hasSavedImage && !draft("replace").busy }
    compose.onNodeWithContentDescription("Choose an image block").performClick()
    compose.onAllNodesWithText("Source.png").onLast().performClick()
    waitUntil { draft("replace").bytes?.contentEquals(replacement) == true }
    compose.onNodeWithTag("resource-name").assertTextContains(initial.name)
    compose.onNodeWithContentDescription("Save").performClick()
    waitUntil { saved != null }
    assertArrayEquals(replacement, saved)
    assertEquals(initial, identity)
    assertTrue(original.any { it != 0.toByte() })
    assertTrue(replacement.any { it != 0.toByte() })
  }

  @Test
  fun replacementPreservesIdentityPositionAndSharedOriginalResources() {
    val vm = DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
    vm.addFile(image(Color.RED), "Original.png", "image/png", BlockKind.IMAGE)
    val original = vm.document.nodes.single()
    val old = vm.files.getValue(original.asset!!).copyOf()
    vm.update(original.copy(displayName = "Alias", hideTitle = true, visible = false))
    vm.setCover(original.asset)
    val coverPath = vm.document.cover
    val current = vm.tree.node(original.id)
    val replacement = image(Color.BLUE)
    vm.updateImage(current, replacement, current.identity)
    assertNull(vm.error)
    val after = vm.tree.node(original.id)
    assertEquals(current.id, after.id)
    assertEquals(current.parentId, after.parentId)
    assertEquals(current.order, after.order)
    assertEquals("Original.png", after.name)
    assertEquals("Alias", after.displayName)
    assertTrue(after.hideTitle)
    assertFalse(after.visible)
    assertNotEquals(current.asset, after.asset)
    assertArrayEquals(replacement, vm.files.getValue(after.asset!!))
    assertArrayEquals(old, vm.files.getValue(coverPath!!))
    assertTrue(vm.files.containsKey(original.asset))
    assertEquals(2, vm.files.size)
  }

  @Test
  fun iconHoldShowsAHintWithoutClickingAndOrdinaryTapStillWorks() {
    var clicks = 0
    compose.setContent {
      MaterialTheme {
        HintIconButton({ clicks++ }, tooltip = "Useful action") {
          Icon(Icons.Default.Edit, "Useful action")
        }
      }
    }
    compose.mainClock.autoAdvance = false
    compose.onNodeWithContentDescription("Useful action").performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(700)
    compose.onNodeWithText("Useful action").assertIsDisplayed()
    assertEquals(0, clicks)
    compose.onNodeWithContentDescription("Useful action").performTouchInput { up() }
    compose.mainClock.advanceTimeBy(1800)
    compose.mainClock.autoAdvance = true
    compose.onNodeWithContentDescription("Useful action").performClick()
    assertEquals(1, clicks)
  }

  @Test
  fun tooltipDoesNotStealAnAudioRepeatHold() {
    var count = 0
    compose.setContent {
      MaterialTheme {
        RepeatingAudioButton(Icons.Default.Forward5, "Seek forward", true) { count++ }
      }
    }
    compose.mainClock.autoAdvance = false
    compose.onNodeWithContentDescription("Seek forward").performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(800)
    compose.onNodeWithText("Seek forward").assertIsDisplayed()
    assertTrue(count >= 2)
    compose.onNodeWithContentDescription("Seek forward").performTouchInput { up() }
    val stopped = count
    compose.mainClock.advanceTimeBy(700)
    assertEquals(stopped, count)
    compose.mainClock.autoAdvance = true
  }

  @Test
  fun mapZoomHintKeepsRepeatingUntilReleaseWithoutStealingFocus() {
    var count = 0
    compose.setContent {
      MaterialTheme {
        com.karandaev.fieldfolio.core.ui.components.map.controls.ui.MapOverlayButton(
          text = "+",
          size = 44.dp,
          hint = "Zoom in",
          onClick = { count++ },
          onPressRepeat = { count++ },
        )
      }
    }
    compose.mainClock.autoAdvance = false
    compose.onNodeWithText("+").performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(800)
    compose.onNodeWithText("Zoom in").assertIsDisplayed()
    assertTrue(count >= 3)
    compose.onNodeWithText("+").performTouchInput { up() }
    val stopped = count
    compose.mainClock.advanceTimeBy(700)
    assertEquals(stopped, count)
    compose.mainClock.autoAdvance = true
  }

  @Test
  @Config(qualifiers = "ru-rRU-w412dp-h915dp-port-mdpi")
  fun savedImageHintUsesTheCurrentApplicationLanguageEvenOnADisabledButton() {
    compose.setContent {
      MaterialTheme { ReportImageInputDialog("hint-ru", "Изображение", {}) { _, _ -> true } }
    }
    compose.mainClock.autoAdvance = false
    compose
      .onNodeWithContentDescription("Восстановить сохранённое изображение")
      .assertIsNotEnabled()
      .performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(700)
    compose.onNodeWithText("Восстановить сохранённое изображение").assertIsDisplayed()
    compose.onNodeWithContentDescription("Восстановить сохранённое изображение").performTouchInput {
      up()
    }
    compose.mainClock.autoAdvance = true
  }

  private fun audioToolbar() {
    compose.setContent {
      MaterialTheme {
        AudioPropertiesDialog(
          DocumentNode(name = "Track", kind = BlockKind.AUDIO),
          emptyMap(),
          {},
        ) { _, _ ->
        }
      }
    }
    compose.onNodeWithText("Audio").assertIsDisplayed()
    val title = compose.onNodeWithText("Audio").fetchSemanticsNode().boundsInRoot
    val bar = compose.onNodeWithTag("audio-action-toolbar").fetchSemanticsNode().boundsInRoot
    assertTrue(title.width > 40f)
    assertTrue(title.right <= bar.left)
    // Save remains directly reachable while the secondary actions scroll.
    compose.onNodeWithContentDescription("Save").assertIsDisplayed()
    compose.onNodeWithContentDescription("Change cover").performScrollTo().assertIsDisplayed()
    val change =
      compose.onNodeWithContentDescription("Change cover").fetchSemanticsNode().boundsInRoot
    assertEquals(title.center.y, change.center.y, 3f)
    snapshot(
      if (
        compose.activity.resources.configuration.orientation ==
          android.content.res.Configuration.ORIENTATION_LANDSCAPE
      )
        "audio-landscape"
      else "audio-portrait"
    )
  }

  @Test fun audioActionsScrollHorizontallyWhileThePortraitTitleRemainsVisible() = audioToolbar()

  @Test
  @Config(qualifiers = "en-rUS-w900dp-h420dp-land-mdpi")
  fun audioActionsStayOnOneHorizontalToolbarInLandscape() = audioToolbar()
}
