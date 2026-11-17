package com.karandaev.fieldfolio.feature.models.legacy

import com.karandaev.fieldfolio.core.domain.model.detection.DetectResponse
import com.karandaev.fieldfolio.core.image.sourcefile.ImageSourceFile

/**
 * Successful detection pipeline result.
 *
 * @param imageSource Image source actually sent to backend.
 * @param detectResponse Backend detection response.
 */
internal data class DetectionPipelineResult(
  val imageSource: ImageSourceFile,
  val detectResponse: DetectResponse,
)
