package com.karandaev.fieldfolio.acode.web

import android.app.Application
import android.webkit.WebViewClient
import com.karandaev.fieldfolio.core.document.runtime.DocumentWebClient
import com.karandaev.fieldfolio.core.document.runtime.createDocumentWebView
import com.karandaev.fieldfolio.core.document.runtime.documentHost
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Both hosts must initialize safely before and after content-capture APIs exist. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class WebViewCompatibilityTest {
  @Test
  fun editorHostKeepsPrivateStateOutOfPlatformStorageOnSupportedApis() {
    val view = createNotebookWebView(RuntimeEnvironment.getApplication(), WebViewClient(), false)
    try {
      assertFalse(view.isSaveEnabled)
      assertFalse(view.settings.allowFileAccess)
      assertFalse(view.settings.allowContentAccess)
    } finally {
      view.destroy()
    }
  }

  @Test
  fun executableDocumentKeepsReadOnlyStorageBoundaryOnSupportedApis() {
    val context = RuntimeEnvironment.getApplication()
    val client =
      DocumentWebClient(
        context,
        documentHost("compat", "document", "public"),
        "scope",
        null,
        {},
        {},
      )
    val view = createDocumentWebView(context, client)
    try {
      assertFalse(view.isSaveEnabled)
      assertFalse(view.settings.allowFileAccess)
      assertFalse(view.settings.allowContentAccess)
      assertFalse(view.settings.domStorageEnabled)
    } finally {
      client.close()
      view.destroy()
    }
  }
}
