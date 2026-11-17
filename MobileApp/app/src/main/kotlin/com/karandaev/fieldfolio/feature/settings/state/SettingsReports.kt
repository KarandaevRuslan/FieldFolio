package com.karandaev.fieldfolio.feature.settings.state

import com.karandaev.fieldfolio.core.domain.model.InspectionReport

/**
 * Imports reports by inserting them into the database.
 *
 * @return Ids of inserted reports in the same order as [inspectionReports].
 */
internal suspend fun SettingsViewModel.performImportInspectionReports(
  inspectionReports: List<InspectionReport>
): List<Long> {
  if (inspectionReports.isEmpty()) {
    return emptyList()
  }

  return inspectionReportRepository.insertAll(inspectionReports = inspectionReports)
}

/** Clears report data if the confirmation word is entered correctly. */
internal suspend fun SettingsViewModel.performResetInspectionReportsToDefaults() {
  val dialogState = runtimeUiState.value.dialogState
  check(dialogState is SettingsDialogState.ResetData && dialogState.isConfirmationValid) {
    "Reset requires an open dialog and the confirmation word"
  }

  // Await the real repository operation; callers must not report success before it completes.
  inspectionReportRepository.resetToDefaults()
  clearDialogState()
}
