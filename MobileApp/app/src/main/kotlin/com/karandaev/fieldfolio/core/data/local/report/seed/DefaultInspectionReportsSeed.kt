package com.karandaev.fieldfolio.core.data.local.report.seed

import com.karandaev.fieldfolio.core.data.local.report.entity.DetectionEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.InspectionReportEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.InspectionResultEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.SegmentationEntity
import com.karandaev.fieldfolio.core.data.local.report.relation.InspectionReportWithResults
import com.karandaev.fieldfolio.core.data.local.report.relation.InspectionResultWithPayload
import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType

/** Provides initial inspection reports inserted when the database is created for the first time. */
object DefaultInspectionReportsSeed {

  /** Creates default inspection report entities. */
  fun create(): List<InspectionReportWithResults> {
    val now = System.currentTimeMillis()

    return listOf(
      createReport(
        title = "Road crack on Main Street",
        content = "Longitudinal asphalt crack detected near the right traffic lane.",
        lastUpdatedAtMillis = now - 3L * DayMillis,
        latitude = 51.5055,
        longitude = -0.0754,
        results =
          listOf(
            createResult(
              sortOrder = 0,
              title = "Primary crack detection",
              description = "Bounding-box result for visible asphalt cracking.",
              type = InspectionResultType.DETECTION,
              detections =
                listOf(
                  createDetection(
                    sortOrder = 0,
                    clazz = "longitudinal_crack",
                    confidence = 0.91,
                    xMin = 0.18,
                    yMin = 0.42,
                    xMax = 0.76,
                    yMax = 0.58,
                  )
                ),
            )
          ),
      ),
      createReport(
        title = "Pothole near bus stop",
        content = "Surface damage found close to the curb. Requires maintenance inspection.",
        lastUpdatedAtMillis = now - 2L * DayMillis,
        latitude = 51.5194,
        longitude = -0.1270,
        results =
          listOf(
            createResult(
              sortOrder = 0,
              title = "Pothole detection",
              description = "Detected pothole close to the curb.",
              type = InspectionResultType.DETECTION,
              detections =
                listOf(
                  createDetection(
                    sortOrder = 0,
                    clazz = "pothole",
                    confidence = 0.87,
                    xMin = 0.35,
                    yMin = 0.50,
                    xMax = 0.62,
                    yMax = 0.74,
                  )
                ),
            )
          ),
      ),
      createReport(
        title = "Multiple pavement defects",
        content = "Several damaged asphalt areas detected on the road segment.",
        lastUpdatedAtMillis = now - DayMillis,
        latitude = 51.5073,
        longitude = -0.1657,
        results =
          listOf(
            createResult(
              sortOrder = 0,
              title = "Defect detection",
              description = "Bounding-box result for visible pavement defects.",
              type = InspectionResultType.DETECTION,
              detections =
                listOf(
                  createDetection(
                    sortOrder = 0,
                    clazz = "transverse_crack",
                    confidence = 0.82,
                    xMin = 0.12,
                    yMin = 0.30,
                    xMax = 0.86,
                    yMax = 0.41,
                  ),
                  createDetection(
                    sortOrder = 1,
                    clazz = "alligator_crack",
                    confidence = 0.79,
                    xMin = 0.24,
                    yMin = 0.56,
                    xMax = 0.68,
                    yMax = 0.84,
                  ),
                ),
            ),
            createResult(
              sortOrder = 1,
              title = "Surface segmentation",
              description = "Segmentation result for damaged pavement regions.",
              type = InspectionResultType.SEGMENTATION,
              segmentations =
                listOf(
                  createSegmentation(
                    sortOrder = 0,
                    clazz = "damaged_asphalt",
                    confidence = 0.84,
                    maskPath = null,
                    polygonJson =
                      """
                  {
                    "type": "polygon",
                    "points": [
                      [0.22, 0.48],
                      [0.71, 0.50],
                      [0.76, 0.78],
                      [0.28, 0.82]
                    ]
                  }
                """
                        .trimIndent(),
                  )
                ),
            ),
          ),
      ),
      createReport(
        title = "Inspection point without result",
        content = "Manual report created for a road segment that still needs image-based analysis.",
        lastUpdatedAtMillis = now,
        latitude = 51.5033,
        longitude = -0.1196,
        results = emptyList(),
      ),
    )
  }

  private fun createReport(
    title: String,
    content: String,
    lastUpdatedAtMillis: Long,
    latitude: Double,
    longitude: Double,
    results: List<InspectionResultWithPayload>,
  ): InspectionReportWithResults {
    return InspectionReportWithResults(
      report =
        InspectionReportEntity(
          title = title,
          content = content,
          lastUpdatedAtMillis = lastUpdatedAtMillis,
          latitude = latitude,
          longitude = longitude,
        ),
      results = results,
    )
  }

  private fun createResult(
    sortOrder: Int,
    title: String,
    description: String,
    type: InspectionResultType,
    detections: List<DetectionEntity> = emptyList(),
    segmentations: List<SegmentationEntity> = emptyList(),
  ): InspectionResultWithPayload {
    return InspectionResultWithPayload(
      result =
        InspectionResultEntity(
          inspectionReportId = 0,
          sortOrder = sortOrder,
          title = title,
          description = description,
          type = type,
        ),
      detections = detections,
      segmentations = segmentations,
    )
  }

  private fun createDetection(
    sortOrder: Int,
    clazz: String,
    confidence: Double,
    xMin: Double,
    yMin: Double,
    xMax: Double,
    yMax: Double,
  ): DetectionEntity {
    return DetectionEntity(
      inspectionResultId = 0,
      sortOrder = sortOrder,
      clazz = clazz,
      confidence = confidence,
      xMin = xMin,
      yMin = yMin,
      xMax = xMax,
      yMax = yMax,
    )
  }

  private fun createSegmentation(
    sortOrder: Int,
    clazz: String,
    confidence: Double?,
    maskPath: String?,
    polygonJson: String?,
  ): SegmentationEntity {
    return SegmentationEntity(
      inspectionResultId = 0,
      sortOrder = sortOrder,
      clazz = clazz,
      confidence = confidence,
      maskPath = maskPath,
      polygonJson = polygonJson,
    )
  }

  private const val DayMillis = 24L * 60L * 60L * 1000L
}
