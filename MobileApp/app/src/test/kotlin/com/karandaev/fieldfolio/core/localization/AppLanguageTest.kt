package com.karandaev.fieldfolio.core.localization

import android.app.Application
import android.content.Context
import android.content.res.Resources
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.data.repository.LocalSettingsRepository
import com.karandaev.fieldfolio.core.domain.model.ThemeMode
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class AppLanguageTest {
  private val originalLocale = Locale.getDefault()
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  @After
  fun reset() {
    AppLanguage.persist(context, "en")
    Locale.setDefault(originalLocale)
  }

  @Test
  fun explicitLanguageSurvivesReopeningContextAndControlsResourceAndDateLocales() {
    AppLanguage.persist(context, "ru")
    val russian = AppLanguage.wrap(context)
    assertEquals("ru", russian.resources.configuration.locales[0].language)
    assertEquals("ru", Locale.getDefault().language)
    assertEquals("Настройки", russian.getString(R.string.destination_settings))
    assertEquals("Пароли не совпадают.", russian.localizeMessage("Passwords do not match."))
    assertEquals(
      "Письмо для сброса пароля отправлено на user@example.com. Проверьте почту.",
      russian.localizeMessage(
        "Password reset email sent to user@example.com. Please check your inbox."
      ),
    )
    assertEquals("HTTP diagnostic 987", russian.localizeMessage("HTTP diagnostic 987"))
    assertEquals("ru", AppLanguage.savedChoice(ApplicationProvider.getApplicationContext()))
    AppLanguage.persist(context, "en")
    val english = AppLanguage.wrap(context)
    assertEquals("Settings", english.getString(R.string.destination_settings))
    assertEquals("en", Locale.getDefault().language)
  }

  @Test
  @Config(qualifiers = "ru-rRU")
  fun systemChoiceUsesTheSystemLocaleAndUnknownChoicesFallBackToEnglish() {
    AppLanguage.persist(context, "system")
    assertEquals(
      Resources.getSystem().configuration.locales[0],
      AppLanguage.wrap(context).resources.configuration.locales[0],
    )
    AppLanguage.persist(context, "unsupported")
    assertEquals("en", AppLanguage.savedChoice(context))
    assertEquals("en", AppLanguage.wrap(context).resources.configuration.locales[0].language)
  }

  @Test
  fun roomLanguageUpdatePreservesThemeAndServerSettings() = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    try {
      val repository = LocalSettingsRepository(db.settingsDao())
      repository.ensureDefaults()
      repository.setThemeMode(ThemeMode.DARK)
      repository.setServerBaseUrl("https://example.com")
      repository.setLanguageCode("ru")
      val restored = LocalSettingsRepository(db.settingsDao()).observeSettings().first()
      assertEquals("ru", restored.languageCode)
      assertEquals(ThemeMode.DARK, restored.themeMode)
      assertEquals("https://example.com", restored.serverBaseUrl)
    } finally {
      db.close()
    }
  }
}
