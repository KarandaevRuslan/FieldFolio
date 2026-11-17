package com.karandaev.fieldfolio.core.json.exportjson.reports

import com.karandaev.fieldfolio.core.domain.model.InspectionReport

const val INSPECTION_REPORTS_EXPORT_DIRECTORY_NAME = "geoinspect-export"
const val INSPECTION_REPORTS_EXPORT_FILE_NAME = "geoinspect-export.json"

private val UnsafeFileNameCharsRegex = Regex("[^\\p{L}\\p{N}._-]+")

fun InspectionReport.buildExportFileName(): String {
  return "${buildSafeExportName()}.json"
}

fun InspectionReport.buildExportDirectoryName(): String {
  return "geoinspect-${buildSafeExportName()}"
}

private fun InspectionReport.buildSafeExportName(): String {
  return title.replace(UnsafeFileNameCharsRegex, "-").trim('-').lowercase().ifBlank { "report-$id" }
}
