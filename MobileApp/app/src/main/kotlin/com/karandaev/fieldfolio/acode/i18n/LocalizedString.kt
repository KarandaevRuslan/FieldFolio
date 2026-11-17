package com.karandaev.fieldfolio.acode.i18n
fun localizedString(context: android.content.Context, language: String, id: Int): String {
  val config = android.content.res.Configuration(context.resources.configuration)
  config.setLocale(java.util.Locale.forLanguageTag(language))
  return context.createConfigurationContext(config).getString(id)
}
