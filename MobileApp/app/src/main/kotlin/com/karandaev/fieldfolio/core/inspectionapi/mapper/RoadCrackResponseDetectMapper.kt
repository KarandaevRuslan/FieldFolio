package com.karandaev.fieldfolio.core.inspectionapi.mapper

import com.karandaev.fieldfolio.core.domain.model.detection.DetectResponse
import com.karandaev.fieldfolio.core.domain.model.detection.Detection
import com.karandaev.fieldfolio.core.domain.model.detection.NormalizedBBox
import com.karandaev.fieldfolio.core.inspectionapi.dto.detect.DetectResponseDto
import com.karandaev.fieldfolio.core.inspectionapi.dto.detect.DetectionDto
import com.karandaev.fieldfolio.core.inspectionapi.dto.detect.NormalizedBBoxDto

/** Maps road crack detection remote DTOs to app models. */
fun DetectResponseDto.toDetectResponse(): DetectResponse {
  return DetectResponse(detections = detections.map { detection -> detection.toDetection() })
}

private fun DetectionDto.toDetection(): Detection {
  return Detection(clazz = clazz, confidence = confidence, bbox = bbox.toBBox())
}

private fun NormalizedBBoxDto.toBBox(): NormalizedBBox {
  return NormalizedBBox(xMin = xMin, yMin = yMin, xMax = xMax, yMax = yMax)
}
