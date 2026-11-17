package com.karandaev.fieldfolio.feature.settings.state

import androidx.lifecycle.viewModelScope
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import kotlinx.coroutines.launch

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

/** Resets reports to default seed data if the confirmation word is entered correctly. */
internal fun SettingsViewModel.performResetInspectionReportsToDefaults() {
  val dialogState = runtimeUiState.value.dialogState

  if (dialogState !is SettingsDialogState.ResetData) {
    return
  }

  if (!dialogState.isConfirmationValid) {
    return
  }

  viewModelScope.launch {
    inspectionReportRepository.resetToDefaults()
    clearDialogState()
  }
}
