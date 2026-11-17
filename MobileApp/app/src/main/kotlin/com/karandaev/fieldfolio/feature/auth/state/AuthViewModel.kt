package com.karandaev.fieldfolio.feature.auth.state

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.karandaev.fieldfolio.core.auth.firebase.FirebaseAuthAccountManager
import com.karandaev.fieldfolio.core.auth.firebase.FirebaseGoogleAuthProvider
import com.karandaev.fieldfolio.core.domain.repository.PersistedAppStateRepository
import com.karandaev.fieldfolio.core.image.crop.ImageCropper
import com.karandaev.fieldfolio.core.image.crop.NormalizedCropBox
import com.karandaev.fieldfolio.core.image.sourcefile.ImageSourceFileProvider
import com.karandaev.fieldfolio.core.inspectionapi.InspectionProvider
import com.karandaev.fieldfolio.feature.auth.state.model.AuthDialogState
import com.karandaev.fieldfolio.feature.auth.state.model.AuthSession
import com.karandaev.fieldfolio.feature.auth.state.model.AuthUiState
import com.karandaev.fieldfolio.feature.auth.state.model.PendingSensitiveOperation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val DELETE_ACCOUNT_CONFIRMATION_WORD = "DELETE"
internal const val CurrentUserAvatarAutoRefreshMinIntervalMillis = 60_000L

/**
 * ViewModel that manages Firebase authentication and app access state.
 *
 * Authentication is backed by Firebase Auth. Google sign-in is delegated to
 * [FirebaseGoogleAuthProvider]. Guest access is persisted through [PersistedAppStateRepository].
 *
 * @param auth Shared Firebase authentication instance.
 * @param authAccountManager Manager for firebase auth actions.
 * @param persistedAppStateRepository Repository used to persist guest access state.
 */
