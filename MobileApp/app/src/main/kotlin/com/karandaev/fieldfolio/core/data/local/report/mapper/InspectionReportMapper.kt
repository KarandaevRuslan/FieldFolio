package com.karandaev.fieldfolio.core.data.local.report.mapper

import com.karandaev.fieldfolio.core.data.local.report.compatibility.InspectionResultCompatibility
import com.karandaev.fieldfolio.core.data.local.report.entity.DetectionEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.InspectionReportEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.InspectionResultEntity
import com.karandaev.fieldfolio.core.data.local.report.relation.InspectionReportWithResults
import com.karandaev.fieldfolio.core.data.local.report.relation.InspectionResultWithPayload
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.detection.DetectResponse
import com.karandaev.fieldfolio.core.domain.model.detection.Detection
import com.karandaev.fieldfolio.core.domain.model.detection.NormalizedBBox
import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import kotlin.collections.orEmpty

// -----------------------------------------------------------------------------
// Report mappings: local -> domain
// -----------------------------------------------------------------------------

/**
 * Converts a local report entity with multiple analysis results to a flat domain report model.
 *
 * Compatibility rule: domain detectResponse is built from the first DETECTION result only.
 * SEGMENTATION results are intentionally not exposed to domain yet.
 */
fun InspectionReportWithResults.toDomain(): InspectionReport {
  val detectResponse =
    results
      .firstDetectionResult()
      ?.detections
      ?.sortedBy { detection -> detection.sortOrder }
      .orEmpty()
      .toDetectResponseOrNull()

  return report.toDomain(detectResponse = detectResponse)
}

/** Converts a local report entity to a domain report model. */
fun InspectionReportEntity.toDomain(detectResponse: DetectResponse? = null): InspectionReport {
  return InspectionReport(
    id = id,
    title = title,
    content = content,
    lastUpdatedAtMillis = lastUpdatedAtMillis,
    point = MapPoint(latitude = latitude, longitude = longitude),
    detectResponse = detectResponse,
  )
}

// -----------------------------------------------------------------------------
// Report mappings: domain -> local
// -----------------------------------------------------------------------------

/** Converts a domain report model to a local report entity. */
fun InspectionReport.toEntity(): InspectionReportEntity {
  return InspectionReportEntity(
    id = id,
    title = title,
    content = content,
    lastUpdatedAtMillis = lastUpdatedAtMillis,
    latitude = latitude,
    longitude = longitude,
  )
}

/**
 * Converts a flat domain report model to the new local nested Room relation model.
 *
 * Compatibility rule: domain detectResponse becomes one DETECTION result. SEGMENTATION results
 * cannot be created from current domain yet.
 */
fun InspectionReport.toEntityWithResults(): InspectionReportWithResults {
  val detectionEntities = detectResponse.toDetectionEntities(inspectionResultId = 0)

  return InspectionReportWithResults(
    report = toEntity(),
    results =
      detectionEntities
        .takeIf { detections -> detections.isNotEmpty() }
        ?.let { detections ->
          listOf(
            InspectionResultWithPayload(
              result = createCompatibilityDetectionResult(inspectionReportId = id),
              detections = detections,
              segmentations = emptyList(),
            )
          )
        }
        .orEmpty(),
  )
}

// -----------------------------------------------------------------------------
// Detection mappings: local -> domain
// -----------------------------------------------------------------------------

/** Converts a local detection entity to a domain detection model. */
fun DetectionEntity.toDomain(): Detection {
  return Detection(
    clazz = clazz,
    confidence = confidence,
    bbox = NormalizedBBox(xMin = xMin, yMin = yMin, xMax = xMax, yMax = yMax),
  )
}

/** Converts local detection entities to a nullable domain detect response. */
private fun List<DetectionEntity>.toDetectResponseOrNull(): DetectResponse? {
  return takeIf { detections -> detections.isNotEmpty() }
    ?.let { detections ->
      DetectResponse(detections = detections.map { detection -> detection.toDomain() })
    }
}

// -----------------------------------------------------------------------------
// Detection mappings: domain -> local
// -----------------------------------------------------------------------------

/** Converts a domain detection response to local detection entities. */
fun DetectResponse?.toDetectionEntities(inspectionResultId: Long): List<DetectionEntity> {
  return this?.detections
    ?.mapIndexed { index, detection ->
      detection.toEntity(inspectionResultId = inspectionResultId, sortOrder = index)
    }
    .orEmpty()
}

/** Converts a domain detection model to a local detection entity. */
fun Detection.toEntity(inspectionResultId: Long, sortOrder: Int): DetectionEntity {
  return DetectionEntity(
    inspectionResultId = inspectionResultId,
    sortOrder = sortOrder,
    clazz = clazz,
    confidence = confidence,
    xMin = bbox.xMin,
    yMin = bbox.yMin,
    xMax = bbox.xMax,
    yMax = bbox.yMax,
  )
}

// -----------------------------------------------------------------------------
// Inspection result compatibility helpers
// -----------------------------------------------------------------------------

/** Returns the first DETECTION result used by the temporary flat domain model. */
private fun List<InspectionResultWithPayload>.firstDetectionResult(): InspectionResultWithPayload? {
  return filter { resultWithPayload ->
      resultWithPayload.result.type == InspectionResultType.DETECTION
    }
    .minWithOrNull(
      compareBy<InspectionResultWithPayload> { it.result.sortOrder }.thenBy { it.result.id }
    )
}

/** Creates the compatibility DETECTION result used by the current flat domain model. */
private fun createCompatibilityDetectionResult(inspectionReportId: Long): InspectionResultEntity {
  return InspectionResultEntity(
    inspectionReportId = inspectionReportId,
    sortOrder = 0,
    title = InspectionResultCompatibility.DetectionResultTitle,
    description = InspectionResultCompatibility.DetectionResultDescription,
    type = InspectionResultType.DETECTION,
  )
}
