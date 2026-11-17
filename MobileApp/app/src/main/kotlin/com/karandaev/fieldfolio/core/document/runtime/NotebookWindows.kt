package com.karandaev.fieldfolio.core.document.runtime

import androidx.compose.runtime.*

/** Expanded documents live above lazy cells, so scrolling/reflow cannot dismiss the window. */
class NotebookWindows {
  var expanded by mutableStateOf<String?>(null)

  fun clear() {
    expanded = null
  }
}

val LocalNotebookWindows = staticCompositionLocalOf<NotebookWindows?> { null }
