package com.karandaev.fieldfolio.core.domain.model.mlmodel

import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType

/** Domain model describing a local ML model available for inspection. */
data class InspectionModel(
  val id: Long = 0L,
  val resultType: InspectionResultType,
  val name: String,
  val sourceType: InspectionModelSourceType,
  val sourcePath: String,
  val inputWidth: Int,
  val inputHeight: Int,
  val description: String? = null,
  val isQuantized: Boolean = false,
  val isSelected: Boolean = false,
  val createdAtMillis: Long = System.currentTimeMillis(),
  val updatedAtMillis: Long = createdAtMillis,
)
