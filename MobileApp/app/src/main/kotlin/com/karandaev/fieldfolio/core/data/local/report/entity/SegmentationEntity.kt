package com.karandaev.fieldfolio.core.data.local.report.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * Room entity for one segmentation item inside a SEGMENTATION inspection result.
 *
 * The payload is intentionally flexible:
 * - maskPath can point to a stored raster mask file.
 * - polygonJson can store normalized polygon/multipolygon data as JSON.
 *
 * Domain compatibility layer does not expose this entity yet.
 */
@Entity(
  tableName = "inspection_result_segmentations",
  foreignKeys =
    [
      ForeignKey(
        entity = InspectionResultEntity::class,
        parentColumns = ["id"],
        childColumns = ["inspectionResultId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index(value = ["inspectionResultId"])],
)
@JsonClass(generateAdapter = true)
data class SegmentationEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val inspectionResultId: Long,
  val sortOrder: Int,
  val clazz: String,
  val confidence: Double?,
  val maskPath: String?,
  val polygonJson: String?,
)
