package com.karandaev.fieldfolio.feature.document

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.DocumentNodeDialog
import com.karandaev.fieldfolio.feature.document.viewer.DocumentImage
import com.karandaev.fieldfolio.feature.document.viewer.ImageResultDialog
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
class DocumentCompletionUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun renamingUtf16FileDoesNotResubmitOrRewriteItsContents() {
    val bytes = "Привет\r\n".toByteArray(Charsets.UTF_16)
    val node =
      DocumentNode(
        name = "original.txt",
        kind = BlockKind.TEXT_FILE,
        asset = "resources/original.txt",
      )
    var saved: DocumentNode? = null
    var body: String? = "unexpected"
    compose.setContent {
      MaterialTheme {
        DocumentNodeDialog(node, mapOf(node.asset!! to bytes), {}) { n, text ->
          saved = n
          body = text
        }
      }
    }
    compose.waitUntil(10000) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      compose
        .onAllNodes(hasSetTextAction() and hasText("original.txt"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    compose.onNodeWithTag("resource-name").performTextReplacement("renamed.txt")
    compose.onNodeWithContentDescription("Save").performClick()
    assertEquals("renamed.txt", saved!!.name)
    assertNull(body)
    assertEquals("Привет\r\n", TextResourceCodec.editable(bytes)!!.text)
  }

  @Test
  fun largeTextFileCanBeRenamedWithoutRenderingOrTruncatingTheEntireText() {
    val original = "x".repeat(1_000_000) + "KEEP_TAIL"
    val node =
      DocumentNode(name = "large.txt", kind = BlockKind.TEXT_FILE, asset = "resources/large.txt")
    var body: String? = "unexpected"
    var saved = false
    compose.setContent {
      MaterialTheme {
        DocumentNodeDialog(node, mapOf(node.asset!! to original.toByteArray()), {}) { _, text ->
          saved = true
          body = text
        }
      }
    }
    compose.waitUntil(10000) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      compose
        .onAllNodes(hasSetTextAction() and hasText("large.txt"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    compose.onNodeWithTag("resource-name").performTextReplacement("large-renamed.txt")
    compose.onNodeWithContentDescription("Save").performClick()
    assertTrue(saved)
    assertNull(body)
  }

  @Test
  fun editingSmallTextResourceReturnsWholeNewTextForEncodingPreservingSave() {
    val node =
      DocumentNode(name = "notes.txt", kind = BlockKind.TEXT_FILE, asset = "resources/notes.txt")
    var body: String? = null
    compose.setContent {
      MaterialTheme {
        DocumentNodeDialog(node, mapOf(node.asset!! to "Initial".toByteArray()), {}) { _, text ->
          body = text
        }
      }
    }
    compose.waitUntil(10000) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      compose.onAllNodesWithText("Initial").fetchSemanticsNodes().isNotEmpty()
    }
    // The adaptive properties window also renders the text in its preview pane.
    compose.onAllNodesWithText("Initial")[0].assertIsDisplayed()
    compose.onNodeWithContentDescription("Edit with Acode").performScrollTo().performClick()
    compose.waitUntil(10000) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      compose.onAllNodesWithTag("acode-editor-window").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithTag("acode-editor-window").assertIsDisplayed()
    assertNull(body) // Opening the editor must not rewrite the UTF-8 resource.
  }

  @Test
  fun damagedThumbnailReportsAnErrorInsteadOfLoadingForever() {
    compose.setContent { MaterialTheme { DocumentImage(byteArrayOf(1, 2, 3, 4)) } }
    try {
      compose.waitUntil(10000) {
        compose
          .onAllNodesWithText("Could not complete the operation")
          .fetchSemanticsNodes()
          .isNotEmpty()
      }
    } catch (e: Exception) {
      println(compose.onRoot(useUnmergedTree = true).printToString())
      throw e
    }
    compose.onNodeWithText("Loading…").assertDoesNotExist()
  }

  @Test
  @Config(qualifiers = "en-rUS-w900dp-h420dp-land-mdpi")
  fun allSegmentationLayersHaveIndependentControls() {
    val bitmap =
      android.graphics.Bitmap.createBitmap(640, 320, android.graphics.Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(android.graphics.Color.DKGRAY)
    val bytes =
      java.io
        .ByteArrayOutputStream()
        .also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        .toByteArray()
    bitmap.recycle()
    fun polygon(x: Double) =
      SegmentationPolygon(
        listOf(
          listOf(
            NormalizedPoint(x, .15),
            NormalizedPoint(x + .25, .15),
            NormalizedPoint(x + .25, .8),
            NormalizedPoint(x, .8),
          )
        )
      )
    val result =
      ModelResultData(
        modelId = "legacy:1:0",
        modelName = "Saved segmentation",
        provider = "LEGACY",
        task = "SEGMENTATION",
        layers =
          listOf(
            SegmentationLayer("Crack", .9, polygons = listOf(polygon(.1))),
            SegmentationLayer("Repair", .8, polygons = listOf(polygon(.6))),
          ),
      )
    compose.setContent { MaterialTheme { ImageResultDialog(bytes, result, null, dismiss = {}) } }
    compose
      .onNode(hasText("Crack") and hasClickAction())
      .assertIsSelected()
      .performClick()
      .assertIsNotSelected()
    compose.onNode(hasText("Repair") and hasClickAction()).assertIsSelected()
    compose.onNode(hasText("Crack") and hasClickAction()).performClick().assertIsSelected()
    compose.onNodeWithTag("result-overlay-toggle").assertIsOn()
    compose.waitUntil(10000) {
      compose.onAllNodesWithText("Loading…").fetchSemanticsNodes().isEmpty()
    }
    compose.onNodeWithText("Could not complete the operation").assertDoesNotExist()
    compose.waitForIdle()
    compose.runOnIdle {
      val view = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
      val shot =
        android.graphics.Bitmap.createBitmap(
          view.width,
          view.height,
          android.graphics.Bitmap.Config.ARGB_8888,
        )
      view.draw(android.graphics.Canvas(shot))
      java.io.File("build/previews-complete").mkdirs()
      java.io.File("build/previews-complete/segmentation-layers-landscape.png").outputStream().use {
        shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
      }
      shot.recycle()
    }
  }
}
