package com.karandaev.fieldfolio.core.data.local.report.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/** Room entity for reports stored in the local database. */
@Entity(tableName = "inspection_reports")
@JsonClass(generateAdapter = true)
data class InspectionReportEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val content: String,
  val lastUpdatedAtMillis: Long,
  val latitude: Double,
  val longitude: Double,
)
