package com.karandaev.fieldfolio.acode.web

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.karandaev.fieldfolio.acode.core.logging.NotebookLog as Log
import com.karandaev.fieldfolio.acode.core.logging.NotebookLog
import org.json.JSONTokener

/** The only JS-to-Kotlin bridge; it is installed exclusively on the trusted editor. */
internal class EditorBridge(
  context: Context,
  private val onChange: (String) -> Unit,
  private val onReady: () -> Unit,
  private val onCursorSelection: (Int, Int, Int) -> Unit,
  private val onSearchStateChange: (String) -> Unit,
  private val onFullscreenChange: (Boolean) -> Unit,
  private val onKeyboardToggle: () -> Unit,
  private val onKeepKeyboardAfterCut: (Int) -> Unit,
  private val onBeginClipboardCut: (Int) -> Unit,
  private val onCancelClipboardCut: () -> Unit,
  private val onNativeContextMenuDisabled: (Boolean) -> Unit,
  private val onSystemConfiguration: () -> String,
  private val onError: (String) -> Unit,
  private val onSave: () -> Unit = {},
) {
  private val handler = Handler(Looper.getMainLooper())
  @Volatile private var closed = false

  internal fun close() {
    closed = true
    handler.removeCallbacksAndMessages(null)
  }

  private fun post(action: () -> Unit) {
    if (!closed) handler.post { if (!closed) action() }
  }

  private val clipboard =
    context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

  @JavascriptInterface
  fun copyToClipboard(text: String) {
    post {
      if (EditorDiagnosticsControl.enabled)
        NotebookLog.trace(
          NotebookLog.Area.IME,
          "clipboard-write-begin",
          "clock=${System.currentTimeMillis()}",
        )
      clipboard.setPrimaryClip(ClipData.newPlainText("Fieldfolio", text))
      if (EditorDiagnosticsControl.enabled)
        NotebookLog.trace(
          NotebookLog.Area.IME,
          "clipboard-write-end",
          "clock=${System.currentTimeMillis()}",
        )
    }
  }

  @JavascriptInterface
  fun readClipboard(): String {
    if (closed) return ""
    return runCatching { clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty() }
      .getOrDefault("")
  }

  @JavascriptInterface
  fun toggleKeyboard() {
    post { onKeyboardToggle() }
  }

  @JavascriptInterface fun diagnosticsEnabled(): Boolean = EditorDiagnosticsControl.enabled

  @JavascriptInterface fun diagnosticOptions(): String = EditorDiagnosticsControl.optionsJson()

  @JavascriptInterface
  fun trace(json: String) {
    if (EditorDiagnosticsControl.enabled) NotebookLog.webTrace(json)
  }

  @JavascriptInterface
  fun traceBatch(json: String) {
    EditorDiagnosticsControl.traceBatch(json)
  }

  @JavascriptInterface
  fun keepKeyboardAfterCut(revision: Int) {
    post { onKeepKeyboardAfterCut(revision) }
  }

  @JavascriptInterface
  fun beginClipboardCut(revision: Int) {
    post { onBeginClipboardCut(revision) }
  }

  @JavascriptInterface
  fun cancelClipboardCut() {
    post { onCancelClipboardCut() }
  }

  @JavascriptInterface
  fun setNativeContextMenuDisabled(disabled: Boolean) {
    post { onNativeContextMenuDisabled(disabled) }
  }

  @JavascriptInterface fun systemConfiguration(): String = onSystemConfiguration()

  @JavascriptInterface
  fun setFullscreen(enabled: Boolean) {
    post { onFullscreenChange(enabled) }
  }

  @JavascriptInterface
  fun save() {
    post { onSave() }
  }

  @JavascriptInterface
  fun changed(source: String) {
    if (source.length <= 8 * 1024 * 1024) post { onChange(source) }
    else post { onError("Text exceeds the editor limit") }
  }

  @JavascriptInterface
  fun cursorSelection(anchor: Int, head: Int, scrollTop: Int) {
    post { onCursorSelection(anchor, head, scrollTop) }
  }

  /** Search terms are forwarded to the in-memory WorkspaceViewModel only. */
  @JavascriptInterface
  fun searchState(json: String) {
    if (json.length <= 8192) post { onSearchStateChange(json) }
  }

  @JavascriptInterface
  fun ready() {
    Log.i("NotebookEditor", "JS bridge: ready()")
    post { onReady() }
  }

  @JavascriptInterface
  fun failed(message: String) {
    Log.e("NotebookEditor", "Editor startup failed")
    post { onError(message.take(2000)) }
  }
}

/** evaluateJavascript gives a JSON-encoded JS string, not its raw body. */
fun readEditorSource(webView: WebView?, fallback: String, callback: (String) -> Unit) {
  if (webView == null) {
    callback(fallback)
    return
  }
  webView.evaluateJavascript("window.EditorApi?.getSource()") { raw ->
    val source = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: fallback
    callback(source)
  }
}
