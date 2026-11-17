package com.karandaev.fieldfolio.core.data.local.converter

import androidx.room.TypeConverter
import com.karandaev.fieldfolio.core.domain.model.mlmodel.InspectionModelSourceType

/** Converts inspection model source type to a SQLite-friendly value. */
class InspectionModelSourceTypeConverter {

  @TypeConverter
  fun fromDatabaseValue(value: String): InspectionModelSourceType {
    return InspectionModelSourceType.from(value)
  }

  @TypeConverter
  fun toDatabaseValue(value: InspectionModelSourceType): String {
    return value.name
  }
}