class AuthViewModel(
  internal val auth: FirebaseAuth,
  internal val authAccountManager: FirebaseAuthAccountManager,
  internal val persistedAppStateRepository: PersistedAppStateRepository,
  internal val inspectionProvider: InspectionProvider,
  internal val imageSourceFileProvider: ImageSourceFileProvider,
  internal val imageCropper: ImageCropper,
) : ViewModel() {

  internal val _state = MutableStateFlow(AuthUiState(session = auth.toAuthSession()))

  /** Current immutable UI state. */
  val state: StateFlow<AuthUiState> = _state.asStateFlow()

  internal var nextRefreshId = 0L

  private val authListener =
    FirebaseAuth.AuthStateListener { firebaseAuth ->
      val newSession = firebaseAuth.toAuthSession()

      _state.update { state -> state.copy(session = newSession) }

      if (newSession is AuthSession.SignedIn) {
        refreshCurrentUserAvatarUrlSilently()
      }
    }

  internal var lastCurrentUserAvatarRefreshElapsedRealtime = 0L
  internal var currentUserAvatarRefreshJob: Job? = null

  internal var pendingSensitiveOperation: PendingSensitiveOperation? = null

  init {
    auth.addAuthStateListener(authListener)
    observeAppAccessState()
  }

  override fun onCleared() {
    auth.removeAuthStateListener(authListener)
    super.onCleared()
  }

  /** Persists explicit guest access and clears credential/dialog drafts after entering the app. */
  fun continueAsGuest() = performContinueAsGuest()

  fun clearGuestModeSilently() = performClearGuestModeSilently()

  /** Updates the one-shot authentication message. */
  fun clearMessage() = performClearMessage()

  fun setUserName(value: String) = performSetUserName(value)

  fun setEmail(value: String) = performSetEmail(value)

  fun setPassword(value: String) = performSetPassword(value)

  fun setRepeatedPassword(value: String) = performSetRepeatedPassword(value)

  fun setOriginalPassword(value: String) = performSetOriginalPassword(value)

  fun setDeleteAccountConfirmationText(value: String) =
    performSetDeleteAccountConfirmationText(value)

  fun clearUserName() = performClearUserName()

  fun clearEmail() = performClearEmail()

  fun clearPassword() = performClearPassword()

  fun clearRepeatedPassword() = performClearRepeatedPassword()

  fun clearOriginalPassword() = performClearOriginalPassword()

  fun clearDeleteAccountConfirmationText() = performClearDeleteAccountConfirmationText()

  /** Clears credential drafts, including password strength and confirmation state. */
  fun clearCredentials() = performClearCredentials()

  /**
   * Validates credentials and registers through the owner scope; invokes the callback only on
   * success.
   */
  fun register(onSuccess: () -> Unit = {}) = performRegister(onSuccess)

  /**
   * Validates the credential draft and updates the authenticated session through the owner scope.
   */
  fun login() = performLogin()

  /** Validates the email draft and reports reset delivery through the shared message channel. */
  fun sendPasswordResetEmail(onSuccess: () -> Unit = {}) = performSendPasswordResetEmail(onSuccess)

  /** Runs the platform credential flow and exchanges its token for the application session. */
  fun signInWithGoogle(context: Context) = performSignInWithGoogle(context)

  /** Signs out and clears local account state without silently enabling guest access. */
  fun logout() = performLogout()

  fun setProfileAvatarImage(context: Context, uri: Uri?) =
    performSetProfileAvatarImage(context, uri)

  fun setProfileAvatarCropBox(context: Context, cropBox: NormalizedCropBox?) =
    performSetProfileAvatarCropBox(context, cropBox)

  fun setSilentlyProfileAvatarCropBox(cropBox: NormalizedCropBox?) =
    performSetSilentlyProfileAvatarCropBox(cropBox)

  fun clearProfileAvatarImage() = performClearProfileAvatarImage()

  fun updateCurrentUserName(onSuccess: () -> Unit = {}) = performUpdateCurrentUserName(onSuccess)

  /**
   * Validates a new password and preserves a pending retry if recent authentication is required.
   */
  fun changePassword() = performChangePassword()

  /**
   * Requires explicit confirmation and handles recent-login recovery before deleting the account.
   */
  fun deleteAccount() = performDeleteAccount()

  /** Reauthenticates and retries only the pending sensitive account operation. */
  fun reauthenticateCurrentUser() = performReauthenticateCurrentUser()

  /** Uploads the current crop draft and refreshes profile state only after a successful update. */
  fun updateCurrentUserAvatar(context: Context, onSuccess: () -> Unit = {}) =
    performUpdateCurrentUserAvatar(context, onSuccess)

  fun refreshCurrentUserAvatarUrl() = performRefreshCurrentUserAvatarUrl()

  fun refreshCurrentUserAvatarUrlIfStale() = performRefreshCurrentUserAvatarUrlIfStale()

  fun resetCurrentUserAvatar(onSuccess: () -> Unit = {}) = performResetCurrentUserAvatar(onSuccess)

  fun setDialogState(dialogState: AuthDialogState) = performSetDialogState(dialogState)

  fun dismissChangeUserNameDialog() = performDismissChangeUserNameDialog()

  fun dismissChangePasswordDialog() = performDismissChangePasswordDialog()

  fun dismissChangeAvatarDialog() = performDismissChangeAvatarDialog()

  fun dismissDeleteAccountDialog() = performDismissDeleteAccountDialog()

  fun clearDialogState() = performClearDialogState()

  fun clearReauthenticationRequest() = performClearReauthenticationRequest()

  fun cancelSensitiveOperation() = performCancelSensitiveOperation()

  fun showChangeUserNameDialog() = performShowChangeUserNameDialog()

  fun showChangePasswordDialog() = performShowChangePasswordDialog()

  fun showChangeAvatarDialog() = performShowChangeAvatarDialog()

  fun showDeleteAccountDialog() = performShowDeleteAccountDialog()

  companion object
}
