package com.karandaev.fieldfolio.feature.models.legacy

import com.karandaev.fieldfolio.core.domain.model.detection.DetectResponse
import com.karandaev.fieldfolio.core.image.sourcefile.ImageSourceFile

/**
 * Stable completed detection result used for cross-route handoff.
 *
 * This state is separate from [DetectUiState] because UI state can be reset or mutated by cropper
 * callbacks, while create/edit form still needs the accepted result.
 *
 * @param refreshId Monotonic result id.
 * @param imageSource Image source used for successful detection.
 * @param detectResponse Backend detection response.
 */
data class DetectCompletedResult(
  val refreshId: Long,
  val imageSource: ImageSourceFile,
  val detectResponse: DetectResponse,
)
