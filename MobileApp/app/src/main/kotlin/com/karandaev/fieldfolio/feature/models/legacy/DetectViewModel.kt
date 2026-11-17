package com.karandaev.fieldfolio.feature.models.legacy

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.karandaev.fieldfolio.core.image.crop.ImageCropper
import com.karandaev.fieldfolio.core.image.crop.NormalizedCropBox
import com.karandaev.fieldfolio.core.image.sourcefile.ImageSourceFileProvider
import com.karandaev.fieldfolio.core.inspectionapi.InspectionProvider
import com.karandaev.fieldfolio.core.ui.state.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel that manages image selection, crop state and infrastructure damage detection.
 *
 * @param inspectionProvider Provider used to run remote detection requests.
 * @param imageSourceFileProvider Provider used to copy external image URIs into app-local files.
 * @param imageCropper Cropper used to prepare selected images before detection.
 */
class DetectViewModel(
  internal val inspectionProvider: InspectionProvider,
  internal val imageSourceFileProvider: ImageSourceFileProvider,
  internal val imageCropper: ImageCropper,
) : ViewModel() {

  internal val _state = MutableStateFlow(DetectUiState())

  /** Current immutable detection UI state. */
  val state: StateFlow<DetectUiState> = _state.asStateFlow()

  internal val _completedResult = MutableStateFlow<DetectCompletedResult?>(null)

  /**
   * Last successfully completed detection result.
   *
   * This value is used by create/edit route to copy detection result into form state. It is
   * intentionally separated from [state], because [state] is screen UI state.
   */
  val completedResult: StateFlow<DetectCompletedResult?> = _completedResult.asStateFlow()

  internal var detectJob: Job? = null

  internal var nextRefreshId = 0L
  internal var nextMessageRefreshId = 0L

  /** Delegates setGalleryImage to the lifecycle-owned detection operation. */
  fun setGalleryImage(context: Context, uri: Uri?) = performSetGalleryImage(context, uri)

  /** Delegates setCapturedPhoto to the lifecycle-owned detection operation. */
  fun setCapturedPhoto(context: Context, uri: Uri?) = performSetCapturedPhoto(context, uri)

  /** Delegates onCameraCaptureCancelled to the lifecycle-owned detection operation. */
  fun onCameraCaptureCancelled() = performOnCameraCaptureCancelled()

  /** Delegates onCameraPermissionDenied to the lifecycle-owned detection operation. */
  fun onCameraPermissionDenied() = performOnCameraPermissionDenied()

  /** Delegates setCropBox to the lifecycle-owned detection operation. */
  fun setCropBox(cropBox: NormalizedCropBox?) = performSetCropBox(cropBox)

  /** Delegates resetCrop to the lifecycle-owned detection operation. */
  fun resetCrop() = performResetCrop()

  /** Delegates clearSelectedImage to the lifecycle-owned detection operation. */
  fun clearSelectedImage() = performClearSelectedImage()

  /** Delegates clearMessage to the lifecycle-owned detection operation. */
  fun clearMessage() = performClearMessage()

  /** Delegates resetForNewDetection to the lifecycle-owned detection operation. */
  fun resetForNewDetection() = performResetForNewDetection()

  /** Delegates consumeCompletedResult to the lifecycle-owned detection operation. */
  fun consumeCompletedResult(refreshId: Long) = performConsumeCompletedResult(refreshId)

  /** Delegates detectSelectedImage to the lifecycle-owned detection operation. */
  fun detectSelectedImage(context: Context) = performDetectSelectedImage(context)

  companion object {

    /**
     * Creates [DetectViewModel] factory.
     *
     * @param inspectionProvider Provider used to run remote detection requests.
     * @param imageSourceFileProvider Provider used to copy selected images into app-local files.
     * @param imageCropper Cropper used to prepare selected image before detection.
     */
    fun factory(
      inspectionProvider: InspectionProvider,
      imageSourceFileProvider: ImageSourceFileProvider,
      imageCropper: ImageCropper,
    ): ViewModelProvider.Factory {
      return viewModelFactory {
        DetectViewModel(
          inspectionProvider = inspectionProvider,
          imageSourceFileProvider = imageSourceFileProvider,
          imageCropper = imageCropper,
        )
      }
    }
  }
}
