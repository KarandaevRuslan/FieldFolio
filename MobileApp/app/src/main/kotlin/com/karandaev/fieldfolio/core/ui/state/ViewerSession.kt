package com.karandaev.fieldfolio.core.ui.state

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel

/** Stable report/item keys survive lazy recycling and orientation-specific composition branches. */
val LocalViewerSession = staticCompositionLocalOf<String?> { null }

@Composable
fun ViewerSession(key: String, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalViewerSession provides key, content = content)
}

@Composable
fun <T> rememberViewerSession(
  key: String,
  vararg inputs: Any?,
  retainUntilOwnerCleared: Boolean = false,
  calculation: @DisallowComposableCalls () -> T,
): T {
  val scope = LocalViewerSession.current
  if (scope == null)
    return rememberSession(
      *inputs,
      retainUntilOwnerCleared = retainUntilOwnerCleared,
      calculation = calculation,
    )
  val owner: SessionStateViewModel = viewModel()
  return remember(owner, scope, key, *inputs) {
    owner.get("viewer:$scope:$key", inputs.toList(), calculation)
  }
}
