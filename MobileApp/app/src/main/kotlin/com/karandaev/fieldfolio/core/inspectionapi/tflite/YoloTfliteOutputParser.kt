package com.karandaev.fieldfolio.core.inspectionapi.tflite

import com.karandaev.fieldfolio.core.domain.model.detection.Detection
import com.karandaev.fieldfolio.core.image.preprocessing.LetterboxTransform

private const val DefaultConfidenceThreshold = 0.25f
private const val DefaultIouThreshold = 0.45f

/**
 * Parses YOLO-like TFLite output into domain detections.
 *
 * Supported output layouts:
 * - `[1, candidateCount, 4 + classCount]`
 * - `[1, 4 + classCount, candidateCount]`
 *
 * Box format is expected to be normalized:
 * - `cx`
 * - `cy`
 * - `width`
 * - `height`
 *
 * Class values are expected to start after the first 4 box values.
 *
 * @param classLabels Labels used to map class indexes to domain class names.
 * @param confidenceThreshold Minimum class confidence required to keep a detection.
 * @param iouThreshold IoU threshold used by non-maximum suppression.
 */
class YoloTfliteOutputParser(
  private val classLabels: List<String>,
  private val confidenceThreshold: Float = DefaultConfidenceThreshold,
  private val iouThreshold: Float = DefaultIouThreshold,
) {

  init {
    require(classLabels.isNotEmpty()) { "Class labels must not be empty." }

    require(confidenceThreshold in 0f..1f) { "Confidence threshold must be between 0 and 1." }

    require(iouThreshold in 0f..1f) { "IoU threshold must be between 0 and 1." }
  }

  /**
   * Converts raw TFLite output tensor into filtered domain detections.
   *
   * The method:
   * 1. Converts supported YOLO output layout into a flat list of candidates.
   * 2. Converts every candidate into [Detection].
   * 3. Drops candidates below [confidenceThreshold].
   * 4. Maps letterboxed model coordinates back to original image coordinates.
   * 5. Applies class-wise non-maximum suppression.
   *
   * @param output Raw TFLite output tensor.
   * @param transform Letterbox transform used during image preprocessing.
   * @return Sorted list of detections by confidence in descending order.
   */
  fun parse(output: Array<Array<FloatArray>>, transform: LetterboxTransform): List<Detection> {
    val expectedFeatureCount = YoloBoxValuesCount + classLabels.size

    val detections =
      output.toYoloCandidates(expectedFeatureCount = expectedFeatureCount).mapNotNull {
        candidate: YoloCandidate ->
        candidate.toDetectionOrNull(
          classLabels = classLabels,
          confidenceThreshold = confidenceThreshold,
          transform = transform,
        )
      }

    return detections.nonMaxSuppression(iouThreshold = iouThreshold)
  }
}
