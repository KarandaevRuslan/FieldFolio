package com.karandaev.fieldfolio.feature.auth.components.cards

import androidx.compose.foundation.lazy.LazyListScope
import com.karandaev.fieldfolio.core.ui.components.notification.MiniUiMessageCard
import com.karandaev.fieldfolio.core.ui.message.shouldShowAsTextBlock
import com.karandaev.fieldfolio.feature.auth.state.model.AuthUiState

/**
 * Adds auth message item to the list when current state contains a message.
 *
 * @param state Current auth UI state.
 * @param onClearMessageClick Called when the user clears the message.
 */
internal fun LazyListScope.authMessageItem(state: AuthUiState, onClearMessageClick: () -> Unit) {
  val message = state.message ?: return

  if (!message.shouldShowAsTextBlock) {
    return
  }

  item {
    MiniUiMessageCard(
      message = message.text,
      isError = message.isError,
      onClearClick = onClearMessageClick,
    )
  }
}
