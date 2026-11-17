package com.karandaev.fieldfolio.feature.settings.state

import androidx.lifecycle.viewModelScope
import com.karandaev.fieldfolio.core.domain.model.AppSettings
import com.karandaev.fieldfolio.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Updates the selected theme mode. */
internal fun SettingsViewModel.performSetThemeMode(themeMode: ThemeMode) {
  viewModelScope.launch { settingsRepository.setThemeMode(themeMode) }
}

/** Updates the selected language code. */
internal fun SettingsViewModel.performSetLanguageCode(languageCode: String) {
  viewModelScope.launch { settingsRepository.setLanguageCode(languageCode) }
}

/** Updates inspection backend server base URL. */
internal fun SettingsViewModel.performSetServerBaseUrl(serverBaseUrl: String) {
  val normalizedBaseUrl =
    normalizeValidServerBaseUrlOrNull(
      serverBaseUrl = serverBaseUrl,
      setInvalidAvailabilityState = ::setSavedServerAvailabilityState,
    ) ?: return

  saveServerBaseUrlAndCheck(serverBaseUrl = normalizedBaseUrl)
}

/** Saves current server base URL draft from the edit dialog. */
internal fun SettingsViewModel.performSaveDraftServerBaseUrl() {
  val dialogState = runtimeUiState.value.dialogState

  if (dialogState !is SettingsDialogState.ChangeServerBaseUrl) {
    return
  }

  val draftServerBaseUrl =
    normalizeValidServerBaseUrlOrNull(
      serverBaseUrl = dialogState.draftServerBaseUrl,
      setInvalidAvailabilityState = ::setDraftServerAvailabilityState,
    ) ?: return

  saveServerBaseUrlAndCheck(serverBaseUrl = draftServerBaseUrl)
}

/** Clears inspection backend server base URL. */
internal fun SettingsViewModel.performClearServerBaseUrl() {
  viewModelScope.launch {
    settingsRepository.setServerBaseUrl(AppSettings.DEFAULT_SERVER_BASE_URL)

    clearAppliedServerBaseUrl(availabilityState = ServerAvailabilityState.Unknown)

    clearDialogState()
  }
}

internal fun SettingsViewModel.ensureDefaultSettings() {
  viewModelScope.launch { settingsRepository.ensureDefaults() }
}

internal fun SettingsViewModel.observeServerBaseUrl() {
  viewModelScope.launch {
    settings
      .map { appSettings -> appSettings.serverBaseUrl }
      .distinctUntilChanged()
      .collect { serverBaseUrl ->
        val normalizedBaseUrl = normalizeServerBaseUrl(serverBaseUrl)

        applyServerBaseUrl(normalizedBaseUrl)

        if (normalizedBaseUrl.isNotBlank() && !hasCheckedInitialNonBlankServerBaseUrl) {
          hasCheckedInitialNonBlankServerBaseUrl = true

          checkSavedServerBaseUrl(serverBaseUrl = normalizedBaseUrl)
        }
      }
  }
}

internal fun SettingsViewModel.applyServerBaseUrl(serverBaseUrl: String) {
  val normalizedBaseUrl = normalizeServerBaseUrl(serverBaseUrl)

  if (normalizedBaseUrl.isBlank()) {
    clearAppliedServerBaseUrl(availabilityState = ServerAvailabilityState.Unknown)
    return
  }

  if (!inspectionProvider.isValidBaseUrl(normalizedBaseUrl)) {
    clearAppliedServerBaseUrl(availabilityState = ServerAvailabilityState.Unavailable)
    return
  }

  resetSavedAvailabilityForNewAppliedBaseUrl(serverBaseUrl = normalizedBaseUrl)

  runCatching {
      inspectionProvider.updateBaseUrl(normalizedBaseUrl)
      appliedServerBaseUrl = normalizedBaseUrl
    }
    .onFailure {
      clearAppliedServerBaseUrl(availabilityState = ServerAvailabilityState.Unavailable)
    }
}

/** Saves server base URL to settings and starts availability check for the saved value. */
internal fun SettingsViewModel.saveServerBaseUrlAndCheck(serverBaseUrl: String) {
  viewModelScope.launch {
    settingsRepository.setServerBaseUrl(serverBaseUrl)
    clearDialogState()

    checkSavedServerBaseUrl(serverBaseUrl = serverBaseUrl)
  }
}

/**
 * Resets saved availability state when a new server base URL is applied.
 *
 * Does not reset state if this exact URL is already being checked.
 */
internal fun SettingsViewModel.resetSavedAvailabilityForNewAppliedBaseUrl(serverBaseUrl: String) {
  val isNewAppliedBaseUrl = appliedServerBaseUrl != serverBaseUrl

  if (isNewAppliedBaseUrl && savedServerAvailabilityCheck.url != serverBaseUrl) {
    cancelSavedServerAvailabilityCheck()
    setSavedServerAvailabilityState(ServerAvailabilityState.Unknown)
  }
}

/** Clears currently applied backend base URL and updates saved availability state. */
internal fun SettingsViewModel.clearAppliedServerBaseUrl(
  availabilityState: ServerAvailabilityState
) {
  appliedServerBaseUrl = null
  cancelSavedServerAvailabilityCheck()
  inspectionProvider.clearBaseUrl()
  setSavedServerAvailabilityState(availabilityState)
}
