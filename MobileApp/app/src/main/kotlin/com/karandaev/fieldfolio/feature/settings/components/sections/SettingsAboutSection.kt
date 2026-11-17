package com.karandaev.fieldfolio.feature.settings.components.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.feature.settings.components.about.SettingsAboutContent

@Composable
internal fun SettingsAboutSection(appVersionName: String) {
  SettingsSection(title = stringResource(R.string.ui_about)) {
    SettingsAboutContent(appVersionName = appVersionName)
  }
}
