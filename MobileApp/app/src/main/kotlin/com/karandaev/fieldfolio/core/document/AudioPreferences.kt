package com.karandaev.fieldfolio.core.document

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

object AudioPreferences {
  const val KEY = "audio_seek_seconds"
  const val DEFAULT_SECONDS = 5
  val options = listOf(1, 2, 5, 10, 15, 30, 60)

  fun read(context: Context) =
    DocumentTextPreferences.preferences(context).getInt(KEY, DEFAULT_SECONDS).coerceIn(1, 60)

  fun set(context: Context, seconds: Int) {
    require(seconds in 1..60)
    DocumentTextPreferences.preferences(context).edit().putInt(KEY, seconds).apply()
  }
}

@Composable
fun rememberAudioSeekSeconds(): State<Int> {
  val context = LocalContext.current
  val value = remember { mutableIntStateOf(AudioPreferences.read(context)) }
  DisposableEffect(context) {
    val preferences = DocumentTextPreferences.preferences(context)
    val listener =
      SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AudioPreferences.KEY) value.intValue = AudioPreferences.read(context)
      }
    preferences.registerOnSharedPreferenceChangeListener(listener)
    onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
  }
  return value
}
