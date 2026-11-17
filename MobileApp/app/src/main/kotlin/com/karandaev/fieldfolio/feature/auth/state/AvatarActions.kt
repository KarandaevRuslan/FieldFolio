package com.karandaev.fieldfolio.feature.auth.state

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.karandaev.fieldfolio.core.image.crop.NormalizedCropBox
import com.karandaev.fieldfolio.core.image.sourcefile.ImageSourceFile
import com.karandaev.fieldfolio.core.ui.message.UiMessageDisplayMode
import com.karandaev.fieldfolio.feature.auth.state.model.AuthSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Updates selected profile avatar image source.
 *
 * The selected URI is copied into a local source file immediately.
 *
 * @param context Android context used to copy selected URI into cache.
 * @param uri URI of the selected avatar image, or null to clear selection.
 */
internal fun AuthViewModel.performSetProfileAvatarImage(context: Context, uri: Uri?) {
  _state.update { state ->
    if (uri == null) {
      state.copy(avatarImageSource = null)
    } else {
      state.copy(
        avatarImageSource = imageSourceFileProvider.createSourceFile(context = context, uri = uri)
      )
    }
  }
}

/**
 * Applies crop to the currently selected profile avatar image.
 *
 * Crop coordinates must be normalized relative to the original image.
 *
 * @param context Android context used to create cropped file in cache.
 * @param cropBox Normalized crop box, or null to reset crop.
 */
internal fun AuthViewModel.performSetProfileAvatarCropBox(
  context: Context,
  cropBox: NormalizedCropBox?,
) {
  val currentImageSource = _state.value.avatarImageSource ?: return

  val updatedImageSource =
    if (cropBox == null) {
      currentImageSource.copy(sourcePath = currentImageSource.originalSourcePath, cropBox = null)
    } else {
      imageCropper.crop(context = context, imageSource = currentImageSource, cropBox = cropBox)
    }

  _state.update { state -> state.copy(avatarImageSource = updatedImageSource) }
}

/**
 * Updates selected profile avatar crop box draft.
 *
 * The image is not cropped immediately because this function may be called very often while the
 * user moves the cropper with fingers.
 */
internal fun AuthViewModel.performSetSilentlyProfileAvatarCropBox(cropBox: NormalizedCropBox?) {
  _state.update { state ->
    val imageSource = state.avatarImageSource ?: return@update state

    state.copy(
      avatarImageSource =
        imageSource.copy(sourcePath = imageSource.originalSourcePath, cropBox = cropBox)
    )
  }
}

/** Clears selected profile avatar image source and crop. */
internal fun AuthViewModel.performClearProfileAvatarImage() {
  _state.update { state -> state.copy(avatarImageSource = null) }
}

/** Uploads selected profile avatar image through backend API and updates local profile photoUrl. */
internal fun AuthViewModel.performUpdateCurrentUserAvatar(
  context: Context,
  onSuccess: () -> Unit = {},
) =
  viewModelScope.launch {
    runWithLoading(onFailure = { error -> showError(AuthErrorMapper.toMessage(error)) }) {
      val imageSource = _state.value.avatarImageSource

      require(imageSource != null) { "Please select an avatar image first." }

      val uploadImageSource = prepareAvatarImageForUpload(imageSource, context = context)

      val avatarUrl =
        inspectionProvider.updateAvatar(sourcePath = uploadImageSource.sourcePath).getOrThrow()

      updateCurrentProfilePhotoUrlWithFallback(avatarUrl)

      dismissChangeAvatarDialog()

      showInfo(text = "Profile avatar updated.", displayMode = UiMessageDisplayMode.Toast)

      onSuccess()
    }
  }

/** Loads current profile avatar URL from backend API and updates local profile photoUrl. */
internal fun AuthViewModel.performRefreshCurrentUserAvatarUrl() {
  startCurrentUserAvatarRefresh(
    respectFreshness = false,
    showLoading = true,
    showErrors = true,
    countFailedAttempt = true,
  )
}

/**
 * Silently refreshes backend avatar URL after successful sign-in or profile session refresh.
 *
 * Failed startup attempts are not counted as fresh because backend base URL may not be ready yet.
 */
internal fun AuthViewModel.refreshCurrentUserAvatarUrlSilently() {
  startCurrentUserAvatarRefresh(
    respectFreshness = false,
    showLoading = false,
    showErrors = false,
    countFailedAttempt = false,
  )
}

/**
 * Silently refreshes backend avatar URL if previous refresh attempt is stale.
 *
 * This should be called from screens where backend base URL is already expected to be applied.
 */
internal fun AuthViewModel.performRefreshCurrentUserAvatarUrlIfStale() {
  startCurrentUserAvatarRefresh(
    respectFreshness = true,
    showLoading = false,
    showErrors = false,
    countFailedAttempt = true,
  )
}

