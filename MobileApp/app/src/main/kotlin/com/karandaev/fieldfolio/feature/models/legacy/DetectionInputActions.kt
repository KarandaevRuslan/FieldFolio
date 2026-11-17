package com.karandaev.fieldfolio.feature.models.legacy

import android.content.Context
import android.net.Uri
import com.karandaev.fieldfolio.core.image.crop.NormalizedCropBox
import com.karandaev.fieldfolio.core.image.sourcefile.ImageSourceFile
import com.karandaev.fieldfolio.core.ui.error.ThrowableResultDecorator
import kotlinx.coroutines.flow.update

/**
 * Selects image from gallery and moves detection flow directly to crop step.
 *
 * @param context Android context used to copy selected image into app-local storage.
 * @param uri Selected image URI, or null when gallery selection was cancelled.
 */
internal fun DetectViewModel.performSetGalleryImage(context: Context, uri: Uri?) {
  setImageFromUri(
    context = context,
    uri = uri,
    nullUriMessage = DetectUiMessages.GallerySelectionCancelled,
  )
}

/**
 * Stores captured camera photo and moves detection flow directly to crop step.
 *
 * @param context Android context used to copy captured image into app-local storage.
 * @param uri Captured photo URI, or null when camera capture was cancelled.
 */
internal fun DetectViewModel.performSetCapturedPhoto(context: Context, uri: Uri?) {
  setImageFromUri(
    context = context,
    uri = uri,
    nullUriMessage = DetectUiMessages.CameraCaptureCancelled,
  )
}

/** Shows an informational message when camera capture was cancelled. */
internal fun DetectViewModel.performOnCameraCaptureCancelled() {
  showInfo(DetectUiMessages.CameraCaptureCancelled)
}

/** Shows an error message when camera permission was denied. */
internal fun DetectViewModel.performOnCameraPermissionDenied() {
  showError(DetectUiMessages.CameraPermissionDenied)
}

internal fun DetectViewModel.performSetCropBox(cropBox: NormalizedCropBox?) {
  if (isDetectionActive()) {
    return
  }

  val imageSource = currentImageSource() ?: return

  if (imageSource.cropBox == cropBox) {
    return
  }

  _state.update { state ->
    state.copy(imageSource = imageSource.withCropBox(cropBox), detectResponse = null)
  }
}

/** Resets selected image crop and uses original image for detection. */
internal fun DetectViewModel.performResetCrop() {
  setCropBox(null)
}

/** Clears selected image, crop state, messages and previous detection result. */
internal fun DetectViewModel.performClearSelectedImage() {
  if (isDetectionActive()) {
    return
  }

  _state.value = DetectUiState()
}

/** Clears current UI message. */
internal fun DetectViewModel.performClearMessage() {
  _state.update { state -> state.copy(message = null) }
}

/**
 * Clears current detection flow state before opening detection screen again.
 *
 * This is needed because [DetectViewModel] is shared between create and detect routes. The
 * completed handoff result is also cleared so a new detection run cannot re-apply an older result
 * to the create/edit form.
 */
internal fun DetectViewModel.performResetForNewDetection() {
  detectJob?.cancel()
  detectJob = null

  _state.value = DetectUiState()
  _completedResult.value = null
}

/**
 * Clears completed detection result after it has been copied into another route state.
 *
 * The [refreshId] guard prevents clearing a newer result from an older effect.
 *
 * @param refreshId Completed result id that was consumed.
 */
internal fun DetectViewModel.performConsumeCompletedResult(refreshId: Long) {
  val currentResult = _completedResult.value

  if (currentResult?.refreshId != refreshId) {
    return
  }

  _completedResult.value = null
}

/**
 * Runs detection for the currently selected image.
 *
 * If a crop box is selected, image is cropped before inference.
 *
 * @param context Android context used to create cropped image file when needed.
 */
internal fun DetectViewModel.performDetectSelectedImage(context: Context) {
  if (isDetectionActive()) {
    return
  }

  val imageSource = currentImageSource()

  if (imageSource == null) {
    showError(DetectUiMessages.NoImageSelected)
    return
  }

  startDetection(context = context, imageSource = imageSource)
}

internal fun DetectViewModel.setImageFromUri(context: Context, uri: Uri?, nullUriMessage: String) {
  if (isDetectionActive()) {
    return
  }

  if (uri == null) {
    showInfo(nullUriMessage)
    return
  }

  ThrowableResultDecorator.run { createImageSource(context = context, uri = uri) }
    .onSuccess { imageSource ->
      showImageStep(imageSource = imageSource, step = DetectStep.CropImage)
    }
    .onFailure { error -> showError(error) }
}

/**
 * Copies external image URI into app-local image source file.
 *
 * @param context Android context used to copy image.
 * @param uri External image URI.
 */
internal fun DetectViewModel.createImageSource(context: Context, uri: Uri): ImageSourceFile {
  return imageSourceFileProvider.createSourceFile(context = context, uri = uri)
}

/**
 * Switches UI to image-related step with provided image source.
 *
 * @param imageSource Selected image source.
 * @param step Target detection flow step.
 */
internal fun DetectViewModel.showImageStep(imageSource: ImageSourceFile, step: DetectStep) {
  _state.value = DetectUiState(step = step, imageSource = imageSource)
}

/** Returns currently selected image source, if any. */
internal fun DetectViewModel.currentImageSource(): ImageSourceFile? {
  return _state.value.imageSource
}

/**
 * Returns image source with updated crop box and original source path restored.
 *
 * @param cropBox New crop box, or null to use original image.
 */
internal fun ImageSourceFile.withCropBox(cropBox: NormalizedCropBox?): ImageSourceFile {
  return copy(sourcePath = originalSourcePath, cropBox = cropBox)
}
