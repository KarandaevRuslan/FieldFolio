package com.karandaev.fieldfolio.core.data.local.report.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.karandaev.fieldfolio.core.data.local.report.entity.DetectionEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.InspectionReportEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.InspectionResultEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.SegmentationEntity
import com.squareup.moshi.JsonClass

/**
 * Room relation model containing one inspection result with its typed payload.
 *
 * DETECTION results use [detections]. SEGMENTATION results use [segmentations].
 */
@JsonClass(generateAdapter = true)
data class InspectionResultWithPayload(
  @Embedded val result: InspectionResultEntity,
  @Relation(parentColumn = "id", entityColumn = "inspectionResultId")
  val detections: List<DetectionEntity>,
  @Relation(parentColumn = "id", entityColumn = "inspectionResultId")
  val segmentations: List<SegmentationEntity>,
)

/** Room relation model containing an inspection report with all analysis results. */
@JsonClass(generateAdapter = true)
data class InspectionReportWithResults(
  @Embedded val report: InspectionReportEntity,
  @Relation(
    entity = InspectionResultEntity::class,
    parentColumn = "id",
    entityColumn = "inspectionReportId",
  )
  val results: List<InspectionResultWithPayload>,
)
