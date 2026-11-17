package com.karandaev.fieldfolio.feature.models.legacy

import com.karandaev.fieldfolio.core.ui.message.UiMessage
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive

/** Switches UI state to loading and clears previous message. */
internal fun DetectViewModel.showLoading() {
  _state.update { state -> state.copy(isLoading = true, message = null) }
}

/**
 * Shows non-error UI message.
 *
 * @param message Message text.
 */
internal fun DetectViewModel.showInfo(message: String) {
  showMessage(message = createInfoMessage(message))
}

/**
 * Shows error UI message.
 *
 * @param message Error message text.
 */
internal fun DetectViewModel.showError(message: String) {
  showMessage(message = createErrorMessage(message))
}

/**
 * Shows throwable as mapped error UI message.
 *
 * @param error Error to map.
 */
internal fun DetectViewModel.showError(error: Throwable) {
  showError(message = DetectErrorMapper.toMessage(error))
}

/**
 * Shows UI message.
 *
 * @param message Message to display.
 */
internal fun DetectViewModel.showMessage(message: UiMessage) {
  _state.update { state -> state.copy(message = message) }
}

/** Creates informational UI message with fresh refresh id. */
internal fun DetectViewModel.createInfoMessage(text: String): UiMessage {
  return UiMessage.info(text = text, refreshId = nextMessageRefreshId())
}

/** Creates error UI message with fresh refresh id. */
internal fun DetectViewModel.createErrorMessage(text: String): UiMessage {
  return UiMessage.error(text = text, refreshId = nextMessageRefreshId())
}

/** Returns next monotonically increasing message refresh id. */
internal fun DetectViewModel.nextMessageRefreshId(): Long {
  nextMessageRefreshId += 1L
  return nextMessageRefreshId
}

/** Returns whether detection coroutine is currently active. */
internal fun DetectViewModel.isDetectionActive(): Boolean {
  return detectJob?.isActive == true
}

/** Returns next monotonically increasing refresh id. */
internal fun DetectViewModel.nextRefreshId(): Long {
  nextRefreshId += 1L
  return nextRefreshId
}
