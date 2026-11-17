package com.karandaev.fieldfolio.core.ui.components.map.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Focus-menu display options, shared by the main map and all embedded maps. */
object MapFocusPreferences {
  private const val PREFERENCES = "map_view"
  private const val SHOW_CURRENT_MAP_CENTER = "focus_show_current_map_center"
  const val DEFAULT_SHOW_CURRENT_MAP_CENTER = true

  private fun preferences(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

  fun showCurrentMapCenter(context: Context): Boolean =
    preferences(context).getBoolean(SHOW_CURRENT_MAP_CENTER, DEFAULT_SHOW_CURRENT_MAP_CENTER)

  fun setShowCurrentMapCenter(context: Context, show: Boolean) {
    preferences(context).edit().putBoolean(SHOW_CURRENT_MAP_CENTER, show).apply()
  }

  @Composable
  fun rememberShowCurrentMapCenter(): State<Boolean> {
    val context = LocalContext.current
    val state = remember(context) { mutableStateOf(showCurrentMapCenter(context)) }
    DisposableEffect(context) {
      val prefs = preferences(context)
      val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
          if (key == SHOW_CURRENT_MAP_CENTER) state.value = showCurrentMapCenter(context)
        }
      prefs.registerOnSharedPreferenceChangeListener(listener)
      onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
  }
}
