package com.karandaev.fieldfolio.feature.models.legacy

import com.karandaev.fieldfolio.core.domain.model.detection.DetectResponse
import com.karandaev.fieldfolio.core.image.sourcefile.ImageSourceFile
import com.karandaev.fieldfolio.core.ui.message.UiMessage

/**
 * UI state for road crack detection.
 *
 * @param step Current detection flow step.
 * @param imageSource Selected or captured image source.
 * @param detectResponse Last successful detection response displayed on detection screen.
 * @param isLoading Whether detection is currently running.
 * @param message Optional UI message.
 */
data class DetectUiState(
  val step: DetectStep = DetectStep.ChooseSource,
  val imageSource: ImageSourceFile? = null,
  val detectResponse: DetectResponse? = null,
  val isLoading: Boolean = false,
  val message: UiMessage? = null,
) {

  /**
   * Returns whether current message should be displayed as an error.
   *
   * Kept as a derived property to avoid passing error state separately.
   */
  val isError: Boolean
    get() = message?.isError == true

  /** Returns whether detection can be started. */
  val canDetect: Boolean
    get() = imageSource != null && step == DetectStep.CropImage && !isLoading
}
