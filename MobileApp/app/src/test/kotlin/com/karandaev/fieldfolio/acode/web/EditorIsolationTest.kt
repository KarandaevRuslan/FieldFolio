package com.karandaev.fieldfolio.acode.web

import android.app.Application
import android.net.Uri
import android.os.Looper
import android.webkit.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EditorIsolationTest {
  private val context
    get() = RuntimeEnvironment.getApplication()

  @Test
  fun detachedEditorBridgeCannotSaveChangeReadClipboardOrRunQueuedCallbacks() {
    val events = mutableListOf<String>()
    val bridge =
      EditorBridge(
        context,
        { events += "changed" },
        { events += "ready" },
        { _, _, _ -> },
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        { "{}" },
        { events += "error" },
        { events += "save" },
      )
    bridge.changed("source")
    bridge.save()
    bridge.copyToClipboard("private")
    bridge.close()
    bridge.changed("late")
    bridge.save()
    bridge.ready()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertTrue(events.isEmpty())
    assertEquals("", bridge.readClipboard())
  }

  @Test
  fun editorCannotLoadExecutableDocumentsOtherAssetsOrNativeFiles() {
    val view = WebView(context)
    val client = LocalAssetsClient(context)
    fun request(url: String) =
      object : WebResourceRequest {
        override fun getUrl() = Uri.parse(url)

        override fun isForMainFrame() = false

        override fun isRedirect() = false

        override fun hasGesture() = false

        override fun getMethod() = "GET"

        override fun getRequestHeaders() = emptyMap<String, String>()
      }
    try {
      for (url in
        listOf(
          "https://appassets.androidplatform.net/assets/document/document-host.html",
          "https://appassets.androidplatform.net/assets/acode/%2e%2e/document/document.html",
          "https://appassets.androidplatform.net:443/assets/acode/editor.html",
          "file:///data/data/com.karandaev.fieldfolio/files/secret",
          "content://media/1",
          "https://outside.invalid/",
        )) assertEquals(url, 403, client.shouldInterceptRequest(view, request(url))!!.statusCode)
    } finally {
      view.destroy()
    }
  }
}
