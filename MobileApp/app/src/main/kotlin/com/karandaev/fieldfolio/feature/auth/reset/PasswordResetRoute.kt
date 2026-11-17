package com.karandaev.fieldfolio.feature.auth.reset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karandaev.fieldfolio.feature.auth.state.AuthViewModel

/** Logical wrapper for the password reset screen. */
@Composable
fun PasswordResetRoute(
  authViewModel: AuthViewModel,
  onLoginClick: () -> Unit,
  onPasswordResetSuccess: () -> Unit,
) {
  val state by authViewModel.state.collectAsStateWithLifecycle()

  PasswordResetScreen(
    state = state,
    onEmailChange = authViewModel::setEmail,
    onEmailClear = authViewModel::clearEmail,
    onResetClick = { authViewModel.sendPasswordResetEmail(onSuccess = onPasswordResetSuccess) },
    onLoginClick = onLoginClick,
    onContinueAsGuestClick = authViewModel::continueAsGuest,
    onClearMessageClick = authViewModel::clearMessage,
  )
}
