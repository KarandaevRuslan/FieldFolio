package com.karandaev.fieldfolio.feature.settings.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karandaev.fieldfolio.core.domain.model.AppSettings
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.ThemeMode
import com.karandaev.fieldfolio.core.domain.repository.InspectionReportRepository
import com.karandaev.fieldfolio.core.domain.repository.SettingsRepository
import com.karandaev.fieldfolio.core.inspectionapi.InspectionProvider
import com.karandaev.fieldfolio.core.ui.state.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal const val SettingsSharingTimeoutMillis = 5_000L
internal const val SavedServerAvailabilityAutoCheckMinIntervalMillis = 60_000L

/**
 * ViewModel for app settings.
 *
 * Exposes persisted settings, runtime-only dialog state, and handles settings updates, note
 * imports, resetting notes to default seed data, analysis backend URL synchronization, and server
 * availability checks.
 */
class SettingsViewModel(
  internal val settingsRepository: SettingsRepository,
  internal val inspectionReportRepository: InspectionReportRepository,
  internal val inspectionProvider: InspectionProvider,
) : ViewModel() {

  internal val _runtimeUiState = MutableStateFlow(SettingsRuntimeUiState())

  /** Runtime-only settings UI state. */
  val runtimeUiState: StateFlow<SettingsRuntimeUiState> = _runtimeUiState.asStateFlow()

  internal var hasCheckedInitialNonBlankServerBaseUrl = false

  internal var lastSavedServerAvailabilityCheckElapsedRealtime = 0L

  internal val savedServerAvailabilityCheck = ServerAvailabilityCheck()
  internal val draftServerAvailabilityCheck = ServerAvailabilityCheck()

  internal var appliedServerBaseUrl: String? = null

  /** Current app settings. */
  val settings: StateFlow<AppSettings> =
    settingsRepository
      .observeSettings()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Companion.WhileSubscribed(SettingsSharingTimeoutMillis),
        initialValue = AppSettings(),
      )

  /** Currently selected theme mode. */
  val themeMode: StateFlow<ThemeMode> =
    settings
      .map { appSettings -> appSettings.themeMode }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Companion.WhileSubscribed(SettingsSharingTimeoutMillis),
        initialValue = AppSettings().themeMode,
      )

  /** Currently selected language code. */
  val languageCode: StateFlow<String> =
    settings
      .map { appSettings -> appSettings.languageCode }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Companion.WhileSubscribed(SettingsSharingTimeoutMillis),
        initialValue = AppSettings().languageCode,
      )

  /** Currently configured analysis server base URL. */
  val serverBaseUrl: StateFlow<String> =
    settings
      .map { appSettings -> appSettings.serverBaseUrl }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Companion.WhileSubscribed(SettingsSharingTimeoutMillis),
        initialValue = AppSettings().serverBaseUrl,
      )

  init {
    ensureDefaultSettings()
    observeServerBaseUrl()
  }

  internal inline fun <reified T : SettingsDialogState> updateDialogState(
    crossinline transform: (T) -> SettingsDialogState
  ) {
    _runtimeUiState.update { state ->
      val dialogState = state.dialogState

      if (dialogState is T) {
        state.copy(dialogState = transform(dialogState))
      } else {
        state
      }
    }
  }

  fun setDialogState(dialogState: SettingsDialogState) = performSetDialogState(dialogState)

  fun clearDialogState() = performClearDialogState()

  fun showResetDataDialog() = performShowResetDataDialog()

  fun showChangeServerBaseUrlDialog() = performShowChangeServerBaseUrlDialog()

  fun setResetDataConfirmationText(value: String) = performSetResetDataConfirmationText(value)

  fun clearResetDataConfirmationText() = performClearResetDataConfirmationText()

  fun setDraftServerBaseUrl(value: String) = performSetDraftServerBaseUrl(value)

  fun clearDraftServerBaseUrl() = performClearDraftServerBaseUrl()

  /** Checks the applied endpoint using cancellable, URL-specific availability state. */
  fun checkSavedServerBaseUrl() = performCheckSavedServerBaseUrl()

  /** Checks the dialog draft independently of the saved endpoint status. */
  fun checkDraftServerBaseUrl() = performCheckDraftServerBaseUrl()

  fun checkSavedServerBaseUrlIfStale() = performCheckSavedServerBaseUrlIfStale()

  fun setThemeMode(themeMode: ThemeMode) = performSetThemeMode(themeMode)

  fun setLanguageCode(languageCode: String) = performSetLanguageCode(languageCode)

  /** Normalizes and validates the endpoint before persisting and checking it. */
  fun setServerBaseUrl(serverBaseUrl: String) = performSetServerBaseUrl(serverBaseUrl)

  /** Persists a valid endpoint draft and closes its dialog before checking availability. */
  fun saveDraftServerBaseUrl() = performSaveDraftServerBaseUrl()

  fun clearServerBaseUrl() = performClearServerBaseUrl()

  /** Returns inserted report IDs in input order using the repository import boundary. */
  suspend fun importInspectionReports(inspectionReports: List<InspectionReport>) =
    performImportInspectionReports(inspectionReports)

  /** Resets report data only when the active reset dialog has a valid confirmation word. */
  fun resetInspectionReportsToDefaults() = performResetInspectionReportsToDefaults()

  companion object {

    /** Creates a factory for [SettingsViewModel]. */
    fun factory(
      settingsRepository: SettingsRepository,
      inspectionReportRepository: InspectionReportRepository,
      inspectionProvider: InspectionProvider,
    ): ViewModelProvider.Factory {
      return viewModelFactory {
        SettingsViewModel(
          settingsRepository = settingsRepository,
          inspectionReportRepository = inspectionReportRepository,
          inspectionProvider = inspectionProvider,
        )
      }
    }
  }
}
