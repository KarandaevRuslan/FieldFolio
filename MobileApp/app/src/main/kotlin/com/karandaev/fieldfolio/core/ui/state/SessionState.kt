package com.karandaev.fieldfolio.core.ui.state

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karandaev.fieldfolio.core.ui.lifecycle.findActivity
import java.util.UUID

/** Only opaque slot identifiers enter saved state. Form values remain in the route's ViewModel. */
class SessionStateViewModel : ViewModel() {
  private data class Entry(val inputs: List<Any?>, val value: Any?)

  private val entries = mutableMapOf<String, Entry>()

  @Suppress("UNCHECKED_CAST")
  fun <T> get(id: String, inputs: List<Any?>, create: () -> T): T {
    val old = entries[id]
    if (old != null && old.inputs == inputs) return old.value as T
    return create().also { entries[id] = Entry(inputs, it) }
  }

  fun clearViewer() {
    entries.keys.removeAll { it.startsWith("viewer:") }
  }

  fun release(id: String) {
    entries.remove(id)
  }

  override fun onCleared() {
    entries.clear()
  }
}

/** Configuration-safe remember, without serializing private drafts into a Bundle. */
@Composable
fun <T> rememberSession(
  vararg inputs: Any?,
  retainUntilOwnerCleared: Boolean = false,
  calculation: @DisallowComposableCalls () -> T,
): T {
  val id = rememberSaveable { UUID.randomUUID().toString() }
  val owner: SessionStateViewModel = viewModel()
  val activity = LocalContext.current.findActivity()
  val value = remember(owner, id, *inputs) { owner.get(id, inputs.toList(), calculation) }
  DisposableEffect(owner, id, activity) {
    onDispose {
      if (!retainUntilOwnerCleared && activity?.isChangingConfigurations != true) owner.release(id)
    }
  }
  return value
}
