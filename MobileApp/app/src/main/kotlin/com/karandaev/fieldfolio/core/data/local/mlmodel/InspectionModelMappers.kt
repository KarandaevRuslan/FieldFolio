package com.karandaev.fieldfolio.core.data.local.mlmodel

import com.karandaev.fieldfolio.core.domain.model.mlmodel.InspectionModel

// -----------------------------------------------------------------------------
// Model mappings: local -> domain
// -----------------------------------------------------------------------------

/** Converts a local model entity to a domain model. */
fun InspectionModelEntity.toDomain(): InspectionModel {
  return InspectionModel(
    id = id,
    resultType = resultType,
    name = name,
    sourceType = sourceType,
    sourcePath = sourcePath,
    inputWidth = inputWidth,
    inputHeight = inputHeight,
    description = description,
    isQuantized = isQuantized,
    isSelected = isSelected,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
  )
}

// -----------------------------------------------------------------------------
// Model mappings: domain -> local
// -----------------------------------------------------------------------------

/** Converts a domain model to a local model entity. */
fun InspectionModel.toEntity(): InspectionModelEntity {
  return InspectionModelEntity(
    id = id,
    resultType = resultType,
    name = name,
    sourceType = sourceType,
    sourcePath = sourcePath,
    inputWidth = inputWidth,
    inputHeight = inputHeight,
    description = description,
    isQuantized = isQuantized,
    isSelected = isSelected,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
  )
}
