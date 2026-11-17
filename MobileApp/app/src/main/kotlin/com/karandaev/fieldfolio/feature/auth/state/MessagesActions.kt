package com.karandaev.fieldfolio.feature.auth.state

import com.karandaev.fieldfolio.core.ui.message.UiMessage
import com.karandaev.fieldfolio.core.ui.message.UiMessageDisplayMode
import kotlinx.coroutines.flow.update

/**
 * Clears the current one-shot UI message.
 *
 * This does not change the current signed-in or signed-out session.
 */
internal fun AuthViewModel.performClearMessage() {
  _state.update { state -> state.copy(message = null) }
}

internal suspend fun AuthViewModel.runWithLoading(
  onFailure: suspend (Throwable) -> Unit,
  block: suspend () -> Unit,
) {
  clearMessage()
  setLoading(true)

  try {
    runCatching { block() }.onFailure { error -> onFailure(error) }
  } finally {
    setLoading(false)
  }
}

internal fun AuthViewModel.setLoading(isLoading: Boolean) {
  _state.update { state -> state.copy(isLoading = isLoading) }
}

internal fun AuthViewModel.showInfo(
  text: String,
  displayMode: UiMessageDisplayMode = UiMessageDisplayMode.TextBlock,
) {
  showMessage(text = text, isError = false, displayMode = displayMode)
}

internal fun AuthViewModel.showError(
  text: String,
  displayMode: UiMessageDisplayMode = UiMessageDisplayMode.TextBlock,
) {
  showMessage(text = text, isError = true, displayMode = displayMode)
}

internal fun AuthViewModel.showMessage(
  text: String,
  isError: Boolean,
  displayMode: UiMessageDisplayMode,
) {
  _state.update { state ->
    state.copy(
      message =
        UiMessage(
          text = text,
          isError = isError,
          displayMode = displayMode,
          refreshId = nextRefreshId(),
        )
    )
  }
}

internal fun AuthViewModel.nextRefreshId(): Long {
  nextRefreshId += 1L
  return nextRefreshId
}
