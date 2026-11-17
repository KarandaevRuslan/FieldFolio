package com.karandaev.fieldfolio.feature.settings.state

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Checks availability of the currently persisted server base URL. */
internal fun SettingsViewModel.performCheckSavedServerBaseUrl() {
  checkSavedServerBaseUrl(serverBaseUrl = currentSavedServerBaseUrl())
}

/** Checks availability of the currently edited server base URL draft. */
internal fun SettingsViewModel.performCheckDraftServerBaseUrl() {
  val dialogState = runtimeUiState.value.dialogState

  if (dialogState !is SettingsDialogState.ChangeServerBaseUrl) {
    return
  }

  checkServerBaseUrlAvailability(
    serverBaseUrl = dialogState.draftServerBaseUrl,
    availabilityCheck = draftServerAvailabilityCheck,
    setAvailabilityState = ::setDraftServerAvailabilityState,
    isResultStillRelevant = { checkedUrl ->
      val currentDialogState = runtimeUiState.value.dialogState

      currentDialogState is SettingsDialogState.ChangeServerBaseUrl &&
        normalizeServerBaseUrl(currentDialogState.draftServerBaseUrl) == checkedUrl
    },
  )
}

/** Checks availability of the provided saved server base URL. */
internal fun SettingsViewModel.checkSavedServerBaseUrl(serverBaseUrl: String) {
  markSavedServerAvailabilityCheckAttempt()

  checkServerBaseUrlAvailability(
    serverBaseUrl = serverBaseUrl,
    availabilityCheck = savedServerAvailabilityCheck,
    setAvailabilityState = ::setSavedServerAvailabilityState,
    isResultStillRelevant = { checkedUrl ->
      savedServerAvailabilityCheck.url == checkedUrl || currentSavedServerBaseUrl() == checkedUrl
    },
  )
}

/**
 * Common server availability check pipeline.
 *
 * Validates URL format, cancels obsolete checks, prevents duplicate checks for the same URL,
 * updates loading state, and ignores stale results when the checked URL is no longer relevant.
 */
internal fun SettingsViewModel.checkServerBaseUrlAvailability(
  serverBaseUrl: String,
  availabilityCheck: ServerAvailabilityCheck,
  setAvailabilityState: (ServerAvailabilityState) -> Unit,
  isResultStillRelevant: (String) -> Boolean,
) {
  val normalizedBaseUrl = normalizeServerBaseUrl(serverBaseUrl)

  if (
    !prepareServerAvailabilityCheck(
      serverBaseUrl = normalizedBaseUrl,
      availabilityCheck = availabilityCheck,
      setAvailabilityState = setAvailabilityState,
    )
  ) {
    return
  }

  val checkJob =
    viewModelScope.launch(start = CoroutineStart.LAZY) {
      val availabilityState = checkServerBaseUrlSafely(serverBaseUrl = normalizedBaseUrl)

      if (availabilityCheck.url != normalizedBaseUrl) {
        return@launch
      }

      val isRelevant = isResultStillRelevant(normalizedBaseUrl)

      availabilityCheck.clear()

      if (!isRelevant) {
        return@launch
      }

      setAvailabilityState(availabilityState)
    }

  availabilityCheck.start(serverBaseUrl = normalizedBaseUrl, job = checkJob)

  checkJob.start()
}

/**
 * Checks availability of the currently persisted server base URL only if the previous check is
 * stale.
 *
 * This is useful for screen-entry checks because it survives configuration changes through
 * ViewModel.
 */
internal fun SettingsViewModel.performCheckSavedServerBaseUrlIfStale() {
  if (isSavedServerAvailabilityCheckFresh()) {
    return
  }

  checkSavedServerBaseUrl()
}

/**
 * Prepares server availability check for the provided URL.
 *
 * Returns false when the check should not be started:
 * - URL is blank;
 * - URL is invalid;
 * - the same URL is already being checked.
 *
 * Cancels obsolete checks and updates UI availability state when needed.
 */
internal fun SettingsViewModel.prepareServerAvailabilityCheck(
  serverBaseUrl: String,
  availabilityCheck: ServerAvailabilityCheck,
  setAvailabilityState: (ServerAvailabilityState) -> Unit,
): Boolean {
  if (serverBaseUrl.isBlank()) {
    availabilityCheck.cancel()
    setAvailabilityState(ServerAvailabilityState.Unknown)
    return false
  }

  if (!inspectionProvider.isValidBaseUrl(serverBaseUrl)) {
    availabilityCheck.cancel()
    setAvailabilityState(ServerAvailabilityState.Unavailable)
    return false
  }

  if (availabilityCheck.isActiveFor(serverBaseUrl)) {
    return false
  }

  availabilityCheck.cancel()
  setAvailabilityState(ServerAvailabilityState.Checking)

  return true
}

/** Checks server health and converts any failure to Unavailable state. */
internal suspend fun SettingsViewModel.checkServerBaseUrlSafely(
  serverBaseUrl: String
): ServerAvailabilityState {
  return runCatching {
      inspectionProvider
        .checkServerHealth(baseUrl = serverBaseUrl)
        .getOrDefault(false)
        .toServerAvailabilityState()
    }
    .getOrDefault(ServerAvailabilityState.Unavailable)
}

/** Cancels current saved server availability check. */
internal fun SettingsViewModel.cancelSavedServerAvailabilityCheck() {
  savedServerAvailabilityCheck.cancel()
}

/** Cancels current draft server availability check. */
internal fun SettingsViewModel.cancelDraftServerAvailabilityCheck() {
  draftServerAvailabilityCheck.cancel()
}

/** Returns whether saved server availability check was performed recently enough. */
internal fun SettingsViewModel.isSavedServerAvailabilityCheckFresh(): Boolean {
  val lastCheckTime = lastSavedServerAvailabilityCheckElapsedRealtime

  if (lastCheckTime == 0L) {
    return false
  }

  return currentElapsedRealtime() - lastCheckTime <
    SavedServerAvailabilityAutoCheckMinIntervalMillis
}

/** Marks current time as the latest saved server availability check attempt. */
internal fun SettingsViewModel.markSavedServerAvailabilityCheckAttempt() {
  lastSavedServerAvailabilityCheckElapsedRealtime = currentElapsedRealtime()
}

/** Returns monotonic elapsed realtime for check throttling. */
internal fun SettingsViewModel.currentElapsedRealtime(): Long {
  return SystemClock.elapsedRealtime()
}
