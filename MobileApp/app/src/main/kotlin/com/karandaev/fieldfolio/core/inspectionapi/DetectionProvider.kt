package com.karandaev.fieldfolio.core.inspectionapi

import com.karandaev.fieldfolio.core.domain.model.detection.DetectResponse

/** Provides road crack detection operations. */
interface DetectionProvider {

  /**
   * Detects road cracks on the provided image.
   *
   * @param sourcePath Absolute or relative path to the local source image file.
   */
  suspend fun detect(sourcePath: String): Result<DetectResponse>
}
