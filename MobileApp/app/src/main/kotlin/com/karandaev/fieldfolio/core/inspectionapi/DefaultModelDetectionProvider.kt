package com.karandaev.fieldfolio.core.inspectionapi

import com.karandaev.fieldfolio.core.domain.model.detection.DetectResponse
import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType
import com.karandaev.fieldfolio.core.domain.model.mlmodel.InspectionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Default implementation of [ModelDetectionProvider].
 *
 * This provider does not use backend base URL. Instead, it stores a domain-specific detection model
 * and delegates inference to [DetectionModelRunner].
 *
 * @param detectionModelRunner Runs actual model inference.
 * @param initialDetectionModel Optional initial detection model.
 */
class DefaultModelDetectionProvider(
  private val detectionModelRunner: DetectionModelRunner,
  initialDetectionModel: InspectionModel? = null,
) : ModelDetectionProvider {

  override var detectionModel: InspectionModel? = initialDetectionModel
    private set

  init {
    initialDetectionModel?.validateDetectionModel()
  }

  /**
   * Updates detection model used for local/model-based detection.
   *
   * @throws IllegalArgumentException if [detectionModel] is not a DETECTION model.
   */
  override fun updateDetectionModel(detectionModel: InspectionModel) {
    detectionModel.validateDetectionModel()

    this.detectionModel = detectionModel
  }

  /** Clears configured detection model. */
  override fun clearDetectionModel() {
    this.detectionModel = null
  }

  /**
   * Detects road cracks on the provided image using the configured detection model.
   *
   * @throws IllegalArgumentException if [sourcePath] is blank.
   * @throws IllegalStateException if detection model is not configured.
   */
  override suspend fun detect(sourcePath: String): Result<DetectResponse> {
    return runCatching {
      require(sourcePath.isNotBlank()) { "Source path must not be blank." }

      val model = currentDetectionModel()

      withContext(Dispatchers.Default) {
        detectionModelRunner.detect(sourcePath = sourcePath, model = model)
      }
    }
  }

  private fun currentDetectionModel(): InspectionModel {
    return detectionModel
      ?: error("Detection model is not initialized. Set detection model before request.")
  }

  private fun InspectionModel.validateDetectionModel() {
    require(resultType == InspectionResultType.DETECTION) {
      "Inspection model must have resultType=${InspectionResultType.DETECTION}, but was $resultType."
    }
  }
}
