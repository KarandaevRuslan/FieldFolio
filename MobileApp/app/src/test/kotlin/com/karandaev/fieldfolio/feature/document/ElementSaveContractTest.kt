package com.karandaev.fieldfolio.feature.document

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.document.runtime.DefaultNotebookDocument
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.AcodeBlockEditor
import com.karandaev.fieldfolio.feature.document.editor.AudioPropertiesDialog
import com.karandaev.fieldfolio.feature.document.editor.DocumentFileBrowser
import com.karandaev.fieldfolio.feature.document.editor.DocumentNodeDialog
import com.karandaev.fieldfolio.feature.document.editor.TextPropertiesDialog
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import com.karandaev.fieldfolio.feature.document.state.action
import com.karandaev.fieldfolio.feature.document.viewer.AcodeReadOnlyDialog
import com.karandaev.fieldfolio.feature.media.ReportImageInputDialog
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(
  sdk = [35],
  application = MyApplication::class,
  qualifiers = "en-rUS-w360dp-h800dp-port-mdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ElementSaveContractTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun show(content: @Composable () -> Unit) {
    val app = compose.activity.application
    if (FirebaseApp.getApps(app).isEmpty())
      FirebaseApp.initializeApp(
        app,
        FirebaseOptions.Builder()
          .setApplicationId("1:123:android:test")
          .setApiKey("test-key")
          .setProjectId("local-test")
          .build(),
      )
    compose.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { content() } } }
  }

  private fun check(kind: BlockKind) {
    val node =
      DocumentNode(
        id = "save-${kind.name}",
        name = "Logical",
        kind = kind,
        body = if (kind == BlockKind.DOCUMENT) DefaultNotebookDocument else "Body",
        latitude = 1.0,
        longitude = 2.0,
        zoneId = "UTC",
        result =
          if (kind == BlockKind.MODEL_RESULT)
            ModelResultData(
              modelId = "m",
              modelName = "Model",
              provider = "local",
              task = "text_generation",
              text = "Result",
            )
          else null,
      )
    var saved: DocumentNode? = null
    show { DocumentNodeDialog(node, emptyMap(), {}) { next, _ -> saved = next } }
    compose
      .onNodeWithContentDescription("Save")
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()
    compose.runOnIdle {
      assertNotNull("Visible Save must commit $kind at phone width", saved)
      assertEquals(node.name, saved!!.name)
    }
  }

  @Test
  fun textSaveIsPinnedAndCommitsEvenWithTheImportActionInPortrait() {
    val node = DocumentNode(id = "text", name = "Text", kind = BlockKind.TEXT, body = "Source")
    var saved: DocumentNode? = null
    show {
      TextPropertiesDialog(node, emptyMap(), {}, importFile = { _, _ -> }) { next, _ ->
        saved = next
      }
    }
    compose.onNodeWithContentDescription("Save").assertIsDisplayed().performClick()
    compose.runOnIdle { assertEquals("Source", saved?.body) }
  }

  @Test fun headingSaveCommitsInPortrait() = check(BlockKind.HEADING)

  @Test fun documentSaveCommitsInPortrait() = check(BlockKind.DOCUMENT)

  @Test fun locationSaveCommitsInPortrait() = check(BlockKind.LOCATION)

  @Test fun weatherSaveCommitsInPortrait() = check(BlockKind.WEATHER)

  @Test fun clockSaveCommitsInPortrait() = check(BlockKind.CLOCK)

  @Test fun modelResultSaveCommitsInPortrait() = check(BlockKind.MODEL_RESULT)

  @Test
  fun audioSaveIsPinnedAndPreviewIsReachable() {
    val node =
      DocumentNode(id = "audio", name = "Song", kind = BlockKind.AUDIO, asset = "sound.m4a")
    var saved: DocumentNode? = null
    show {
      AudioPropertiesDialog(node, mapOf("sound.m4a" to byteArrayOf(1, 2, 3)), {}) { next, _ ->
        saved = next
      }
    }
    compose.onNodeWithContentDescription("Save").assertIsDisplayed().performClick()
    compose.runOnIdle { assertEquals("Song", saved?.name) }
    compose.onNodeWithContentDescription("View").performScrollTo().performClick()
    compose.onNodeWithTag("element-preview-window").assertIsDisplayed()
  }

  private fun image(): ByteArray =
    java.io
      .ByteArrayOutputStream()
      .also { output ->
        Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).also {
          it.compress(Bitmap.CompressFormat.PNG, 100, output)
          it.recycle()
        }
      }
      .toByteArray()

  @Test
  fun imageSaveCommitsInPortrait() {
    var saved: ByteArray? = null
    val bytes = image()
    show {
      ReportImageInputDialog(
        "image-test",
        "Image",
        {},
        initialBytes = bytes,
        initialName = "original photo.png",
      ) { value, name ->
        saved = value
        assertEquals("original photo.png", name)
        true
      }
    }
    compose.waitUntil(10000) {
      !compose
        .onNodeWithContentDescription("Save")
        .fetchSemanticsNode()
        .config
        .contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
    }
    compose.onNodeWithContentDescription("Save").assertIsDisplayed().performClick()
    compose.waitUntil(10000) { saved != null }
    assertArrayEquals(bytes, saved)
  }

  @Test
  fun coverSelectionHasNoLogicalName() {
    show {
      ReportImageInputDialog(
        "cover-test",
        "Change cover",
        {},
        initialBytes = image(),
        namedResource = false,
      ) { _, _ ->
        true
      }
    }
    compose.onNodeWithTag("resource-name").assertDoesNotExist()
    compose.onNodeWithTag("resource-display-name").assertDoesNotExist()
  }

  @Test
  fun helpUsesSyntaxHintInsteadOfLogicalFilename() {
    val node =
      DocumentNode(
        id = "js-help",
        name = "plain name",
        kind = BlockKind.TEXT,
        syntaxExtension = "js",
        body = "const value=1;",
      )
    show { AcodeBlockEditor(node, emptyMap(), {}, allowRename = false) { _, _ -> } }
    compose.onNodeWithContentDescription("Help").assertIsDisplayed()
  }

  @Test
  fun logicalJsFilenameAloneDoesNotAddDocumentApiHelp() {
    show {
      AcodeBlockEditor(
        DocumentNode(name = "ordinary.js", kind = BlockKind.TEXT, body = "source"),
        emptyMap(),
        {},
        allowRename = false,
      ) { _, _ ->
      }
    }
    compose.onNodeWithContentDescription("Help").assertDoesNotExist()
  }

  @Test
  fun vfsExportsSelectionButCopyPathRequiresExactlyOneObject() {
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
    val vm = DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
    val first = vm.add(BlockKind.TEXT, "First")
    vm.add(BlockKind.TEXT, "Second")
    show { DocumentFileBrowser(vm, null, vm::navigate, {}, {}, {}) }
    compose.onNodeWithText("First").performTouchInput { longClick() }
    compose.onNodeWithTag("vfs-export").assertExists()
    compose.onNodeWithTag("vfs-copy-path").performScrollTo().performClick()
    val clip =
      app.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    assertEquals(vm.tree.path(first.id), clip.primaryClip!!.getItemAt(0).text.toString())
    compose.onNodeWithText("Second").performClick()
    compose.onNodeWithTag("vfs-copy-path").assertDoesNotExist()
    compose.onNodeWithTag("vfs-export").assertExists()
  }

  @Test
  fun readonlyJavaScriptHasNoAuthoringHelp() {
    show {
      AcodeReadOnlyDialog(
        DocumentNode(
          id = "readonly-help",
          name = "Source",
          kind = BlockKind.TEXT,
          body = "const x=1;",
          syntaxExtension = "js",
        ),
        emptyMap(),
        {},
      )
    }
    compose.onNodeWithContentDescription("Help").assertDoesNotExist()
  }

  @Test
  fun vfsExportCreatesAPortableZipThroughTheSystemDestinationContract() {
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
    val vm = DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
    val node = vm.add(BlockKind.TEXT, "Export me")
    vm.update(node.copy(body = "Exported content"))
    val output = java.io.File(app.cacheDir, "vfs-export-test.zip")
    val uri = android.net.Uri.parse("content://export.test/zip")
    org.robolectric.Shadows.shadowOf(app.contentResolver).registerOutputStreamSupplier(uri) {
      output.outputStream()
    }
    show { DocumentFileBrowser(vm, null, vm::navigate, {}, {}, {}) }
    compose.onNodeWithText("Export me").performTouchInput { longClick() }
    compose.onNodeWithTag("vfs-export").performScrollTo().performClick()
    val shadow = org.robolectric.Shadows.shadowOf(compose.activity)
    var request: android.content.Intent? = null
    var code = 0
    compose.waitUntil(10000) {
      shadow.nextStartedActivityForResult?.let {
        request = it.intent
        code = it.requestCode
      }
      request != null
    }
    assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT, request!!.action)
    assertEquals("application/zip", request!!.type)
    assertEquals("Export me.zip", request!!.getStringExtra(android.content.Intent.EXTRA_TITLE))
    compose.runOnIdle {
      assertTrue(
        compose.activity.activityResultRegistry.dispatchResult(
          code,
          android.app.Activity.RESULT_OK,
          android.content.Intent().setData(uri),
        )
      )
    }
    try {
      compose.waitUntil(10000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        vm.error?.let { throw AssertionError("Export failed", it) }
        !vm.elementExportBusy && output.length() > 22
      }
    } catch (failure: Throwable) {
      throw AssertionError(
        "Export state: busy=${vm.elementExportBusy}, file=${output.exists()}, size=${output.length()}, picker=${app.reportLibrary.externalPicker}",
        failure,
      )
    }
    val entries = mutableMapOf<String, ByteArray>()
    java.util.zip.ZipInputStream(output.inputStream()).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        entries[entry.name] = zip.readBytes()
      }
    }
    assertTrue(entries.getValue("document.json").decodeToString().contains("Exported content"))
    assertFalse(
      org.json
        .JSONObject(entries.getValue("manifest.json").decodeToString())
        .getBoolean("encrypted")
    )
    assertFalse(app.reportLibrary.externalPicker)
    output.delete()
    vm.discardForLock()
  }
}
