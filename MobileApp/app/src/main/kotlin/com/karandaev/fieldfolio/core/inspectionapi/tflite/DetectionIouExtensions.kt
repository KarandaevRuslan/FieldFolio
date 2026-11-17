package com.karandaev.fieldfolio.core.inspectionapi.tflite

import com.karandaev.fieldfolio.core.domain.model.detection.Detection
import kotlin.math.max
import kotlin.math.min

/**
 * Calculates intersection over union between two detections.
 *
 * IoU is calculated using normalized bounding boxes.
 *
 * @param other Detection to compare with.
 * @return IoU value from `0.0` to `1.0`.
 */
internal fun Detection.iou(other: Detection): Double {
  val intersectionXMin = max(bbox.xMin, other.bbox.xMin)
  val intersectionYMin = max(bbox.yMin, other.bbox.yMin)
  val intersectionXMax = min(bbox.xMax, other.bbox.xMax)
  val intersectionYMax = min(bbox.yMax, other.bbox.yMax)

  val intersectionWidth = max(0.0, intersectionXMax - intersectionXMin)

  val intersectionHeight = max(0.0, intersectionYMax - intersectionYMin)

  val intersectionArea = intersectionWidth * intersectionHeight

  val area = (bbox.xMax - bbox.xMin) * (bbox.yMax - bbox.yMin)
  val otherArea = (other.bbox.xMax - other.bbox.xMin) * (other.bbox.yMax - other.bbox.yMin)

  val unionArea = area + otherArea - intersectionArea

  if (unionArea <= 0.0) {
    return 0.0
  }

  return intersectionArea / unionArea
}
