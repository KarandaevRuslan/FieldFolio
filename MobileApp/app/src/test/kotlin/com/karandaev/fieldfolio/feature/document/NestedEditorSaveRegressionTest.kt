package com.karandaev.fieldfolio.feature.document

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
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
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.DocumentFileBrowser
import com.karandaev.fieldfolio.feature.document.editor.DocumentNodeEditor
import com.karandaev.fieldfolio.feature.document.editor.TextPropertiesDialog
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import com.karandaev.fieldfolio.feature.document.state.action
import org.json.JSONObject
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
  qualifiers = "en-rUS-w360dp-h800dp-port-mdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NestedEditorSaveRegressionTest {
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

  private fun web(view: View): WebView? =
    if (view is WebView && Shadows.shadowOf(view).getJavascriptInterface("NativeEditor") != null)
      view
    else
      (view as? ViewGroup)?.let { group ->
        (0 until group.childCount).firstNotNullOfOrNull { web(group.getChildAt(it)) }
      }

  private fun acodeSave(text: String) {
    compose.onAllNodesWithContentDescription("Edit with Acode").onFirst().let { action ->
      if (!action.isDisplayed()) action.performScrollTo()
      action.performClick()
    }
    compose.onNodeWithTag("acode-editor-window").assertIsDisplayed()
    compose.onAllNodesWithContentDescription("Save").onLast().performClick()
    compose.runOnIdle {
      val view = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::web)!!
      val shadow = Shadows.shadowOf(view)
      assertEquals("window.EditorApi?.getSource()", shadow.lastEvaluatedJavascript)
      shadow.lastEvaluatedJavascriptCallback.onReceiveValue(JSONObject.quote(text))
    }
    compose.onNodeWithTag("acode-editor-window").assertDoesNotExist()
  }

  private fun editThenSave(initial: String, replacement: String, kind: BlockKind = BlockKind.TEXT) {
    val node =
      DocumentNode(
        id = "text-flow",
        name = "Text",
        kind = kind,
        body = initial,
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
    show {
      DocumentNodeEditor(node, emptyMap(), {}, importFile = { _, _ -> }) { next, _ -> saved = next }
    }
    acodeSave(replacement)
    if (kind != BlockKind.DOCUMENT) compose.onAllNodesWithText(replacement).onFirst().assertExists()
    compose.onNodeWithContentDescription("Save").performClick()
    compose.runOnIdle { assertEquals(replacement, saved?.body) }
  }

  @Test
  fun headingSubtitleThroughNestedAcodeSavesLatestBody() =
    editThenSave("Old subtitle", "New subtitle", BlockKind.HEADING)

  @Test
  fun modelNotesThroughNestedAcodeSaveLatestBody() =
    editThenSave("Old model note", "New model note", BlockKind.MODEL_RESULT)

  @Test
  fun documentThroughNestedAcodeSavesLatestSource() =
    editThenSave(
      "<notebook></notebook>",
      "<notebook><script id=\"1\" type=\"text/markdown\">Changed</script></notebook>",
      BlockKind.DOCUMENT,
    )

  @Test
  fun textFileThroughNestedAcodeSavesLatestBytes() {
    val node =
      DocumentNode(id = "file", name = "File", kind = BlockKind.TEXT_FILE, asset = "file.txt")
    var saved: ByteArray? = null
    show {
      TextPropertiesDialog(
        node,
        mapOf("file.txt" to "Old file".toByteArray()),
        {},
        importFile = { _, bytes -> saved = bytes },
      ) { _, _ ->
        fail("Changed file must use atomic resource update")
      }
    }
    acodeSave("New file")
    compose.waitUntil(5000) {
      !compose
        .onNodeWithContentDescription("Save")
        .fetchSemanticsNode()
        .config
        .contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
    }
    compose.onNodeWithContentDescription("Save").performClick()
    compose.runOnIdle { assertEquals("New file", saved?.toString(Charsets.UTF_8)) }
  }

  @Test
  fun editorGoToPathOpensLeafAndClosesPathDialog() {
    val app = compose.activity.application as MyApplication
    val vm = DocumentViewModel(app.reportLibrary, app.catalogModelRunner, 0, null, null)
    val folder = vm.add(BlockKind.FOLDER, "Folder")
    vm.navigate(folder.id)
    val item = vm.add(BlockKind.TEXT, "Item")
    vm.navigate(null)
    var opened: DocumentNode? = null
    show { DocumentFileBrowser(vm, vm.folder, vm::navigate, { opened = it }, {}, {}) }
    compose.onNodeWithContentDescription("Go to path").performClick()
    compose.onNodeWithTag("document-path").performTextReplacement("/Folder/Item")
    compose.onNodeWithContentDescription("Open").performClick()
    compose.runOnIdle {
      assertEquals(item.id, opened?.id)
      assertEquals(folder.id, vm.folder)
    }
    compose.onNodeWithTag("document-path").assertDoesNotExist()
  }

  @Test
  @Config(qualifiers = "en-rUS-w800dp-h360dp-land-mdpi")
  fun resetConfirmationAlsoFillsLandscapeDialogBody() = resetConfirmationFillsDialogBody()

  @Test
  fun resetConfirmationFillsDialogBody() {
    show {
      com.karandaev.fieldfolio.feature.settings.dialogs.SettingsResetDataDialogHost(
        true,
        "",
        "RESET",
        false,
        {},
        {},
        {},
        {},
      )
    }
    val field =
      compose
        .onNode(hasSetTextAction() and hasText("Confirmation"))
        .fetchSemanticsNode()
        .boundsInRoot
    val panel = compose.onNodeWithTag("overlay-panel").fetchSemanticsNode().boundsInRoot
    assertTrue("Confirmation fills available body width", field.width >= panel.width - 52f)
  }

  @Test
  fun creatingTextThroughNestedAcodeSavesLatestBody() = editThenSave("", "New text from Acode")

  @Test
  fun replacingTextThroughNestedAcodeSavesLatestBody() =
    editThenSave("Old text", "Changed text from Acode")
}
