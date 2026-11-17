package com.karandaev.fieldfolio.feature.auth.reset.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.ui.scroll.AppLazyColumn as LazyColumn
import com.karandaev.fieldfolio.feature.auth.components.cards.authMessageItem
import com.karandaev.fieldfolio.feature.auth.state.model.AuthUiState
import com.karandaev.fieldfolio.feature.shared.components.fields.MyEmailField

@Composable
internal fun PasswordResetFormList(
  state: AuthUiState,
  contentPadding: PaddingValues,
  onEmailChange: (String) -> Unit,
  onEmailClear: () -> Unit,
  onResetClick: () -> Unit,
  onLoginClick: () -> Unit,
  onContinueAsGuestClick: () -> Unit,
  onClearMessageClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isLoading = state.isLoading

  LazyColumn(
    modifier = modifier,
    contentPadding = contentPadding,
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    authMessageItem(state = state, onClearMessageClick = onClearMessageClick)

    item {
      MyEmailField(
        email = state.email,
        enabled = !isLoading,
        onEmailChange = onEmailChange,
        onEmailClear = onEmailClear,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    item {
      PasswordResetActions(
        isLoading = isLoading,
        onResetClick = onResetClick,
        onContinueAsGuestClick = onContinueAsGuestClick,
        onLoginClick = onLoginClick,
      )
    }
  }
}
