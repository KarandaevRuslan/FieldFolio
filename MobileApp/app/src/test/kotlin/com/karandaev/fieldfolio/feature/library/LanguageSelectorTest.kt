package com.karandaev.fieldfolio.feature.library

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.karandaev.fieldfolio.core.domain.model.ThemeMode
import com.karandaev.fieldfolio.feature.settings.SettingsScreen
import com.karandaev.fieldfolio.feature.settings.state.ServerAvailabilityState
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguageSelectorTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var chosen by mutableStateOf("en")
  private var theme by mutableStateOf(ThemeMode.LIGHT)

  private fun show(fontScale: Float = 1f) {
    compose.setContent {
      MaterialTheme {
        CompositionLocalProvider(
          LocalDensity provides Density(LocalDensity.current.density, fontScale)
        ) {
          Surface(Modifier.fillMaxSize()) {
            SettingsScreen(
              profile = null,
              themeMode = theme,
              languageCode = chosen,
              serverBaseUrl = "",
              serverAvailabilityState = ServerAvailabilityState.Unknown,
              appVersionName = "1.1.0",
              isExporting = false,
              isImporting = false,
              isResetting = false,
              onProfileClick = {},
              onSignInClick = {},
              onThemeModeSelect = { theme = it },
              onLanguageCodeSelect = { chosen = it },
              onServerBaseUrlClick = {},
              onCheckServerClick = {},
              onExportClick = {},
              onImportClick = {},
              onResetClick = {},
            )
          }
        }
      }
    }
  }

  private fun bounds(tag: String): Rect {
    val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    return Rect(
      node.positionOnScreen.x,
      node.positionOnScreen.y,
      node.positionOnScreen.x + node.size.width,
      node.positionOnScreen.y + node.size.height,
    )
  }

  private fun checkGeometry() {
    compose.onNodeWithTag("language-selector").performScrollTo().assertIsDisplayed()
    val group = bounds("language-selector")
    val label = bounds("language-label")
    val theme = bounds("theme-selector")
    val themeLabel = bounds("theme-label")
    assertTrue("Collapsed label: $label", label.width > 15 && label.height > 10)
    assertTrue("Language overlaps label", label.right <= group.left)
    assertTrue("Theme overlaps label", themeLabel.right <= theme.left)
    assertTrue("Language row is below theme", theme.bottom <= group.top)
    assertEquals(theme.right, group.right, 1f)
    assertEquals(theme.width, group.width, 1f)
    assertTrue(
      group.left >= 0 && group.right <= compose.activity.resources.displayMetrics.widthPixels
    )
  }

  private fun screenshot(name: String) {
    compose.runOnIdle {
      val view = compose.activity.window.decorView
      val bitmap =
        android.graphics.Bitmap.createBitmap(
          view.width,
          view.height,
          android.graphics.Bitmap.Config.ARGB_8888,
        )
      view.draw(android.graphics.Canvas(bitmap))
      val directory = java.io.File("build/previews-v2").apply { mkdirs() }
      java.io.File(directory, "$name.png").outputStream().use {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
      }
      bitmap.recycle()
    }
  }

  @Test
  fun phoneSelectorsShareTheStyleAndApplyChanges() {
    show()
    checkGeometry()
    compose.onNodeWithTag("language-selector").performClick()
    compose.onNodeWithTag("language-ru").performClick()
    compose.runOnIdle { assertEquals("ru", chosen) }
    compose.onNodeWithTag("language-selector").performClick()
    compose.onNodeWithTag("language-system").performClick()
    compose.runOnIdle { assertEquals("system", chosen) }
    compose.onNodeWithTag("theme-selector").performScrollTo().performClick()
    compose.onNodeWithTag("theme-DARK").performClick()
    compose.runOnIdle { assertEquals(ThemeMode.DARK, theme) }
    screenshot("settings-phone")
  }

  @Test
  @Config(qualifiers = "ru-rRU-w900dp-h420dp-land-mdpi")
  fun russianLandscapeSettingsKeepLabelsAndOptionsVisible() {
    show()
    checkGeometry()
    screenshot("settings-landscape-ru")
  }

  @Test
  @Config(qualifiers = "ru-rRU-w320dp-h915dp-port-mdpi")
  fun largeRussianTextWrapsWithoutClipping() {
    show(1.8f)
    checkGeometry()
    screenshot("settings-large-text-ru")
  }
}
