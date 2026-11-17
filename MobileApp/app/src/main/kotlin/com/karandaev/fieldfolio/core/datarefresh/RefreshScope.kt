package com.karandaev.fieldfolio.core.datarefresh

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex

/** An independently refreshable UI surface (report, editor preview, or nested dialog). */
class RefreshScope {
  private data class Request(val key: Any, val run: suspend () -> Unit)

  private val registrations = androidx.compose.runtime.mutableStateMapOf<Any, Request>()
  val hasRefreshableNeeds: Boolean
    get() = registrations.isNotEmpty()

  private val mutex = Mutex()
  var refreshing by mutableStateOf(false)
    private set

  /** Register only while a resource is actually part of this surface. */
  fun register(token: Any, key: Any, refresh: suspend () -> Unit) {
    registrations[token] = Request(key, refresh)
  }

  fun unregister(token: Any) {
    registrations.remove(token)
  }

  suspend fun refresh() {
    if (!mutex.tryLock()) return
    try {
      refreshing = true
      // One refresh per resource key, even if ten visible widgets use it.
      val requests = registrations.values.distinctBy { it.key }.toList()
      coroutineScope { requests.map { async { it.run() } }.forEach { it.await() } }
    } finally {
      refreshing = false
      mutex.unlock()
    }
  }
}

val LocalRefreshScope = compositionLocalOf<RefreshScope?> { null }

@Composable fun rememberRefreshScope(): RefreshScope = remember { RefreshScope() }

/** A registration is not a request: fast Lazy list scrolling only changes membership. */
@Composable
fun RegisterRefreshNeed(key: Any?, refresh: suspend () -> Unit) {
  val area = LocalRefreshScope.current
  val latest = androidx.compose.runtime.rememberUpdatedState(refresh)
  DisposableEffect(area, key) {
    val token = Any()
    if (key != null) area?.register(token, key) { latest.value() }
    onDispose { area?.unregister(token) }
  }
}
