package com.karandaev.fieldfolio.core.ui.components.dialog

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.tr

@Composable
fun rememberUnsavedClose(dirty: Boolean, discard: () -> Unit): () -> Unit {
  var confirm by rememberSession { mutableStateOf(false) }
  val currentDiscard by rememberUpdatedState(discard)
  val currentDirty by rememberUpdatedState(dirty)
  if (confirm)
    SafeAlertDialog(
      { confirm = false },
      title = { Text(tr("unsaved")) },
      text = { Text(tr("discard_body")) },
      confirmButton = {
        TextButton({
          confirm = false
          currentDiscard()
        }) {
          Text(tr("discard"))
        }
      },
      dismissButton = { TextButton({ confirm = false }) { Text(tr("cancel")) } },
    )
  return { if (currentDirty) confirm = true else currentDiscard() }
}
