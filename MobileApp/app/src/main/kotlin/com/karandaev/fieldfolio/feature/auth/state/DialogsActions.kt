package com.karandaev.fieldfolio.feature.auth.state

import com.karandaev.fieldfolio.feature.auth.state.model.AuthDialogState
import kotlinx.coroutines.flow.update

/** Shows the provided authentication dialog. */
internal fun AuthViewModel.performSetDialogState(dialogState: AuthDialogState) {
  _state.update { state -> state.copy(dialogState = dialogState) }
}

/** Dismisses display name dialog and clears temporary display name input. */
internal fun AuthViewModel.performDismissChangeUserNameDialog() {
  clearUserName()
  clearDialogState()
}

/** Dismisses change password dialog and clears temporary password inputs. */
internal fun AuthViewModel.performDismissChangePasswordDialog() {
  clearPassword()
  clearDialogState()
}

/** Dismisses avatar dialog and clears selected avatar image. */
internal fun AuthViewModel.performDismissChangeAvatarDialog() {
  clearProfileAvatarImage()
  clearDialogState()
}

/** Dismisses delete account dialog and clears confirmation text. */
internal fun AuthViewModel.performDismissDeleteAccountDialog() {
  clearDeleteAccountConfirmationText()
  clearDialogState()
}

/** Hides the currently shown authentication dialog. */
internal fun AuthViewModel.performClearDialogState() {
  _state.update { state -> state.copy(dialogState = AuthDialogState.None) }
}

/** Clears pending reauthentication request and entered original password. */
internal fun AuthViewModel.performClearReauthenticationRequest() {
  pendingSensitiveOperation = null

  _state.update { state -> state.copy(originalPassword = "", isReauthenticationRequired = false) }
}

/** Cancels pending sensitive operation and clears all temporary sensitive inputs. */
internal fun AuthViewModel.performCancelSensitiveOperation() {
  pendingSensitiveOperation = null

  _state.update { state ->
    state.copy(
      originalPassword = "",
      password = "",
      repeatedPassword = "",
      deleteAccountConfirmationText = "",
      passwordStrength = 0,
      isReauthenticationRequired = false,
      dialogState = AuthDialogState.None,
    )
  }
}

internal fun AuthViewModel.performShowChangeUserNameDialog() {
  setDialogState(AuthDialogState.ChangeUserName)
}

internal fun AuthViewModel.performShowChangePasswordDialog() {
  setDialogState(AuthDialogState.ChangePassword)
}

internal fun AuthViewModel.performShowChangeAvatarDialog() {
  setDialogState(AuthDialogState.ChangeAvatar)
}

internal fun AuthViewModel.performShowDeleteAccountDialog() {
  setDialogState(AuthDialogState.DeleteAccount)
}
