package com.karandaev.fieldfolio.core.inspectionapi

import com.karandaev.fieldfolio.core.domain.model.detection.DetectResponse
import com.karandaev.fieldfolio.core.domain.model.mlmodel.InspectionModel

/** Runs detection using the provided domain inspection model. */
interface DetectionModelRunner {

  /**
   * Detects objects on the provided image using the provided model.
   *
   * @param sourcePath Absolute or relative path to the local source image file.
   * @param model Domain inspection model used for inference.
   */
  suspend fun detect(sourcePath: String, model: InspectionModel): DetectResponse
}
