package com.karandaev.fieldfolio.feature.settings.state

import kotlinx.coroutines.flow.update

/** Shows the provided settings dialog. */
internal fun SettingsViewModel.performSetDialogState(dialogState: SettingsDialogState) {
  if (dialogState !is SettingsDialogState.ChangeServerBaseUrl) {
    cancelDraftServerAvailabilityCheck()
  }

  _runtimeUiState.update { state -> state.copy(dialogState = dialogState) }
}

/** Hides the currently shown settings dialog. */
internal fun SettingsViewModel.performClearDialogState() {
  cancelDraftServerAvailabilityCheck()

  _runtimeUiState.update { state -> state.copy(dialogState = SettingsDialogState.None) }
}

/** Shows reset data confirmation dialog. */
internal fun SettingsViewModel.performShowResetDataDialog() {
  setDialogState(SettingsDialogState.ResetData())
}

/** Shows server base URL edit dialog using the currently persisted URL as initial draft. */
internal fun SettingsViewModel.performShowChangeServerBaseUrlDialog() {
  val currentServerBaseUrl = currentSavedServerBaseUrl()

  setDialogState(
    SettingsDialogState.ChangeServerBaseUrl(
      draftServerBaseUrl = currentServerBaseUrl,
      draftServerAvailabilityState =
        currentServerAvailabilityState(serverBaseUrl = currentServerBaseUrl),
    )
  )
}

/** Updates reset data confirmation text. */
internal fun SettingsViewModel.performSetResetDataConfirmationText(value: String) {
  updateDialogState<SettingsDialogState.ResetData> { dialogState ->
    dialogState.copy(confirmationText = value)
  }
}

/** Clears reset data confirmation text. */
internal fun SettingsViewModel.performClearResetDataConfirmationText() {
  setResetDataConfirmationText("")
}

/** Updates server base URL draft inside the edit dialog. */
internal fun SettingsViewModel.performSetDraftServerBaseUrl(value: String) {
  cancelDraftServerAvailabilityCheck()

  val draftAvailabilityState =
    if (normalizeServerBaseUrl(value) == currentSavedServerBaseUrl()) {
      currentServerAvailabilityState(serverBaseUrl = value)
    } else {
      ServerAvailabilityState.Unknown
    }

  updateDialogState<SettingsDialogState.ChangeServerBaseUrl> { dialogState ->
    dialogState.copy(
      draftServerBaseUrl = value,
      draftServerAvailabilityState = draftAvailabilityState,
    )
  }
}

/** Clears server base URL draft inside the edit dialog. */
internal fun SettingsViewModel.performClearDraftServerBaseUrl() {
  setDraftServerBaseUrl("")
}

internal fun SettingsViewModel.setSavedServerAvailabilityState(
  availabilityState: ServerAvailabilityState
) {
  _runtimeUiState.update { state ->
    state.copy(
      savedServerAvailabilityState = availabilityState,
      dialogState =
        syncDraftServerAvailabilityWithSaved(
          state.dialogState,
          savedServerBaseUrl = currentSavedServerBaseUrl(),
          savedAvailabilityState = availabilityState,
        ),
    )
  }
}

internal fun SettingsViewModel.setDraftServerAvailabilityState(
  availabilityState: ServerAvailabilityState
) {
  updateDialogState<SettingsDialogState.ChangeServerBaseUrl> { dialogState ->
    dialogState.copy(draftServerAvailabilityState = availabilityState)
  }
}

/**
 * Synchronizes opened server base URL dialog with saved availability result.
 *
 * This updates draft availability only when:
 * - change server dialog is opened;
 * - saved check is finished, not Checking;
 * - draft check is not running;
 * - draft URL is still the same as saved URL.
 */
internal fun SettingsViewModel.syncDraftServerAvailabilityWithSaved(
  dialog: SettingsDialogState,
  savedServerBaseUrl: String,
  savedAvailabilityState: ServerAvailabilityState,
): SettingsDialogState {
  if (dialog !is SettingsDialogState.ChangeServerBaseUrl) {
    return dialog
  }

  if (savedAvailabilityState == ServerAvailabilityState.Checking) {
    return dialog
  }

  if (draftServerAvailabilityCheck.url != null) {
    return dialog
  }

  val normalizedDraftServerBaseUrl = normalizeServerBaseUrl(dialog.draftServerBaseUrl)

  if (normalizedDraftServerBaseUrl != savedServerBaseUrl) {
    return dialog
  }

  return dialog.copy(
    draftServerAvailabilityState =
      if (normalizedDraftServerBaseUrl.isBlank()) {
        ServerAvailabilityState.Unknown
      } else {
        savedAvailabilityState
      }
  )
}

internal val SettingsDialogState.ResetData.isConfirmationValid: Boolean
  get() = confirmationText.trim() == RESET_DATA_CONFIRMATION_WORD
