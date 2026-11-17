package com.karandaev.fieldfolio.feature.settings.state

/** Returns the currently persisted server base URL in normalized form. */
internal fun SettingsViewModel.currentSavedServerBaseUrl(): String {
  return normalizeServerBaseUrl(settings.value.serverBaseUrl)
}

/** Normalizes a server base URL entered by user or loaded from settings. */
internal fun SettingsViewModel.normalizeServerBaseUrl(serverBaseUrl: String): String {
  return serverBaseUrl.trim()
}

/**
 * Returns normalized server base URL if it is blank or valid.
 *
 * Blank URL is allowed because it means that backend API is not configured. Invalid non-blank URL
 * changes the provided availability state and returns null.
 */
internal fun SettingsViewModel.normalizeValidServerBaseUrlOrNull(
  serverBaseUrl: String,
  setInvalidAvailabilityState: (ServerAvailabilityState) -> Unit,
): String? {
  val normalizedBaseUrl = normalizeServerBaseUrl(serverBaseUrl)

  if (normalizedBaseUrl.isNotBlank() && !inspectionProvider.isValidBaseUrl(normalizedBaseUrl)) {
    setInvalidAvailabilityState(ServerAvailabilityState.Unavailable)
    return null
  }

  return normalizedBaseUrl
}

/**
 * Returns availability state that should be shown for the provided server base URL.
 *
 * Blank URLs are always treated as unknown. Saved Checking state is not copied into draft dialog
 * because it is transient and belongs to the saved URL card check lifecycle.
 */
internal fun SettingsViewModel.currentServerAvailabilityState(
  serverBaseUrl: String
): ServerAvailabilityState {
  val normalizedBaseUrl = normalizeServerBaseUrl(serverBaseUrl)

  if (normalizedBaseUrl.isBlank()) {
    return ServerAvailabilityState.Unknown
  }

  val savedAvailabilityState = runtimeUiState.value.savedServerAvailabilityState

  return if (savedAvailabilityState == ServerAvailabilityState.Checking) {
    ServerAvailabilityState.Unknown
  } else {
    savedAvailabilityState
  }
}

/** Converts server health check result to UI availability state. */
internal fun Boolean.toServerAvailabilityState(): ServerAvailabilityState {
  return if (this) {
    ServerAvailabilityState.Available
  } else {
    ServerAvailabilityState.Unavailable
  }
}
