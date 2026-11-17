package com.karandaev.fieldfolio.feature.auth.reset.contents

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.feature.auth.reset.components.PasswordResetFormList
import com.karandaev.fieldfolio.feature.auth.state.model.AuthUiState

@Composable
internal fun PasswordResetSinglePaneContent(
  state: AuthUiState,
  onEmailChange: (String) -> Unit,
  onEmailClear: () -> Unit,
  onResetClick: () -> Unit,
  onLoginClick: () -> Unit,
  onContinueAsGuestClick: () -> Unit,
  onClearMessageClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  PasswordResetFormList(
    state = state,
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    onEmailChange = onEmailChange,
    onEmailClear = onEmailClear,
    onResetClick = onResetClick,
    onLoginClick = onLoginClick,
    onContinueAsGuestClick = onContinueAsGuestClick,
    onClearMessageClick = onClearMessageClick,
    modifier = modifier,
  )
}
