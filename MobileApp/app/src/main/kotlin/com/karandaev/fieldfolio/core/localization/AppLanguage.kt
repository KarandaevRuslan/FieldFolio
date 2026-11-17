package com.karandaev.fieldfolio.core.localization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/** Room stores the choice; this small mirror applies it before the activity inflates any UI. */
object AppLanguage {
  private const val Preferences = "application_language"
  private const val Choice = "choice"

  fun normalize(code: String): String =
    when (code) {
      "ru",
      "en",
      "system" -> code
      else -> "en"
    }

  fun savedChoice(context: Context): String =
    normalize(
      context
        .getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        .getString(Choice, "en")
        .orEmpty()
    )

  /** Call off the main thread, then recreate the activity only after this completes. */
  fun persist(context: Context, code: String) {
    check(
      context
        .getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        .edit()
        .putString(Choice, normalize(code))
        .commit()
    )
  }

  fun wrap(context: Context): Context = localizedContext(context, savedChoice(context))

  internal fun localizedContext(context: Context, code: String): Context {
    val locale =
      when (normalize(code)) {
        "system" -> Resources.getSystem().configuration.locales[0]
        "ru" -> Locale.forLanguageTag("ru")
        else -> Locale.ENGLISH
      }
    Locale.setDefault(locale)
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocales(LocaleList(locale))
    configuration.setLayoutDirection(locale)
    return context.createConfigurationContext(configuration)
  }
}
