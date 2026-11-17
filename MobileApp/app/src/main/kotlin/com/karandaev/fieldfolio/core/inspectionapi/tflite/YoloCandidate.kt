package com.karandaev.fieldfolio.core.inspectionapi.tflite

import com.karandaev.fieldfolio.core.domain.model.detection.Detection
import com.karandaev.fieldfolio.core.domain.model.detection.NormalizedBBox
import com.karandaev.fieldfolio.core.image.preprocessing.LetterboxTransform

/**
 * Raw YOLO candidate decoded from model output.
 *
 * Expected value layout:
 * - index `0`: normalized center x
 * - index `1`: normalized center y
 * - index `2`: normalized width
 * - index `3`: normalized height
 * - index `4..`: class confidences
 */
internal class YoloCandidate(private val values: FloatArray) {

  /**
   * Converts this candidate to a domain [Detection], or returns `null` when confidence is too low
   * or box coordinates are invalid.
   *
   * @param classLabels Labels used to map best class index to class name.
   * @param confidenceThreshold Minimum confidence required to keep this candidate.
   * @param transform Letterbox transform used to map model coordinates back to original image.
   */
  fun toDetectionOrNull(
    classLabels: List<String>,
    confidenceThreshold: Float,
    transform: LetterboxTransform,
  ): Detection? {
    require(values.size >= YoloBoxValuesCount + classLabels.size) {
      "YOLO candidate has invalid size: ${values.size}."
    }

    val cx = values[0]
    val cy = values[1]
    val width = values[2]
    val height = values[3]

    val bestClassScore = findBestClassScore(classCount = classLabels.size) ?: return null

    if (bestClassScore.confidence < confidenceThreshold) {
      return null
    }

    val modelXMin = cx - width / 2f
    val modelYMin = cy - height / 2f
    val modelXMax = cx + width / 2f
    val modelYMax = cy + height / 2f

    val originalBox =
      transform.modelNormalizedToOriginalNormalizedBox(
        xMin = modelXMin,
        yMin = modelYMin,
        xMax = modelXMax,
        yMax = modelYMax,
      )

    if (originalBox.xMax <= originalBox.xMin || originalBox.yMax <= originalBox.yMin) {
      return null
    }

    return Detection(
      clazz = classLabels[bestClassScore.classIndex],
      confidence = bestClassScore.confidence.toDouble(),
      bbox =
        NormalizedBBox(
          xMin = originalBox.xMin.toDouble(),
          yMin = originalBox.yMin.toDouble(),
          xMax = originalBox.xMax.toDouble(),
          yMax = originalBox.yMax.toDouble(),
        ),
    )
  }

  /** Finds class index with the highest confidence. */
  private fun findBestClassScore(classCount: Int): YoloClassScore? {
    var bestClassIndex = -1
    var bestConfidence = 0f

    for (index in 0 until classCount) {
      val confidence = values[YoloBoxValuesCount + index]

      if (confidence > bestConfidence) {
        bestConfidence = confidence
        bestClassIndex = index
      }
    }

    if (bestClassIndex == -1) {
      return null
    }

    return YoloClassScore(classIndex = bestClassIndex, confidence = bestConfidence)
  }
}

/** Best class prediction for one YOLO candidate. */
private data class YoloClassScore(val classIndex: Int, val confidence: Float)
