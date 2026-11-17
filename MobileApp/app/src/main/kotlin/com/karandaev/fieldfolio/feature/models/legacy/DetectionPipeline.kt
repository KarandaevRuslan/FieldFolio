package com.karandaev.fieldfolio.feature.models.legacy

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.karandaev.fieldfolio.core.domain.model.detection.DetectResponse
import com.karandaev.fieldfolio.core.image.sourcefile.ImageSourceFile
import com.karandaev.fieldfolio.core.ui.error.ThrowableResultDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Starts asynchronous detection pipeline.
 *
 * Pipeline prepares upload image, runs backend request and applies result to UI state. Crop errors
 * and backend errors are handled by one decorator.
 *
 * @param context Android context used to create cropped image file when needed.
 * @param imageSource Selected image source.
 */
internal fun DetectViewModel.startDetection(context: Context, imageSource: ImageSourceFile) {
  showLoading()

  detectJob =
    viewModelScope.launch {
      try {
        val result =
          ThrowableResultDecorator.runSuspend {
            runDetectionPipeline(context = context, imageSource = imageSource)
          }

        if (!isActive) {
          return@launch
        }

        result
          .onSuccess { pipelineResult ->
            showDetectionSuccess(
              imageSource = pipelineResult.imageSource,
              data = pipelineResult.detectResponse,
            )
          }
          .onFailure { error -> showDetectionError(imageSource = imageSource, error = error) }
      } finally {
        detectJob = null
      }
    }
}

/**
 * Returns original or cropped image source that should be uploaded for detection.
 *
 * @param context Android context used by image cropper.
 * @param imageSource Selected image source.
 */
internal suspend fun DetectViewModel.prepareDetectionImageForUpload(
  context: Context,
  imageSource: ImageSourceFile,
): ImageSourceFile {
  val cropBox = imageSource.cropBox ?: return imageSource

  return withContext(Dispatchers.Default) {
    imageCropper.crop(
      context = context,
      imageSource = imageSource.copy(sourcePath = imageSource.originalSourcePath),
      cropBox = cropBox,
    )
  }
}

/**
 * Runs full detection pipeline.
 *
 * The pipeline includes optional image crop and backend detection request. Any thrown error is
 * handled by [ThrowableResultDecorator] in [startDetection].
 *
 * @param context Android context used by cropper.
 * @param imageSource Selected image source.
 */
internal suspend fun DetectViewModel.runDetectionPipeline(
  context: Context,
  imageSource: ImageSourceFile,
): DetectionPipelineResult {
  val uploadImageSource =
    prepareDetectionImageForUpload(context = context, imageSource = imageSource)

  val detectResponse =
    inspectionProvider.detect(sourcePath = uploadImageSource.sourcePath).getOrThrow()

  return DetectionPipelineResult(imageSource = uploadImageSource, detectResponse = detectResponse)
}

/**
 * Stores successful detection result and marks it as not delivered to route delegate yet.
 *
 * @param imageSource Image source used for detection.
 * @param data Detection response.
 */
internal fun DetectViewModel.showDetectionSuccess(
  imageSource: ImageSourceFile,
  data: DetectResponse,
) {
  val refreshId = nextRefreshId()

  _completedResult.value =
    DetectCompletedResult(refreshId = refreshId, imageSource = imageSource, detectResponse = data)

  _state.update { state ->
    state.copy(
      step = DetectStep.CropImage,
      imageSource = imageSource,
      detectResponse = data,
      isLoading = false,
      message = null,
    )
  }
}

/**
 * Stores failed detection result as user-friendly error message.
 *
 * @param imageSource Image source used for detection attempt.
 * @param error Detection failure.
 */
internal fun DetectViewModel.showDetectionError(imageSource: ImageSourceFile, error: Throwable) {
  _state.update { state ->
    state.copy(
      step = DetectStep.CropImage,
      imageSource = imageSource,
      detectResponse = null,
      isLoading = false,
      message = createErrorMessage(text = DetectErrorMapper.toMessage(error)),
    )
  }
}
