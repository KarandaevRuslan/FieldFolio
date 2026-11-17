package com.karandaev.fieldfolio.core.document.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.*
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.security.MessageDigest

/** Deliberately different from Acode's appassets origin, also unique to a runtime lease. */
internal fun documentHost(report: String, node: String, lease: String): String =
  "document-" +
    MessageDigest.getInstance("SHA-256")
      .digest("$report\u0000$node\u0000$lease".toByteArray())
      .take(16)
      .joinToString("") { "%02x".format(it) } +
    ".fieldfolio.invalid"

internal class DocumentWebClient(
  context: Context,
  val host: String,
  val scope: String,
  @Volatile var resources: DocumentResources?,
  private val finished: () -> Unit,
  private val failed: () -> Unit,
) : WebViewClient() {
  @Volatile private var closed = false
  private val loader =
    WebViewAssetLoader.Builder()
      .setDomain(host)
      .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context.applicationContext))
      .build()
  val page = "https://$host/assets/document/document-host.html"
  val renderer = "https://$host/assets/document/document.html"

  fun close() {
    closed = true
    resources = null
  }

  internal fun intercept(uri: Uri, method: String = "GET"): WebResourceResponse {
    if (
      closed ||
        method != "GET" ||
        uri.scheme != "https" ||
        uri.host != host ||
        uri.port != -1 ||
        uri.query != null ||
        uri.fragment != null
    )
      return blocked()
    val path = uri.encodedPath.orEmpty()
    if (path.startsWith("/vfs/"))
      return try {
        resources?.response(uri, host, scope) ?: blocked()
      } catch (_: Exception) {
        blocked()
      }
    if (path == "/favicon.ico")
      return WebResourceResponse("image/x-icon", null, ByteArrayInputStream(ByteArray(0)))
    val allowed =
      path in setOf("/assets/document/document.html", "/assets/document/document-host.html") ||
        path.matches(Regex("/assets/document/assets/[A-Za-z0-9_.-]+\\.(js|css|woff2?|ttf|wasm)"))
    if (!allowed) return blocked()
    val response = loader.shouldInterceptRequest(uri) ?: return blocked()
    response.responseHeaders =
      (response.responseHeaders ?: emptyMap()) +
        mapOf(
          "Cache-Control" to "no-store",
          "X-Content-Type-Options" to "nosniff",
          "Access-Control-Allow-Origin" to "null",
          "Referrer-Policy" to "no-referrer",
          "Permissions-Policy" to "camera=(), microphone=(), geolocation=()",
        )
    if (path == "/assets/document/document.html")
      response.responseHeaders =
        response.responseHeaders + ("Content-Security-Policy" to rendererCsp(host))
    return response
  }

  override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
    intercept(request.url, request.method)

  // Android still calls this overload for some subresources on older WebView versions.
  @Deprecated("Legacy Android entry point")
  override fun shouldInterceptRequest(view: WebView?, url: String?) =
    intercept(Uri.parse(url ?: ""))

  override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
    request.isForMainFrame || request.url.toString().substringBefore('#') != renderer

  @Deprecated("Legacy Android entry point")
  override fun shouldOverrideUrlLoading(view: WebView?, url: String?) =
    url?.substringBefore('#') != renderer

  override fun onPageFinished(view: WebView, url: String?) {
    if (url == page && !closed) finished()
  }

  override fun onReceivedError(
    view: WebView,
    request: WebResourceRequest,
    error: WebResourceError,
  ) {
    if (request.isForMainFrame) failed()
  }

  override fun onReceivedSslError(
    view: WebView,
    handler: SslErrorHandler,
    error: android.net.http.SslError,
  ) {
    handler.cancel()
  }

  override fun onReceivedHttpAuthRequest(
    view: WebView,
    handler: HttpAuthHandler,
    host: String,
    realm: String,
  ) {
    handler.cancel()
  }

  override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
    request.cancel()
  }

  override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
    close()
    failed()
    (view.parent as? android.view.ViewGroup)?.removeView(view)
    view.destroy()
    return true
  }

  companion object {
    internal fun rendererCsp(host: String) =
      "default-src 'none'; script-src https://$host 'unsafe-eval'; style-src https://$host 'unsafe-inline'; img-src https://$host data: blob:; media-src https://$host data: blob:; font-src https://$host data:; connect-src https://$host; worker-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors https://$host; sandbox allow-scripts"

    fun blocked() =
      WebResourceResponse(
        "text/plain",
        "UTF-8",
        403,
        "Blocked",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)),
      )
  }
}

/**
 * No addJavascriptInterface, file access, persistent storage, popups or native permission prompts.
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun createDocumentWebView(context: Context, client: DocumentWebClient): WebView =
  DocumentRuntimeWebView(context).apply {
    isSaveEnabled = false
    importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    if (android.os.Build.VERSION.SDK_INT >= 30)
      importantForContentCapture =
        android.view.View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
    settings.saveFormData = false
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = false
    settings.databaseEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
    settings.setGeolocationEnabled(false)
    settings.mediaPlaybackRequiresUserGesture = true
    settings.defaultTextEncodingName = "UTF-8"
    // Both WebViews are local-only. No auth flow depends on WebView cookies (Firebase uses its
    // SDK).
    CookieManager.getInstance().setAcceptCookie(false)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
    webViewClient = client
    webChromeClient =
      object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
          request.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(
          origin: String,
          callback: GeolocationPermissions.Callback,
        ) {
          callback.invoke(origin, false, false)
        }

        override fun onShowFileChooser(
          view: WebView?,
          callback: android.webkit.ValueCallback<Array<Uri>>?,
          params: FileChooserParams?,
        ): Boolean {
          callback?.onReceiveValue(null)
          return true
        }

        override fun onConsoleMessage(message: ConsoleMessage) =
          true // report contents never enter Logcat

        override fun onJsAlert(
          view: WebView,
          url: String,
          message: String,
          result: JsResult,
        ): Boolean {
          result.cancel()
          return true
        }

        override fun onJsConfirm(
          view: WebView,
          url: String,
          message: String,
          result: JsResult,
        ): Boolean {
          result.cancel()
          return true
        }

        override fun onJsPrompt(
          view: WebView,
          url: String,
          message: String,
          defaultValue: String,
          result: JsPromptResult,
        ): Boolean {
          result.cancel()
          return true
        }
      }
    setDownloadListener { _, _, _, _, _ -> }
    overScrollMode = android.view.View.OVER_SCROLL_NEVER
    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
  }

/** Full documents own their gestures; mini documents leave vertical drags to the report feed. */
internal class DocumentRuntimeWebView(context: Context) : WebView(context) {
  var documentExpanded = false

  override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
    if (documentExpanded)
      parent?.requestDisallowInterceptTouchEvent(
        event.actionMasked != android.view.MotionEvent.ACTION_UP &&
          event.actionMasked != android.view.MotionEvent.ACTION_CANCEL
      )
    return super.dispatchTouchEvent(event)
  }
}
