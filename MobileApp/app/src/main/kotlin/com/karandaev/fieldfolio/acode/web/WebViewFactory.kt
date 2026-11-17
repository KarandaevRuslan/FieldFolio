package com.karandaev.fieldfolio.acode.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.karandaev.fieldfolio.acode.core.logging.NotebookLog as Log
import java.io.ByteArrayInputStream

internal const val WEB_BASE = "https://appassets.androidplatform.net/assets/acode/"
private const val ASSET_HOST = "appassets.androidplatform.net"
private const val WEB_ASSET_PREFIX = "/assets/acode/"

private fun isCriticalWebResource(uri: Uri): Boolean =
  uri.host == ASSET_HOST &&
    uri.path?.startsWith(WEB_ASSET_PREFIX) == true &&
    listOf(".html", ".js", ".css", ".wasm").any { uri.path!!.endsWith(it) }

/**
 * Offline asset loader with diagnostic traces, not a network proxy. No notebook source text, DOM
 * contents or document IDs are logged.
 */
internal class LocalAssetsClient(
  context: Context,
  private val owner: String = "WebView",
  private val pageFinished: (() -> Unit)? = null,
  private val onAssetError: (String) -> Unit = {},
  private val notebookResource: ((Uri) -> WebResourceResponse?)? = null,
) : WebViewClient() {
  private val main = Handler(Looper.getMainLooper())
  private val loader =
    WebViewAssetLoader.Builder()
      .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
      .build()

  override fun shouldInterceptRequest(
    view: WebView,
    request: WebResourceRequest,
  ): WebResourceResponse? {
    val uri = request.url
    if (uri.scheme != "https" || uri.host != ASSET_HOST || uri.port != -1) {
      Log.w(
        "NotebookAssets",
        "$owner blocked external request: ${uri.scheme}://${uri.host}${uri.path}",
      )
      return blocked()
    }
    if (uri.path?.startsWith("/notebook-resources/") == true)
      return notebookResource?.invoke(uri) ?: blocked()
    if (uri.path == "/favicon.ico") return emptyFavicon()

    if (uri.path?.startsWith(WEB_ASSET_PREFIX) != true) return blocked()
    if (
      uri.pathSegments.any { it == "." || it == ".." } ||
        uri.encodedPath.orEmpty().contains("%", true)
    )
      return blocked()
    val response = loader.shouldInterceptRequest(uri)
    val critical = request.isForMainFrame || isCriticalWebResource(uri)
    if (critical) {
      Log.d(
        "NotebookAssets",
        "$owner request ${request.method} $uri -> " +
          if (response == null) "MISSING" else "${response.statusCode} ${response.mimeType}",
      )
    }
    if (response == null && critical) {
      main.post { onAssetError("Bundled asset missing: $uri") }
    }
    return response ?: blocked()
  }

  override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
    Log.w("NotebookAssets", "$owner navigation prevented: ${request.url}")
    return true
  }

  override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
    super.onPageStarted(view, url, favicon)
    Log.i("NotebookAssets", "$owner page started: $url")
  }

  override fun onPageFinished(view: WebView, url: String?) {
    super.onPageFinished(view, url)
    Log.i("NotebookAssets", "$owner page finished: $url")
    pageFinished?.invoke()
  }

  override fun onReceivedHttpError(
    view: WebView,
    request: WebResourceRequest,
    response: WebResourceResponse,
  ) {
    super.onReceivedHttpError(view, request, response)
    Log.e(
      "NotebookAssets",
      "$owner HTTP ${response.statusCode} ${response.reasonPhrase}: ${request.url}; main=${request.isForMainFrame}",
    )
    if (
      response.statusCode >= 400 &&
        request.url.host == ASSET_HOST &&
        (request.isForMainFrame || isCriticalWebResource(request.url))
    ) {
      main.post { onAssetError("HTTP ${response.statusCode}: ${request.url}") }
    }
  }

  override fun onReceivedError(
    view: WebView,
    request: WebResourceRequest,
    error: WebResourceError,
  ) {
    super.onReceivedError(view, request, error)
    Log.e(
      "NotebookAssets",
      "$owner network/error ${error.errorCode}: ${error.description}; " +
        "main=${request.isForMainFrame}; url=${request.url}",
    )
    if (request.isForMainFrame || isCriticalWebResource(request.url)) {
      main.post {
        onAssetError("WebView error ${error.errorCode}: ${error.description} (${request.url})")
      }
    }
  }

  override fun onRenderProcessGone(
    view: WebView,
    detail: android.webkit.RenderProcessGoneDetail,
  ): Boolean {
    (view.getTag(com.karandaev.fieldfolio.R.id.editor_bridge_handle) as? EditorBridge)?.close()
    view.setTag(com.karandaev.fieldfolio.R.id.editor_bridge_handle, null)
    view.removeJavascriptInterface("NativeEditor")
    main.post { onAssetError("The editor renderer stopped. Retry to restore the in-memory draft.") }
    (view.parent as? android.view.ViewGroup)?.removeView(view)
    view.destroy()
    return true
  }

  private fun emptyFavicon() =
    WebResourceResponse("image/x-icon", null, ByteArrayInputStream(ByteArray(0)))

  private fun blocked() =
    WebResourceResponse(
      "text/plain",
      "UTF-8",
      403,
      "Blocked",
      emptyMap(),
      ByteArrayInputStream(ByteArray(0)),
    )
}

internal fun setWebViewBackground(view: WebView, dark: Boolean) {
  view.setBackgroundColor(Color.parseColor(if (dark) "#10131a" else "#f7f8fc"))
}

@SuppressLint("SetJavaScriptEnabled")
internal fun createNotebookWebView(
  context: Context,
  client: WebViewClient,
  dark: Boolean,
  editor: Boolean = false,
): WebView =
  (if (editor) EditorActionModeWebView(context) else WebView(context)).apply {
    setWebViewBackground(this, dark)
    isFocusable = true
    isFocusableInTouchMode = true
    overScrollMode = View.OVER_SCROLL_NEVER
    isSaveEnabled = false
    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    if (android.os.Build.VERSION.SDK_INT >= 30)
      importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
    settings.saveFormData = false
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    settings.setGeolocationEnabled(false)
    settings.mediaPlaybackRequiresUserGesture = true
    settings.defaultTextEncodingName = "UTF-8"
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.setSupportMultipleWindows(false)
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    webViewClient = client
  }

internal fun webPageUrl(page: String, dark: Boolean, language: String): String =
  WEB_BASE +
    page +
    "?theme=" +
    (if (dark) "dark" else "light") +
    "&language=" +
    (if (language == "ru") "ru" else "en")
