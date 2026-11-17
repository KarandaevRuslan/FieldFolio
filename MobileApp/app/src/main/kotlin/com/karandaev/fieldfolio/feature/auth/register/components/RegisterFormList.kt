package com.karandaev.fieldfolio.feature.auth.register.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.ui.components.bars.PasswordStrengthBar
import com.karandaev.fieldfolio.core.ui.scroll.AppLazyColumn as LazyColumn
import com.karandaev.fieldfolio.feature.auth.components.cards.authMessageItem
import com.karandaev.fieldfolio.feature.auth.state.model.AuthUiState
import com.karandaev.fieldfolio.feature.shared.components.fields.MyEmailField
import com.karandaev.fieldfolio.feature.shared.components.fields.MyPasswordField
import com.karandaev.fieldfolio.feature.shared.components.fields.MyUserNameField

@Composable
internal fun RegisterFormList(
  state: AuthUiState,
  contentPadding: PaddingValues,
  onUserNameChange: (String) -> Unit,
  onUserNameClear: () -> Unit,
  onEmailChange: (String) -> Unit,
  onEmailClear: () -> Unit,
  onPasswordChange: (String) -> Unit,
  onPasswordClear: () -> Unit,
  onRepeatedPasswordChange: (String) -> Unit,
  onRepeatedPasswordClear: () -> Unit,
  onRegisterClick: () -> Unit,
  onGoogleSignInClick: () -> Unit,
  onContinueAsGuestClick: () -> Unit,
  onLoginClick: () -> Unit,
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
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MyUserNameField(
          userName = state.userName,
          enabled = !isLoading,
          onUserNameChange = onUserNameChange,
          onUserNameClear = onUserNameClear,
          modifier = Modifier.fillMaxWidth(),
        )

        MyEmailField(
          email = state.email,
          enabled = !isLoading,
          onEmailChange = onEmailChange,
          onEmailClear = onEmailClear,
          modifier = Modifier.fillMaxWidth(),
        )

        MyPasswordField(
          password = state.password,
          enabled = !isLoading,
          onPasswordChange = onPasswordChange,
          onPasswordClear = onPasswordClear,
          modifier = Modifier.fillMaxWidth(),
        )

        MyPasswordField(
          password = state.repeatedPassword,
          enabled = !isLoading,
          onPasswordChange = onRepeatedPasswordChange,
          onPasswordClear = onRepeatedPasswordClear,
          modifier = Modifier.fillMaxWidth(),
          label = stringResource(R.string.ui_repeat_password),
        )

        PasswordStrengthBar(strength = state.passwordStrength)
      }
    }

    item {
      RegisterActions(
        isLoading = isLoading,
        onRegisterClick = onRegisterClick,
        onGoogleSignInClick = onGoogleSignInClick,
        onContinueAsGuestClick = onContinueAsGuestClick,
        onLoginClick = onLoginClick,
      )
    }
  }
}
