package com.karandaev.fieldfolio.feature.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.karandaev.fieldfolio.core.auth.model.UserProfile
import com.karandaev.fieldfolio.core.domain.model.ThemeMode
import com.karandaev.fieldfolio.core.ui.adaptive.AdaptiveLayoutType
import com.karandaev.fieldfolio.core.ui.adaptive.rememberAdaptiveLayoutType
import com.karandaev.fieldfolio.feature.settings.contents.SettingsSinglePaneContent
import com.karandaev.fieldfolio.feature.settings.contents.SettingsTwoPaneContent
import com.karandaev.fieldfolio.feature.settings.state.ServerAvailabilityState

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun SettingsScreen(
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
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val layoutType = rememberAdaptiveLayoutType(maxWidth = maxWidth, maxHeight = maxHeight)

    when (layoutType) {
      AdaptiveLayoutType.SinglePane -> {
        SettingsSinglePaneContent(
          profile = profile,
          themeMode = themeMode,
          languageCode = languageCode,
          serverBaseUrl = serverBaseUrl,
          serverAvailabilityState = serverAvailabilityState,
          appVersionName = appVersionName,
          isExporting = isExporting,
          isImporting = isImporting,
          isResetting = isResetting,
          onProfileClick = onProfileClick,
          onSignInClick = onSignInClick,
          onLanguageCodeSelect = onLanguageCodeSelect,
          onThemeModeSelect = onThemeModeSelect,
          onServerBaseUrlClick = onServerBaseUrlClick,
          onCheckServerClick = onCheckServerClick,
          onExportClick = onExportClick,
          onImportClick = onImportClick,
          onResetClick = onResetClick,
          modifier = Modifier.fillMaxSize(),
        )
      }

      AdaptiveLayoutType.TwoPane -> {
        SettingsTwoPaneContent(
          profile = profile,
          themeMode = themeMode,
          languageCode = languageCode,
          serverBaseUrl = serverBaseUrl,
          serverAvailabilityState = serverAvailabilityState,
          appVersionName = appVersionName,
          isExporting = isExporting,
          isImporting = isImporting,
          isResetting = isResetting,
          onProfileClick = onProfileClick,
          onSignInClick = onSignInClick,
          onLanguageCodeSelect = onLanguageCodeSelect,
          onThemeModeSelect = onThemeModeSelect,
          onServerBaseUrlClick = onServerBaseUrlClick,
          onCheckServerClick = onCheckServerClick,
          onExportClick = onExportClick,
          onImportClick = onImportClick,
          onResetClick = onResetClick,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }
}
