package com.karandaev.fieldfolio.core.data.local.report.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/** Room entity for one bounding-box detection inside a DETECTION inspection result. */
@Entity(
  tableName = "inspection_result_detections",
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
data class DetectionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val inspectionResultId: Long,
  val sortOrder: Int,
  val clazz: String,
  val confidence: Double,
  val xMin: Double,
  val yMin: Double,
  val xMax: Double,
  val yMax: Double,
)
