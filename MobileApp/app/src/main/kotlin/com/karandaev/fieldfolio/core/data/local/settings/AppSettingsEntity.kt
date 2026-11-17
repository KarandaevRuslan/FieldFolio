package com.karandaev.fieldfolio.core.data.local.settings

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karandaev.fieldfolio.core.domain.model.AppSettings
import com.karandaev.fieldfolio.core.domain.model.ThemeMode

/** Room entity for user-configurable app settings. */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
  @PrimaryKey val id: Int = DEFAULT_ID,
  val languageCode: String = DEFAULT_LANGUAGE_CODE,
  val themeMode: String = ThemeMode.LIGHT.name,
  val serverBaseUrl: String = AppSettings.DEFAULT_SERVER_BASE_URL,
) {
  companion object {
    const val DEFAULT_ID = 1
    const val DEFAULT_LANGUAGE_CODE = "en"
  }
}
