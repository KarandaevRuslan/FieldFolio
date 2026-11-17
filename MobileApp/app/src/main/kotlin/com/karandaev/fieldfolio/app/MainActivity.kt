package com.karandaev.fieldfolio.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.localization.AppLanguage
import com.karandaev.fieldfolio.core.state.PersistedAppStateViewModel
import com.karandaev.fieldfolio.core.ui.theme.MyTheme
import com.karandaev.fieldfolio.feature.settings.state.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main activity that hosts the MapNotes Compose application.
 *
 * The activity is responsible only for setting up edge-to-edge mode, creating the root application
 * dependencies, collecting global settings, and applying the app theme.
 */
class MainActivity : ComponentActivity() {

  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(AppLanguage.wrap(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    lifecycleScope.launch {
      repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
        com.karandaev.fieldfolio.core.names.NameResolutionEvents.events.collect { event ->
          android.widget.Toast.makeText(
              this@MainActivity,
              getString(
                com.karandaev.fieldfolio.R.string.v16_name_resolved,
                event.requested,
                event.resolved,
              ),
              android.widget.Toast.LENGTH_LONG,
            )
            .show()
        }
      }
    }

    setContent {
      val app = rememberAppServices()
      LaunchedEffect(app.settingsRepository) {
        app.settingsRepository
          .observeSettings()
          .map { AppLanguage.normalize(it.languageCode) }
          .distinctUntilChanged()
          .collect { code ->
            if (AppLanguage.savedChoice(this@MainActivity) != code) {
              withContext(Dispatchers.IO) { AppLanguage.persist(this@MainActivity, code) }
              recreate()
            }
          }
      }

      val settingsViewModel: SettingsViewModel =
        viewModel(
          factory =
            SettingsViewModel.factory(
              settingsRepository = app.settingsRepository,
              inspectionReportRepository = app.inspectionReportRepository,
              inspectionProvider = app.inspectionProvider,
            )
        )

      val persistedAppStateViewModel: PersistedAppStateViewModel =
        viewModel(
          factory =
            PersistedAppStateViewModel.factory(
              persistedAppStateRepository = app.persistedAppStateRepository
            )
        )

      val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

      MyTheme(themeMode = settings.themeMode) {
        MyApp(
          app = app,
          settingsViewModel = settingsViewModel,
          persistedAppStateViewModel = persistedAppStateViewModel,
        )
      }
    }
  }
}
