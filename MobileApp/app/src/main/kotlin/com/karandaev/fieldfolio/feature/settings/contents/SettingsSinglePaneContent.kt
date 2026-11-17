package com.karandaev.fieldfolio.feature.settings.contents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.auth.model.UserProfile
import com.karandaev.fieldfolio.core.domain.model.ThemeMode
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll
import com.karandaev.fieldfolio.feature.settings.components.sections.SettingsAboutSection
import com.karandaev.fieldfolio.feature.settings.components.sections.SettingsAccountSection
import com.karandaev.fieldfolio.feature.settings.components.sections.SettingsAppearanceSection
import com.karandaev.fieldfolio.feature.settings.components.sections.SettingsDataSection
import com.karandaev.fieldfolio.feature.settings.components.sections.SettingsDocumentSection
import com.karandaev.fieldfolio.feature.settings.components.sections.SettingsServerSection
import com.karandaev.fieldfolio.feature.settings.state.ServerAvailabilityState

@Composable
internal fun SettingsSinglePaneContent(
  profile: UserProfile?,
  themeMode: ThemeMode,
  languageCode: String,
  serverBaseUrl: String,
  serverAvailabilityState: ServerAvailabilityState,
  appVersionName: String,
  isExporting: Boolean,
  isImporting: Boolean,
  isResetting: Boolean,
  onProfileClick: () -> Unit,
  onSignInClick: () -> Unit,
  onLanguageCodeSelect: (String) -> Unit,
  onThemeModeSelect: (ThemeMode) -> Unit,
  onServerBaseUrlClick: () -> Unit,
  onCheckServerClick: () -> Unit,
  onExportClick: () -> Unit,
  onImportClick: () -> Unit,
  onResetClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    SettingsAccountSection(
      profile = profile,
      onProfileClick = onProfileClick,
      onSignInClick = onSignInClick,
    )

    SettingsAppearanceSection(
      themeMode = themeMode,
      languageCode = languageCode,
      onLanguageCodeSelect = onLanguageCodeSelect,
      onThemeModeSelect = onThemeModeSelect,
    )

    SettingsServerSection(
      serverBaseUrl = serverBaseUrl,
      availabilityState = serverAvailabilityState,
      enabled = !isResetting && !isExporting && !isImporting,
      onServerBaseUrlClick = onServerBaseUrlClick,
      onCheckServerClick = onCheckServerClick,
    )

    SettingsDataSection(
      isExporting = isExporting,
      isImporting = isImporting,
      isResetting = isResetting,
      onExportClick = onExportClick,
      onImportClick = onImportClick,
      onResetClick = onResetClick,
    )

    SettingsDocumentSection()

    SettingsAboutSection(appVersionName = appVersionName)
  }
}