/** Starts profile avatar refresh with common loading, throttling and duplicate-job protection. */
internal fun AuthViewModel.startCurrentUserAvatarRefresh(
  respectFreshness: Boolean,
  showLoading: Boolean,
  showErrors: Boolean,
  countFailedAttempt: Boolean,
): Job? {
  if (state.value.session !is AuthSession.SignedIn) {
    return null
  }

  if (respectFreshness && isCurrentUserAvatarRefreshFresh()) {
    return null
  }

  currentUserAvatarRefreshJob
    ?.takeIf { job -> job.isActive }
    ?.let { activeJob ->
      return activeJob
    }

  if (countFailedAttempt) {
    markCurrentUserAvatarRefreshAttempt()
  }

  val refreshJob =
    viewModelScope.launch {
      if (showLoading) {
        clearMessage()
        setLoading(true)
      }

      try {
        runCatching { loadCurrentUserAvatarUrl() }
          .onSuccess {
            if (!countFailedAttempt) {
              markCurrentUserAvatarRefreshAttempt()
            }
          }
          .onFailure { error ->
            if (showErrors) {
              showError(AuthErrorMapper.toMessage(error))
            }
          }
      } finally {
        currentUserAvatarRefreshJob = null

        if (showLoading) {
          setLoading(false)
        }
      }
    }

  currentUserAvatarRefreshJob = refreshJob

  return refreshJob
}

/** Loads backend avatar URL and applies it to current local signed-in profile. */
internal suspend fun AuthViewModel.loadCurrentUserAvatarUrl() {
  val avatarUrl = inspectionProvider.getAvatarUrl().getOrThrow()

  updateCurrentProfilePhotoUrlWithFallback(avatarUrl)
}

/** Resets current user's custom profile avatar through backend API. */
internal fun AuthViewModel.performResetCurrentUserAvatar(onSuccess: () -> Unit = {}) =
  viewModelScope.launch {
    runWithLoading(onFailure = { error -> showError(AuthErrorMapper.toMessage(error)) }) {
      val avatarUrl = inspectionProvider.resetAvatar().getOrThrow()

      updateCurrentProfilePhotoUrlWithFallback(avatarUrl)
      clearProfileAvatarImage()
      clearDialogState()

      showInfo(text = "Profile avatar reset.", displayMode = UiMessageDisplayMode.Toast)

      onSuccess()
    }
  }

/** Applies selected crop box before avatar upload. */
internal fun AuthViewModel.prepareAvatarImageForUpload(
  imageSource: ImageSourceFile,
  context: Context,
): ImageSourceFile {
  val cropBox = imageSource.cropBox ?: return imageSource

  return imageCropper.crop(
    context = context,
    imageSource = imageSource.copy(sourcePath = imageSource.originalSourcePath),
    cropBox = cropBox,
  )
}

/**
 * Applies backend profile avatar URL to the current signed-in UI profile.
 *
 * If backend has no custom avatar URL, original provider/Firebase photo URL is used as fallback.
 */
internal fun AuthViewModel.updateCurrentProfilePhotoUrlWithFallback(photoUrl: String?) {
  _state.update { state ->
    val session = state.session

    if (session is AuthSession.SignedIn) {
      state.copy(
        session =
          session.copy(
            profile = session.profile.copy(photoUrl = photoUrl ?: session.profile.originalPhotoUrl)
          )
      )
    } else {
      state
    }
  }
}

/**
 * Clears current photoUrl inside the signed-in UI profile.
 *
 * This is an explicit local clear and does not apply provider fallback. originalPhotoUrl is
 * preserved.
 */
internal fun AuthViewModel.clearCurrentProfilePhotoUrl() {
  _state.update { state ->
    val session = state.session

    if (session is AuthSession.SignedIn) {
      state.copy(session = session.copy(profile = session.profile.copy(photoUrl = null)))
    } else {
      state
    }
  }
}

/** Returns whether current user avatar refresh was attempted recently enough. */
internal fun AuthViewModel.isCurrentUserAvatarRefreshFresh(): Boolean {
  val lastRefreshTime = lastCurrentUserAvatarRefreshElapsedRealtime

  if (lastRefreshTime == 0L) {
    return false
  }

  return currentElapsedRealtime() - lastRefreshTime < CurrentUserAvatarAutoRefreshMinIntervalMillis
}

/** Marks current time as latest current user avatar refresh attempt. */
internal fun AuthViewModel.markCurrentUserAvatarRefreshAttempt() {
  lastCurrentUserAvatarRefreshElapsedRealtime = currentElapsedRealtime()
}

/** Returns monotonic elapsed realtime for refresh throttling. */
internal fun AuthViewModel.currentElapsedRealtime(): Long {
  return SystemClock.elapsedRealtime()
}
