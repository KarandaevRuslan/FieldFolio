package com.karandaev.fieldfolio.core.data.local.settings

import com.karandaev.fieldfolio.core.domain.model.AppSettings
import com.karandaev.fieldfolio.core.domain.model.ThemeMode

/** Converts a local settings entity to a domain settings model. */
fun AppSettingsEntity.toDomain(): AppSettings {
  return AppSettings(
    languageCode = languageCode,
    themeMode = ThemeMode.from(themeMode),
    serverBaseUrl = serverBaseUrl,
  )
}

/** Converts a domain settings model to a local settings entity. */
fun AppSettings.toEntity(): AppSettingsEntity {
  return AppSettingsEntity(
    languageCode = languageCode,
    themeMode = themeMode.name,
    serverBaseUrl = serverBaseUrl,
  )
}
