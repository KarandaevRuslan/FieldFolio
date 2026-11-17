package com.karandaev.fieldfolio.core.data.local.converter

import androidx.room.TypeConverter
import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType

/** Converts inspection result type to a SQLite-friendly value. */
class InspectionResultTypeConverter {

  @TypeConverter
  fun fromDatabaseValue(value: String): InspectionResultType {
    return InspectionResultType.from(value)
  }

  @TypeConverter
  fun toDatabaseValue(value: InspectionResultType): String {
    return value.name
  }
}
