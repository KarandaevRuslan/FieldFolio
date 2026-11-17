package com.karandaev.fieldfolio.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karandaev.fieldfolio.BuildConfig
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.library.rootIds
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.feature.auth.state.AuthViewModel
import com.karandaev.fieldfolio.feature.auth.state.model.currentProfile
import com.karandaev.fieldfolio.feature.settings.dialogs.SettingsResetDataDialogHost
import com.karandaev.fieldfolio.feature.settings.dialogs.SettingsServerBaseUrlDialogHost
import com.karandaev.fieldfolio.feature.settings.reset.rememberResetNotesToDefaultsAction
import com.karandaev.fieldfolio.feature.settings.state.RESET_DATA_CONFIRMATION_WORD
import com.karandaev.fieldfolio.feature.settings.state.ServerAvailabilityState
import com.karandaev.fieldfolio.feature.settings.state.SettingsDialogState
import com.karandaev.fieldfolio.feature.settings.state.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
  settingsViewModel: SettingsViewModel,
  authViewModel: AuthViewModel,
  inspectionReports: List<InspectionReport>,
  onProfileClick: () -> Unit,
  onSignInClick: () -> Unit,
) {
  val app = rememberAppServices()

  val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
  val settingsRuntimeUiState by settingsViewModel.runtimeUiState.collectAsStateWithLifecycle()
  val authState by authViewModel.state.collectAsStateWithLifecycle()

  val transfers =
    com.karandaev.fieldfolio.feature.library.rememberLibraryTransfers(app.reportLibrary)
  val transferScope = androidx.compose.runtime.rememberCoroutineScope()
  val isExporting = transfers.isExporting
  val isImporting = transfers.isImporting
  var isResetting by rememberSession { mutableStateOf(false) }

  val exportReportsSuccessMessage = stringResource(R.string.settings_data_export_reports_success)
  val importReportsSuccessMessage = stringResource(R.string.settings_data_import_reports_success)

  LaunchedEffect(Unit) { settingsViewModel.checkSavedServerBaseUrlIfStale() }

  LaunchedEffect(
    authState.currentProfile?.uid,
    settingsRuntimeUiState.savedServerAvailabilityState,
  ) {
    if (settingsRuntimeUiState.savedServerAvailabilityState == ServerAvailabilityState.Available) {
      authViewModel.refreshCurrentUserAvatarUrlIfStale()
    }
  }

  val resetDataDialogState = settingsRuntimeUiState.dialogState as? SettingsDialogState.ResetData
  val serverBaseUrlDialogState =
    settingsRuntimeUiState.dialogState as? SettingsDialogState.ChangeServerBaseUrl

  val resetNotesToDefaults =
    rememberResetNotesToDefaultsAction(
      resetNotesToDefaults = settingsViewModel::resetInspectionReportsToDefaults,
      onResettingChange = { resetting -> isResetting = resetting },
    )

  SettingsResetDataDialogHost(
    showDialog = resetDataDialogState != null,
    confirmationText = resetDataDialogState?.confirmationText.orEmpty(),
    requiredConfirmationWord = RESET_DATA_CONFIRMATION_WORD,
    isResetting = isResetting,
    onConfirmationTextChange = settingsViewModel::setResetDataConfirmationText,
    onConfirmationTextClear = settingsViewModel::clearResetDataConfirmationText,
    onDismiss = settingsViewModel::clearDialogState,
    onConfirm = resetNotesToDefaults,
  )

  SettingsServerBaseUrlDialogHost(
    showDialog = serverBaseUrlDialogState != null,
    serverBaseUrl = serverBaseUrlDialogState?.draftServerBaseUrl.orEmpty(),
    availabilityState =
      serverBaseUrlDialogState?.draftServerAvailabilityState
        ?: settingsRuntimeUiState.savedServerAvailabilityState,
    enabled = !isExporting && !isImporting && !isResetting,
    onServerBaseUrlChange = settingsViewModel::setDraftServerBaseUrl,
    onServerBaseUrlClear = settingsViewModel::clearDraftServerBaseUrl,
    onCheckServerClick = settingsViewModel::checkDraftServerBaseUrl,
    onDismiss = settingsViewModel::clearDialogState,
    onSave = settingsViewModel::saveDraftServerBaseUrl,
  )

  SettingsScreen(
    profile = authState.currentProfile,
    themeMode = settings.themeMode,
    languageCode = settings.languageCode,
    serverBaseUrl = settings.serverBaseUrl,
    serverAvailabilityState = settingsRuntimeUiState.savedServerAvailabilityState,
    appVersionName = BuildConfig.VERSION_NAME,
    isExporting = isExporting,
    isImporting = isImporting,
    isResetting = isResetting,
    onProfileClick = onProfileClick,
    onSignInClick = onSignInClick,
    onThemeModeSelect = settingsViewModel::setThemeMode,
    onLanguageCodeSelect = settingsViewModel::setLanguageCode,
    onServerBaseUrlClick = settingsViewModel::showChangeServerBaseUrlDialog,
    onCheckServerClick = settingsViewModel::checkSavedServerBaseUrl,
    onExportClick = { transferScope.launch { transfers.export(app.reportLibrary.rootIds()) } },
    onImportClick = { transfers.import(null) },
    onResetClick = settingsViewModel::showResetDataDialog,
  )
}
