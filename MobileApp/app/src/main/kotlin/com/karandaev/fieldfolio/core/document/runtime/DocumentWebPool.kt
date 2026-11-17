package com.karandaev.fieldfolio.core.document.runtime

import android.content.Context
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import java.util.UUID
import org.json.JSONTokener

/** Bounded, report-owned WebViews retain live form/Nexus state across rotation and expansion. */
class DocumentWebPool : ViewModel() {
  internal class Entry(
    val key: String,
    val context: MutableContextWrapper,
    val client: DocumentWebClient,
    val view: WebView,
  ) {
    var finished by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var leases = 0
    var host: FrameLayout? = null
    var source by mutableStateOf<String?>(null)
    var access: DocumentAccessContext? = null
    var dark: Boolean? = null
    var language: String? = null
    var snapshot: String? = null
    var lastSearchRequest: String? = null
    var dead = false
  }

  private val entries = linkedMapOf<String, Entry>()

  private data class Snapshot(val state: String, val searchRequest: String?)

  private val snapshots = linkedMapOf<String, Snapshot>()
  private val handler = Handler(Looper.getMainLooper())

  internal fun acquire(context: Context, report: String, node: String): Entry {
    val key = "$report\u0000$node"
    val old = entries.remove(key)
    if (old?.dead == true) destroy(old)
    val previous = old?.takeUnless { it.dead }
    val entry =
      previous
        ?: run {
          val wrapper = MutableContextWrapper(context.applicationContext)
          val scope = UUID.randomUUID().toString()
          lateinit var item: Entry
          val client =
            DocumentWebClient(
              context,
              documentHost(report, node, scope),
              scope,
              null,
              { item.finished = true },
              {
                item.failed = true
                item.dead = true
              },
            )
          val web = createDocumentWebView(wrapper, client)
          item = Entry(key, wrapper, client, web)
          snapshots.remove(key)?.let { saved ->
            item.snapshot = saved.state
            item.lastSearchRequest = saved.searchRequest
          }
          web.loadUrl(client.page)
          item
        }
    entries[key] = entry
    entry.leases++
    entry.context.baseContext = context
    entry.view.onResume()
    return entry
  }

  internal fun attach(entry: Entry, container: FrameLayout, active: Boolean) {
    if (entry.dead) return
    if (entry.view.parent !== container) {
      (entry.view.parent as? android.view.ViewGroup)?.removeView(entry.view)
      container.addView(entry.view, FrameLayout.LayoutParams(-1, -1))
    }
    entry.host = container
    container.visibility = if (active) android.view.View.VISIBLE else android.view.View.INVISIBLE
    entry.view.visibility = container.visibility
    entry.view.isEnabled = active
    if (active) entry.view.onResume()
    else {
      entry.view.clearFocus()
      entry.view.onPause()
    }
  }

  internal fun release(entry: Entry) {
    entry.leases = (entry.leases - 1).coerceAtLeast(0)
    if (entry.leases > 0) return
    if (entry.dead) {
      if (entries[entry.key] === entry) entries.remove(entry.key)
      destroy(entry)
      return
    }
    entry.context.baseContext = entry.context.applicationContext
    entry.view.clearFocus()
    entry.view.onPause()
    // An untrusted response is data only, is bounded, and never evaluated as code.
    entry.view.evaluateJavascript("window.PreviewApi?.exportState?.()") { raw ->
      if (!entry.dead && entry.leases == 0) {
        val state = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
        if (state != null && state.length <= 65536) {
          snapshots.remove(entry.key)
          snapshots[entry.key] = Snapshot(state, entry.lastSearchRequest)
          while (snapshots.size > 64) snapshots.remove(snapshots.keys.first())
        }
        trim()
      }
    }
    // A hostile/broken runtime cannot retain unlimited WebViews by withholding a callback.
    handler.postDelayed({ trim() }, 1000)
  }

  private fun trim() {
    val inactive = entries.values.filter { it.leases == 0 }
    inactive.dropLast(4).forEach { entry ->
      entries.remove(entry.key)
      destroy(entry)
    }
  }

  internal fun pause() {
    entries.values.filterNot { it.dead }.forEach { it.view.onPause() }
  }

  internal fun resume() {
    entries.values.filter { !it.dead && it.leases > 0 }.forEach { it.view.onResume() }
  }

  /** Password locking revokes outstanding resource capabilities before decrypted data is wiped. */
  fun clearReport(report: String) {
    entries.values
      .filter { (it.key.startsWith("$report\u0000") || it.key.startsWith("$report:")) }
      .toList()
      .forEach {
        entries.remove(it.key)
        destroy(it)
      }
    snapshots.keys.removeAll { (it.startsWith("$report\u0000") || it.startsWith("$report:")) }
  }

  private fun destroy(entry: Entry) {
    entry.dead = true
    entry.client.close()
    entry.source = null
    entry.access = null
    entry.snapshot = null
    (entry.view.parent as? android.view.ViewGroup)?.removeView(entry.view)
    entry.host = null
    runCatching {
      entry.view.stopLoading()
      entry.view.destroy()
    }
  }

  override fun onCleared() {
    handler.removeCallbacksAndMessages(null)
    entries.values.forEach(::destroy)
    entries.clear()
    snapshots.clear()
  }
}

val LocalDocumentWebPool = staticCompositionLocalOf<DocumentWebPool?> { null }
