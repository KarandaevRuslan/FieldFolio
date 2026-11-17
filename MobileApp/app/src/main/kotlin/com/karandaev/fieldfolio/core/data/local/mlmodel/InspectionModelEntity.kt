package com.karandaev.fieldfolio.core.data.local.mlmodel

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType
import com.karandaev.fieldfolio.core.domain.model.mlmodel.InspectionModelSourceType

/** Room entity describing a local inspection ML model. */
@Entity(
  tableName = "inspection_models",
  indices =
    [Index(value = ["result_type"]), Index(value = ["source_type", "source_path"], unique = true)],
)
data class InspectionModelEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  @ColumnInfo(name = "result_type") val resultType: InspectionResultType,
  val name: String,
  @ColumnInfo(name = "source_type")
  val sourceType: InspectionModelSourceType = InspectionModelSourceType.ASSET,
  @ColumnInfo(name = "source_path") val sourcePath: String,
  @ColumnInfo(name = "input_width") val inputWidth: Int,
  @ColumnInfo(name = "input_height") val inputHeight: Int,
  val description: String? = null,
  @ColumnInfo(name = "is_quantized") val isQuantized: Boolean = false,
  @ColumnInfo(name = "is_selected") val isSelected: Boolean = false,
  @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long = System.currentTimeMillis(),
  @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long = createdAtMillis,
)
