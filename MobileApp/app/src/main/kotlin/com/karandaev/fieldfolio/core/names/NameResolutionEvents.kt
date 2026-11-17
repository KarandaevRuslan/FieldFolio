package com.karandaev.fieldfolio.core.names

import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.receiveAsFlow

data class NameResolution(val requested: String, val resolved: String)

/** Successful mutations report allocations through one UI-independent event source. */
object NameResolutionEvents {
  private val pending = Channel<NameResolution>(64, BufferOverflow.DROP_OLDEST)
  val events = pending.receiveAsFlow()
  private val current = ThreadLocal<MutableList<NameResolution>?>()

  private class Journal(val values: MutableList<NameResolution>) :
    ThreadContextElement<MutableList<NameResolution>?>, AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<Journal>

    override fun updateThreadContext(context: CoroutineContext) =
      current.get().also { current.set(values) }

    override fun restoreThreadContext(
      context: CoroutineContext,
      oldState: MutableList<NameResolution>?,
    ) {
      current.set(oldState)
    }
  }

  fun record(requested: String, resolved: String) {
    if (requested == resolved) return
    val event = NameResolution(requested, resolved)
    current.get()?.add(event) ?: pending.trySend(event)
  }

  private fun publish(values: List<NameResolution>) {
    values.distinct().forEach { pending.trySend(it) }
  }

  fun <T> capture(block: () -> T): T {
    if (current.get() != null) return block()
    val values = mutableListOf<NameResolution>()
    current.set(values)
    try {
      val result = block()
      publish(values)
      return result
    } finally {
      current.remove()
    }
  }

  suspend fun <T> transaction(block: suspend () -> T): T {
    if (current.get() != null) return block()
    val values = mutableListOf<NameResolution>()
    val result = withContext(Journal(values)) { block() }
    publish(values)
    return result
  }
}
