package com.karandaev.fieldfolio.core.domain.model.mlmodel

/** Describes where a model file is stored. */
enum class InspectionModelSourceType {

  /** Model is bundled inside app assets. */
  ASSET,

  /** Model is stored as a regular local file. */
  FILE;

  companion object {

    /** Parses source type from stored string. */
    fun from(value: String): InspectionModelSourceType {
      return entries.firstOrNull { type -> type.name.equals(value, ignoreCase = true) } ?: ASSET
    }

    /** Parses source type from stored string or returns null. */
    fun fromOrNull(value: String): InspectionModelSourceType? {
      return entries.firstOrNull { type -> type.name.equals(value, ignoreCase = true) }
    }
  }
}
