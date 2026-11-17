package com.karandaev.fieldfolio.feature.auth.state

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.karandaev.fieldfolio.core.security.password.AuthValidation
import com.karandaev.fieldfolio.core.security.password.PasswordStrengthCalculator
import com.karandaev.fieldfolio.core.ui.message.UiMessageDisplayMode
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Updates the user name input value. */
internal fun AuthViewModel.performSetUserName(value: String) {
  _state.update { state -> state.copy(userName = value) }
}

/** Updates the email input value. */
internal fun AuthViewModel.performSetEmail(value: String) {
  _state.update { state -> state.copy(email = value) }
}

/** Updates the password input value. */
internal fun AuthViewModel.performSetPassword(value: String) {
  _state.update { state ->
    state.copy(password = value, passwordStrength = PasswordStrengthCalculator.calculate(value))
  }
}

/** Updates the repeated password input value. */
internal fun AuthViewModel.performSetRepeatedPassword(value: String) {
  _state.update { state -> state.copy(repeatedPassword = value) }
}

/** Updates the original password input value used for reauthentication. */
internal fun AuthViewModel.performSetOriginalPassword(value: String) {
  _state.update { state -> state.copy(originalPassword = value) }
}

/** Updates delete account confirmation text. */
internal fun AuthViewModel.performSetDeleteAccountConfirmationText(value: String) {
  _state.update { state -> state.copy(deleteAccountConfirmationText = value) }
}

/** Clears the user name input value. */
internal fun AuthViewModel.performClearUserName() {
  _state.update { state -> state.copy(userName = "") }
}

/** Clears the email input value. */
internal fun AuthViewModel.performClearEmail() {
  _state.update { state -> state.copy(email = "") }
}

/** Clears password input values. */
internal fun AuthViewModel.performClearPassword() {
  _state.update { state -> state.copy(password = "", repeatedPassword = "", passwordStrength = 0) }
}

/** Clears the repeated password input value. */
internal fun AuthViewModel.performClearRepeatedPassword() {
  _state.update { state -> state.copy(repeatedPassword = "") }
}

/** Clears the original password input value used for reauthentication. */
internal fun AuthViewModel.performClearOriginalPassword() {
  _state.update { state -> state.copy(originalPassword = "") }
}

/** Clears delete account confirmation text. */
internal fun AuthViewModel.performClearDeleteAccountConfirmationText() {
  _state.update { state -> state.copy(deleteAccountConfirmationText = "") }
}

/** Clears user name, email and password input values. */
internal fun AuthViewModel.performClearCredentials() {
  clearReauthenticationRequest()

  _state.update { state ->
    state.copy(
      userName = "",
      email = "",
      password = "",
      repeatedPassword = "",
      deleteAccountConfirmationText = "",
      passwordStrength = 0,
      avatarImageSource = null,
    )
  }
}

internal fun AuthViewModel.performRegister(onSuccess: () -> Unit = {}) =
  viewModelScope.launch {
    runWithLoading(onFailure = { error -> showError(AuthErrorMapper.toMessage(error)) }) {
      val userName = _state.value.userName.trim()
      val email = AuthValidation.requireValidEmail(_state.value.email)
      val password = _state.value.password
      val repeatedPassword = _state.value.repeatedPassword
      val passwordStrength = _state.value.passwordStrength

      require(userName.isNotBlank()) { "Please enter your name." }

      AuthValidation.requireStrongRepeatedPassword(
        password = password,
        repeatedPassword = repeatedPassword,
        passwordStrength = passwordStrength,
      )

      authAccountManager.registerWithEmail(userName = userName, email = email, password = password)

      showInfo(
        text = "Verification email sent to $email. Please verify your email, then sign in.",
        displayMode = UiMessageDisplayMode.Toast,
      )

      clearPassword()
      clearUserName()
      clearProfileAvatarImage()
      onSuccess()
    }
  }

/**
 * Signs in a user with email and password.
 *
 * If the email is not verified, a new verification email is sent and the user is signed out.
 */
internal fun AuthViewModel.performLogin() =
  viewModelScope.launch {
    runWithLoading(onFailure = { error -> showError(AuthErrorMapper.toMessage(error)) }) {
      val email = AuthValidation.requireValidEmail(_state.value.email)
      val password = _state.value.password

      require(password.isNotBlank()) { "Please enter your password." }

      val isEmailVerified = authAccountManager.loginWithEmail(email = email, password = password)

      if (!isEmailVerified) {
        showInfo(
          "Your email is not verified yet. We sent a verification email to $email. Please verify it and try again."
        )

        return@runWithLoading
      }

      showInfo(text = "Signed in successfully.", displayMode = UiMessageDisplayMode.Toast)

      clearGuestMode()
      clearUserName()
      clearPassword()
      clearProfileAvatarImage()
    }
  }

/** Sends a password reset email to the current email input value. */
internal fun AuthViewModel.performSendPasswordResetEmail(onSuccess: () -> Unit = {}) =
  viewModelScope.launch {
    runWithLoading(onFailure = { error -> showError(AuthErrorMapper.toMessage(error)) }) {
      val email =
        AuthValidation.requireValidEmail(
          value = _state.value.email,
          blankMessage = "Please enter your email address first.",
        )

      authAccountManager.sendPasswordResetEmail(email)

      showInfo(
        text = "Password reset email sent to $email. Please check your inbox.",
        displayMode = UiMessageDisplayMode.Toast,
      )
      onSuccess()
    }
  }

/**
 * Signs in the user with Google using Credential Manager and Firebase Auth.
 *
 * @param context Android context required by Credential Manager.
 */
internal fun AuthViewModel.performSignInWithGoogle(context: Context) =
  viewModelScope.launch {
    runWithLoading(onFailure = { error -> showError(AuthErrorMapper.toMessage(error)) }) {
      authAccountManager.signInWithGoogle(context)
      showInfo(
        text = "Signed in with Google successfully.",
        displayMode = UiMessageDisplayMode.Toast,
      )

      clearGuestMode()
      clearCredentials()
    }
  }
