package com.karandaev.fieldfolio.acode.web

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.acode.core.logging.NotebookLog as Log
import com.karandaev.fieldfolio.acode.core.logging.NotebookLog
import com.karandaev.fieldfolio.acode.i18n.localizedString
import com.karandaev.fieldfolio.acode.settings.EditorPreferences
import com.karandaev.fieldfolio.acode.settings.EditorTheme
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Owns CodeMirror WebView lifecycle and native/editor handshake, not document state. */
@Composable
fun NotebookEditorWebView(
  id: String,
  initialSource: String,
  externalRevision: Int = 0,
  dark: Boolean,
  language: String,
  editorPreferences: EditorPreferences,
  onChanged: (String) -> Unit,
  selectionAnchor: Int,
  selectionHead: Int,
  editorScrollTop: Int,
  onCursorSelection: (Int, Int, Int) -> Unit,
  searchStateJson: String,
  onSearchStateChange: (String) -> Unit,
  fullscreen: Boolean,
  onFullscreenChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  active: Boolean = true,
  fileName: String = "notebook.html",
  resourceFile: Boolean = false,
  documentRuntime: Boolean = false,
  readOnly: Boolean = false,
  onSaveRequested: () -> Unit = {},
  onWebView: (WebView?) -> Unit = {},
) {
  val editorTheme = editorPreferences.theme
  val editorFontSize = editorPreferences.fontSize
  val editorWordWrap = editorPreferences.wordWrap
  val editorAutocomplete = editorPreferences.autocomplete
  val editorQuickTools = editorPreferences.quickTools
  val editorDark =
    when (editorTheme) {
      EditorTheme.DARK -> true
      EditorTheme.LIGHT -> false
      EditorTheme.FOLLOW_APP -> dark
    }
  val latestSource by rememberUpdatedState(initialSource)
  val latestSave by rememberUpdatedState(onSaveRequested)
  val latestWebViewCallback by rememberUpdatedState(onWebView)
  val latestChanged by rememberUpdatedState(onChanged)
  val latestCursorSelection by rememberUpdatedState(onCursorSelection)
  val latestSearchStateChange by rememberUpdatedState(onSearchStateChange)
  val latestSearchState by rememberUpdatedState(searchStateJson)
  val latestFullscreenChange by rememberUpdatedState(onFullscreenChange)
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var web by remember(id) { mutableStateOf<WebView?>(null) }
  var ready by remember(id) { mutableStateOf(false) }
  var visible by remember(id) { mutableStateOf(false) }
  var error by remember(id) { mutableStateOf<String?>(null) }
  var reload by remember(id) { mutableStateOf(0) }

  Box(modifier.clipToBounds().background(if (dark) Color(0xFF10131A) else Color(0xFFF7F8FC))) {
    key(reload) {
      AndroidView(
        factory = { context ->
          val factoryRevision = reload
          createNotebookWebView(
              context,
              LocalAssetsClient(
                context,
                owner = "CodeMirror",
                onAssetError = {
                  Log.e("NotebookEditor", "Editor asset load failed")
                  error = it
                  ready = false
                  visible = false
                },
              ),
              editorDark,
              editor = true,
            )
            .apply {
              webChromeClient =
                object : WebChromeClient() {
                  override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    val priority =
                      when (msg.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> android.util.Log.ERROR
                        ConsoleMessage.MessageLevel.WARNING -> android.util.Log.WARN
                        else -> android.util.Log.DEBUG
                      }
                    NotebookLog.webConsole(
                      NotebookLog.Area.EDITOR,
                      priority,
                      msg.message(),
                      msg.sourceId(),
                      msg.lineNumber(),
                    )
                    // Console diagnostics are non-fatal. Fatal editor failures
                    // reach NativeEditor.failed() via editor-boot.js; missing
                    // JS/CSS reaches LocalAssetsClient. (A favicon is not fatal.)
                    return true
                  }
                }
              val nativeBridge =
                EditorBridge(
                  context = context,
                  onChange = { if (!readOnly) latestChanged(it) },
                  onCursorSelection = { anchor, head, scroll ->
                    latestCursorSelection(anchor, head, scroll)
                  },
                  onSearchStateChange = { latestSearchStateChange(it) },
                  onFullscreenChange = { latestFullscreenChange(it) },
                  onKeyboardToggle = { web?.let(EditorKeyboardController::toggle) },
                  onKeepKeyboardAfterCut = { revision ->
                    (web as? EditorActionModeWebView)?.let {
                      EditorKeyboardController.keepAfterCut(it, revision)
                    }
                  },
                  onBeginClipboardCut = { revision ->
                    (web as? EditorActionModeWebView)?.armKeyboardAfterCut(revision)
                  },
                  onCancelClipboardCut = {
                    (web as? EditorActionModeWebView)?.cancelKeyboardRequest("beforeinput")
                  },
                  onNativeContextMenuDisabled = { disabled ->
                    (web as? EditorActionModeWebView)?.setNativeContextMenuDisabled(disabled)
                  },
                  onSystemConfiguration = {
                    (web as? EditorActionModeWebView)?.systemConfigurationJson()
                      ?: "{\"keyboardHeight\":0,\"hardKeyboardHidden\":2}"
                  },
                  onReady = {
                    Log.i("NotebookEditor", "editor bridge handshake OK")
                    ready = true
                    error = null
                    (web as? EditorActionModeWebView)?.let(EditorDiagnosticsControl::attach)
                  },
                  onSave = { if (!readOnly) latestSave() },
                  onError = {
                    Log.e("NotebookEditor", "Editor bridge failure")
                    error = it
                    ready = false
                    visible = false
                  },
                )
              setTag(R.id.editor_bridge_handle, nativeBridge)
              addJavascriptInterface(nativeBridge, "NativeEditor")
              web = this
              latestWebViewCallback(this)
              Log.i("NotebookEditor", "loading editor entry point")
              loadUrl(webPageUrl("editor.html", editorDark, language))
            }
        },
        update = { view -> updateWorkspaceWebViewVisibility(view, active) },
        onRelease = { view ->
          if (web === view) {
            web = null
            latestWebViewCallback(null)
            ready = false
            visible = false
          }
          (view.getTag(R.id.editor_bridge_handle) as? EditorBridge)?.close()
          view.setTag(R.id.editor_bridge_handle, null)
          view.removeJavascriptInterface("NativeEditor")
          view.stopLoading()
          view.destroy()
        },
        // Android IME animates this size every frame. Logging and performing
        // extra main-thread work on each frame makes the keyboard feel slow.
        modifier =
          Modifier.fillMaxSize().onSizeChanged {
            EditorDiagnosticsControl.traceLayout("viewport-compose-webview", it.width, it.height)
          },
      )
    }
    if (active)
      WebLoadOverlay(visible && error == null, error, language) {
        error = null
        ready = false
        visible = false
        reload += 1
        Log.i("NotebookEditor", "manual retry")
        // key(reload) creates a fresh WebView, including recovery after renderer death.
      }
  }

  DisposableEffect(web) {
    val view = web as? EditorActionModeWebView
    if (view != null) EditorDiagnosticsControl.attach(view)
    onDispose { if (view != null) EditorDiagnosticsControl.detach(view) }
  }

  // A clipboard popup can briefly take window focus while this activity
  // remains resumed. Actual navigation/backgrounding must cancel Cut IME.
  DisposableEffect(web, lifecycleOwner) {
    val view = web as? EditorActionModeWebView
    val observer = LifecycleEventObserver { _, event ->
      // Log the lifecycle owner separately from window/view focus. It
      // may be a navigation entry; window focus alone cannot tell us
      // whether this cancellation was triggered in the failed Cut.
      if (view != null && event == Lifecycle.Event.ON_PAUSE) {
        view.logDiagnostic("ime-lifecycle-pause", view.keyboardRevision)
        view.cancelKeyboardRequest("activity-pause")
      } else if (view != null && event == Lifecycle.Event.ON_RESUME) {
        view.logDiagnostic("ime-lifecycle-resume", view.keyboardRevision)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      view?.cancelKeyboardRequest("dispose")
    }
  }

  // Acode owns the stack of Find, palette and settings overlays. Let it
  // consume Back first, then delegate to the existing app/fullscreen handler.
  DisposableEffect(backDispatcher, web, ready, active) {
    val dispatcher = backDispatcher
    val view = web
    if (dispatcher == null || view == null || !ready || !active) {
      onDispose {}
    } else {
      var disposed = false
      val callback =
        object : OnBackPressedCallback(true) {
          private var pending = false

          override fun handleOnBackPressed() {
            (view as? EditorActionModeWebView)?.cancelKeyboardRequest("back-dispatch")
            if (pending) return
            pending = true
            view.evaluateJavascript("window.EditorApi?.handleBack?.() === true") { handled ->
              pending = false
              if (disposed || !view.isAttachedToWindow) return@evaluateJavascript
              if (handled != "true") {
                isEnabled = false
                try {
                  dispatcher.onBackPressed()
                } finally {
                  if (view.isAttachedToWindow) isEnabled = true
                }
              }
            }
          }
        }
      dispatcher.addCallback(callback)
      onDispose {
        disposed = true
        callback.remove()
      }
    }
  }

  // Preview hides the live editor without destroying CodeMirror state. Do
  // not restore focus on re-entry: a tap or the keyboard action owns that.
  LaunchedEffect(web, ready, active) {
    val view = web ?: return@LaunchedEffect
    if (ready && !active) {
      view.evaluateJavascript("window.EditorApi?.suspendKeyboardFocus?.()") { view.clearFocus() }
    }
  }

  // WebView.onPageFinished may fire before ES modules have executed.
  LaunchedEffect(id, web, reload) {
    val view = web ?: return@LaunchedEffect
    Log.i("NotebookEditor", "waiting for window.EditorApi (10s timeout)")
    repeat(100) {
      if (ready || error != null) return@LaunchedEffect
      view.evaluateJavascript("Boolean(window.EditorApi?.setSource)") { result ->
        if (result == "true") {
          Log.i("NotebookEditor", "window.EditorApi.setSource detected")
          ready = true
          error = null
        }
      }
      delay(100)
    }
    if (!ready && error == null) {
      Log.e("NotebookEditor", "timeout: window.EditorApi not created after 10s")
      error = localizedString(context, language, R.string.editor_failed_to_start)
    }
  }
  LaunchedEffect(web, ready, fullscreen) {
    if (ready) web?.evaluateJavascript("window.EditorApi?.setFullscreenState($fullscreen)", null)
  }
  // Preferences update independently of the document/session initialization.
  // Moving the font slider must never reload source or reset text selection.
  LaunchedEffect(
    web,
    ready,
    dark,
    language,
    editorTheme,
    editorFontSize,
    editorWordWrap,
    editorAutocomplete,
    editorQuickTools,
  ) {
    val view = web ?: return@LaunchedEffect
    if (!ready) return@LaunchedEffect
    val options =
      """(function(){window.EditorApi?.setPreferences({
            dark:$dark,
            language:${JSONObject.quote(language)},
            theme:${JSONObject.quote(when (editorTheme) {
                EditorTheme.LIGHT -> "light"
                EditorTheme.DARK -> "dark"
                EditorTheme.FOLLOW_APP -> "system"
            })},
            fontSize:$editorFontSize,
            wordWrap:$editorWordWrap,
            autocomplete:$editorAutocomplete,
            showQuickTools:$editorQuickTools
        })})()"""
        .trimIndent()
    view.evaluateJavascript(options) { result ->
      Log.d("NotebookEditor", "editor preferences applied: fontSize=$editorFontSize")
    }
    setWebViewBackground(view, editorDark)
  }

  // Apply only EXTERNAL edits; never echo each JS keystroke back to CodeMirror.
  // An echo can race fast input and reset the caret to an older document revision.
  LaunchedEffect(externalRevision, ready, web) {
    val view = web ?: return@LaunchedEffect
    if (!ready) return@LaunchedEffect
    val encoded = JSONObject.quote(initialSource)
    view.evaluateJavascript(
      "(function(){if(window.EditorApi && window.EditorApi.getSource()!==$encoded){window.EditorApi.setSource($encoded);window.EditorApi.refresh()}})()",
      null,
    )
  }

  LaunchedEffect(web, ready, id) {
    if (!ready) return@LaunchedEffect
    val view = web ?: return@LaunchedEffect
    Log.i("NotebookEditor", "initializing CodeMirror DOM and source")
    // Apply both source and appearance before uncovering the WebView.
    val script =
      """
            (function () {
                const api = window.EditorApi;
                const host = document.getElementById('editor');
                if (!api || !host) return 'CodeMirror API or DOM host is missing';
                const text = ${JSONObject.quote(latestSource)};
                api.setDocument?.(${JSONObject.quote(fileName)}, ${JSONObject.quote(if(documentRuntime)"document"else if(resourceFile)"resource"else"notebook")});
                api.setSource(text);
                api.setSessionKey(${JSONObject.quote(id)});
                if ($readOnly && typeof api.setReadOnly !== 'function') return 'Read-only runtime is unavailable';
                api.setReadOnly?.($readOnly);
                api.restoreSession($selectionAnchor, $selectionHead, $editorScrollTop);
                api.setFullscreenState($fullscreen);
                api.restoreSearchState(${JSONObject.quote(latestSearchState)});
                if (api.getSource() !== text) return 'CodeMirror did not retain the document';
                if (!host.querySelector('.cm-editor')) return 'CodeMirror editor node is missing';
                api.refresh();
                return true;
            })()
        """
        .trimIndent()
    view.evaluateJavascript(script) { result ->
      Log.i("NotebookEditor", "initialization check returned $result")
      if (result == "true" && error == null) {
        // Let Compose/WebView finish one layout pass before evaluating
        // viewport dimensions and showing the editor.
        view.postDelayed(
          {
            Log.i("NotebookEditor", "Native WebView bounds: ${view.width}x${view.height}")
            view.evaluateJavascript("JSON.stringify(window.EditorApi.getDiagnostics())") { details
              ->
              Log.i("NotebookEditor", "DOM diagnostics: $details")
              if (view.height == 0) {
                Log.e("NotebookEditor", "WebView has zero native height")
              }
            }
            view.requestLayout()
            view.invalidate()
            visible = true
          },
          80,
        )
      } else if (result != "true") {
        Log.e("NotebookEditor", "initialization failed: $result")
        error = "CodeMirror initialization failed: $result"
        ready = false
        visible = false
      }
    }
    setWebViewBackground(view, editorDark)
  }
}
