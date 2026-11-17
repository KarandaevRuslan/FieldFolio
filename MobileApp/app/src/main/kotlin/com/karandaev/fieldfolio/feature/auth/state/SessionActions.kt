package com.karandaev.fieldfolio.feature.auth.state

import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.karandaev.fieldfolio.core.auth.firebase.toUserProfile
import com.karandaev.fieldfolio.core.domain.model.persisted.AppAccessState
import com.karandaev.fieldfolio.core.ui.message.UiMessageDisplayMode
import com.karandaev.fieldfolio.feature.auth.state.model.AuthSession
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Continues into the app without signing in.
 *
 * Guest access is persisted between app launches.
 */
internal fun AuthViewModel.performContinueAsGuest() {
  viewModelScope.launch {
    persistedAppStateRepository.setAppAccessState(createAppAccessState(isGuestModeEnabled = true))

    showInfo(text = "Continuing as guest.", displayMode = UiMessageDisplayMode.Toast)

    clearCredentials()
    clearDialogState()
  }
}

/**
 * Clears guest mode only in the current in-memory UI state.
 *
 * This is useful when the app must leave guest access immediately without changing the persisted
 * guest-mode choice.
 */
internal fun AuthViewModel.performClearGuestModeSilently() {
  _state.update { state -> state.copy(isGuestModeEnabled = false) }
}

/**
 * Signs out from Firebase.
 *
 * This does not enable guest mode automatically. If the user wants to continue without account
 * after logout, they should explicitly choose guest access again.
 */
internal fun AuthViewModel.performLogout() =
  viewModelScope.launch {
    runWithLoading(
      onFailure = { error ->
        forceLocalLogout()
        showError(
          text = "Signed out, but something went wrong. ${error.message.orEmpty()}".trim(),
          displayMode = UiMessageDisplayMode.Toast,
        )
      }
    ) {
      forceLocalLogout()

      showInfo(text = "You have been signed out.", displayMode = UiMessageDisplayMode.Toast)
    }
  }

internal fun AuthViewModel.observeAppAccessState() {
  viewModelScope.launch {
    persistedAppStateRepository.observeAppAccessState().collect { appAccessState ->
      _state.update { state -> state.copy(isGuestModeEnabled = appAccessState.isGuestModeEnabled) }
    }
  }
}

internal suspend fun AuthViewModel.clearGuestMode() {
  persistedAppStateRepository.setAppAccessState(createAppAccessState(isGuestModeEnabled = false))
}

internal fun AuthViewModel.createAppAccessState(isGuestModeEnabled: Boolean): AppAccessState {
  return AppAccessState(isGuestModeEnabled = isGuestModeEnabled)
}

internal suspend fun AuthViewModel.forceLocalLogout() {
  authAccountManager.signOut()
  clearGuestMode()
  clearUserName()
  clearPassword()
  clearProfileAvatarImage()
  clearDialogState()
}

/** Refreshes local auth session after profile changes that may not trigger auth listener. */
internal suspend fun AuthViewModel.refreshAuthSession() {
  authAccountManager.reloadCurrentUser()

  val newSession = auth.toAuthSession()

  _state.update { state -> state.copy(session = newSession) }

  if (newSession is AuthSession.SignedIn) {
    refreshCurrentUserAvatarUrlSilently()
  }
}

internal fun FirebaseAuth.toAuthSession(): AuthSession {
  val user = currentUser

  return if (user == null) {
    AuthSession.SignedOut
  } else {
    AuthSession.SignedIn(profile = user.toUserProfile())
  }
}
