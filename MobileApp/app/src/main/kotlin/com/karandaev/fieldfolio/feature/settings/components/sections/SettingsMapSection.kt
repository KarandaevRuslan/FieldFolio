package com.karandaev.fieldfolio.feature.settings.components.sections

import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.ui.components.map.settings.MapFocusPreferences

/** Same card and switch style as the existing Text settings section. */
@Composable
internal fun SettingsMapSection() {
  val context = LocalContext.current
  val showCurrentMapCenter by MapFocusPreferences.rememberShowCurrentMapCenter()
  SettingsSection(stringResource(R.string.destination_map)) {
    ListItem(
      headlineContent = { Text(stringResource(R.string.settings_map_show_center)) },
      supportingContent = { Text(stringResource(R.string.settings_map_show_center_description)) },
      trailingContent = {
        Switch(
          checked = showCurrentMapCenter,
          onCheckedChange = { MapFocusPreferences.setShowCurrentMapCenter(context, it) },
          modifier = Modifier.testTag("map-show-center-switch"),
        )
      },
    )
  }
}
