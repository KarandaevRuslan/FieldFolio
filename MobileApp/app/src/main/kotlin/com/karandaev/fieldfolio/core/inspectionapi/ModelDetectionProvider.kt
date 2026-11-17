package com.karandaev.fieldfolio.core.inspectionapi

import com.karandaev.fieldfolio.core.domain.model.mlmodel.InspectionModel

/** Provides detection operations backed by a configured domain inspection model. */
interface ModelDetectionProvider : DetectionProvider {

  /** Currently configured detection model. */
  val detectionModel: InspectionModel?

  /**
   * Updates detection model used for local/model-based detection.
   *
   * @param detectionModel Domain inspection model for DETECTION results.
   */
  fun updateDetectionModel(detectionModel: InspectionModel)

  /** Clears configured detection model. */
  fun clearDetectionModel()
}
