package com.karandaev.fieldfolio.core.document

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

object DocumentTextPreferences {
  const val DEFAULT_PAGE_SIZE = 1000
  const val KEY = "text_page_characters"
  val options = listOf(500, 1000, 2000, 4000, 8000, 16000)

  fun preferences(context: Context) =
    context.applicationContext.getSharedPreferences("document_view", Context.MODE_PRIVATE)

  fun read(context: Context) =
    preferences(context).getInt(KEY, DEFAULT_PAGE_SIZE).coerceIn(100, TextResourceCodec.PAGE_CHARS)

  fun set(context: Context, value: Int) {
    require(value in 100..TextResourceCodec.PAGE_CHARS)
    preferences(context).edit().putInt(KEY, value).apply()
  }
}

@Composable
fun rememberTextPageSize(): State<Int> {
  val context = LocalContext.current
  val state = remember { mutableIntStateOf(DocumentTextPreferences.read(context)) }
  DisposableEffect(context) {
    val prefs = DocumentTextPreferences.preferences(context)
    val listener =
      SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == DocumentTextPreferences.KEY)
          state.intValue = DocumentTextPreferences.read(context)
      }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
  }
  return state
}

/**
 * Stable UTF-16 page boundaries, extending one code unit rather than splitting a surrogate pair.
 */
fun textPage(text: String, index: Int, size: Int): String {
  require(size > 0 && index >= 0)
  val rawStart = (index.toLong() * size).coerceAtMost(text.length.toLong()).toInt()
  var start = rawStart
  var end = minOf(text.length.toLong(), rawStart.toLong() + size).toInt()
  if (
    start > 0 &&
      start < text.length &&
      text[start].isLowSurrogate() &&
      text[start - 1].isHighSurrogate()
  )
    start++
  if (end > 0 && end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate())
    end++
  return text.substring(start.coerceAtMost(end), end)
}

fun textPageCount(text: String, size: Int): Int {
  require(size > 0)
  val count = maxOf(1, (text.length.toLong() + size - 1).div(size).toInt())
  return if (count > 1 && textPage(text, count - 1, size).isEmpty()) count - 1 else count
}
