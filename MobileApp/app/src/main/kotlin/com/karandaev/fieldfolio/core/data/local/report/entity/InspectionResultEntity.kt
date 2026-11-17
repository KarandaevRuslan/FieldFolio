package com.karandaev.fieldfolio.core.data.local.report.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType
import com.squareup.moshi.JsonClass

/**
 * Room entity for one analysis result attached to an inspection report.
 *
 * This is intentionally generic:
 * - DETECTION result can have bounding-box detections.
 * - SEGMENTATION result can get its own payload table later.
 */
@Entity(
  tableName = "inspection_results",
  foreignKeys =
    [
      ForeignKey(
        entity = InspectionReportEntity::class,
        parentColumns = ["id"],
        childColumns = ["inspectionReportId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices =
    [
      Index(value = ["inspectionReportId"]),
      Index(value = ["inspectionReportId", "sortOrder"]),
      Index(value = ["inspectionReportId", "type"]),
    ],
)
@JsonClass(generateAdapter = true)
data class InspectionResultEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val inspectionReportId: Long,
  val sortOrder: Int,
  val title: String,
  val description: String,
  @ColumnInfo(name = "type") val type: InspectionResultType,
)
