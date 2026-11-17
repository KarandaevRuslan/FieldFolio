package com.karandaev.fieldfolio.feature.auth.state

import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.karandaev.fieldfolio.core.security.password.AuthValidation
import com.karandaev.fieldfolio.core.ui.message.UiMessageDisplayMode
import com.karandaev.fieldfolio.feature.auth.state.model.PendingSensitiveOperation
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Updates the current signed-in user's display name. */
internal fun AuthViewModel.performUpdateCurrentUserName(onSuccess: () -> Unit = {}) =
  viewModelScope.launch {
    runWithLoading(onFailure = { error -> showError(AuthErrorMapper.toMessage(error)) }) {
      val userName = _state.value.userName.trim()

      require(userName.isNotBlank()) { "Please enter your name." }

      authAccountManager.updateCurrentUserName(userName)
      refreshAuthSession()

      showInfo(text = "Profile name updated.", displayMode = UiMessageDisplayMode.Toast)

      dismissChangeUserNameDialog()
      onSuccess()
    }
  }

/** Changes password for users signed in with email/password provider. */
internal fun AuthViewModel.performChangePassword() =
  viewModelScope.launch {
    runSensitiveOperation(pendingOperation = PendingSensitiveOperation.ChangePassword) {
      validateNewPassword()

      authAccountManager.changePassword(_state.value.password)

      showInfo(text = "Password updated.", displayMode = UiMessageDisplayMode.Toast)

      dismissChangePasswordDialog()
    }
  }

/** Deletes the current signed-in account. */
internal fun AuthViewModel.performDeleteAccount() =
  viewModelScope.launch {
    runSensitiveOperation(pendingOperation = PendingSensitiveOperation.DeleteAccount) {
      validateDeleteAccountConfirmation()

      authAccountManager.deleteAccount()

      clearGuestMode()
      dismissDeleteAccountDialog()
      clearCredentials()

      showInfo(text = "Account deleted.", displayMode = UiMessageDisplayMode.Toast)
    }
  }

/** Reauthenticates current user and retries pending sensitive operation. */
internal fun AuthViewModel.performReauthenticateCurrentUser() =
  viewModelScope.launch {
    runWithLoading(onFailure = { error -> showError(AuthErrorMapper.toMessage(error)) }) {
      val originalPassword = _state.value.originalPassword.trim()

      require(originalPassword.isNotBlank()) { "Please enter your current password." }

      authAccountManager.reauthenticateWithPassword(originalPassword)

      val operation = pendingSensitiveOperation ?: return@runWithLoading

      clearReauthenticationRequest()
      retrySensitiveOperation(operation)
    }
  }

internal suspend fun AuthViewModel.runSensitiveOperation(
  pendingOperation: PendingSensitiveOperation,
  block: suspend () -> Unit,
) {
  runWithLoading(
    onFailure = { error ->
      if (error.isRecentLoginRequired()) {
        requestReauthentication(pendingOperation)
      } else {
        showError(AuthErrorMapper.toMessage(error))
      }
    },
    block = block,
  )
}

internal fun AuthViewModel.requestReauthentication(pendingOperation: PendingSensitiveOperation) {
  pendingSensitiveOperation = pendingOperation

  _state.update { state -> state.copy(originalPassword = "", isReauthenticationRequired = true) }

  clearDialogState()
}

internal suspend fun AuthViewModel.retrySensitiveOperation(operation: PendingSensitiveOperation) {
  when (operation) {
    PendingSensitiveOperation.ChangePassword -> {
      validateNewPassword()

      authAccountManager.changePassword(_state.value.password)

      showInfo(text = "Password updated.", displayMode = UiMessageDisplayMode.Toast)

      dismissChangePasswordDialog()
    }

    PendingSensitiveOperation.DeleteAccount -> {
      validateDeleteAccountConfirmation()

      authAccountManager.deleteAccount()

      clearGuestMode()
      dismissDeleteAccountDialog()
      clearCredentials()

      showInfo(text = "Account deleted.", displayMode = UiMessageDisplayMode.Toast)
    }
  }
}

/** Checks whether delete account confirmation text matches required word. */
internal fun AuthViewModel.validateDeleteAccountConfirmation() {
  require(_state.value.deleteAccountConfirmationText.trim() == DELETE_ACCOUNT_CONFIRMATION_WORD) {
    "Please type $DELETE_ACCOUNT_CONFIRMATION_WORD to confirm account deletion."
  }
}

internal fun AuthViewModel.validateNewPassword() {
  AuthValidation.requireStrongRepeatedPassword(
    password = _state.value.password,
    repeatedPassword = _state.value.repeatedPassword,
    passwordStrength = _state.value.passwordStrength,
    passwordBlankMessage = "Please enter your new password.",
    repeatedPasswordBlankMessage = "Please repeat your new password.",
  )
}

internal fun Throwable.isRecentLoginRequired(): Boolean {
  var currentError: Throwable? = this

  while (currentError != null) {
    if (currentError is FirebaseAuthRecentLoginRequiredException) {
      return true
    }

    currentError = currentError.cause
  }

  return false
}
