package com.karandaev.fieldfolio.core.inspectionapi.tflite

import com.karandaev.fieldfolio.core.domain.model.detection.Detection

/**
 * Applies class-wise non-maximum suppression.
 *
 * Detections are grouped by class. Inside each class group, detections are sorted by confidence in
 * descending order. A detection is kept only if its IoU with all already selected detections is
 * less than or equal to [iouThreshold].
 *
 * @param iouThreshold Maximum allowed IoU between two kept detections of the same class.
 * @return Detections sorted by confidence in descending order.
 */
internal fun List<Detection>.nonMaxSuppression(iouThreshold: Float): List<Detection> {
  if (isEmpty()) {
    return emptyList()
  }

  return groupBy { detection: Detection -> detection.clazz }
    .values
    .flatMap { classDetections: List<Detection> ->
      classDetections.suppressOverlaps(iouThreshold = iouThreshold)
    }
    .sortedByDescending { detection: Detection -> detection.confidence }
}

/** Suppresses overlapping detections inside one class group. */
private fun List<Detection>.suppressOverlaps(iouThreshold: Float): List<Detection> {
  val selectedDetections = mutableListOf<Detection>()

  sortedByDescending { detection: Detection -> detection.confidence }
    .forEach { candidate: Detection ->
      val shouldKeep =
        selectedDetections.none { existing: Detection ->
          existing.iou(candidate) > iouThreshold.toDouble()
        }

      if (shouldKeep) {
        selectedDetections += candidate
      }
    }

  return selectedDetections
}
