package com.karandaev.fieldfolio.core.domain.model.inspection

/** Type of inspection analysis result. */
enum class InspectionResultType {

  /** Bounding-box object detection. */
  DETECTION,

  /** Pixel or polygon based segmentation. */
  SEGMENTATION;

  companion object {

    /** Parses result type from stored string. */
    fun from(value: String): InspectionResultType {
      return entries.firstOrNull { type -> type.name.equals(value, ignoreCase = true) } ?: DETECTION
    }

    /** Parses result type from stored string or returns null. */
    fun fromOrNull(value: String): InspectionResultType? {
      return entries.firstOrNull { type -> type.name.equals(value, ignoreCase = true) }
    }
  }
}
