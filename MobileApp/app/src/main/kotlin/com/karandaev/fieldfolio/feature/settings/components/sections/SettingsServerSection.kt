package com.karandaev.fieldfolio.feature.settings.components.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.feature.settings.components.server.SettingsServerBaseUrlRow
import com.karandaev.fieldfolio.feature.settings.state.ServerAvailabilityState

/**
 * Settings section that displays analysis backend server configuration.
 *
 * @param serverBaseUrl Currently saved server base URL.
 * @param availabilityState Current availability state of the saved server URL.
 * @param enabled Whether the card and check button are enabled.
 * @param onServerBaseUrlClick Called when server base URL card is clicked.
 * @param onCheckServerClick Called when availability marker button is clicked.
 */
@Composable
internal fun SettingsServerSection(
  serverBaseUrl: String,
  availabilityState: ServerAvailabilityState,
  enabled: Boolean,
  onServerBaseUrlClick: () -> Unit,
  onCheckServerClick: () -> Unit,
) {
  SettingsSection(title = stringResource(R.string.ui_server)) {
    SettingsServerBaseUrlRow(
      serverBaseUrl = serverBaseUrl,
      availabilityState = availabilityState,
      enabled = enabled,
      onClick = onServerBaseUrlClick,
      onCheckServerClick = onCheckServerClick,
    )
  }
}
