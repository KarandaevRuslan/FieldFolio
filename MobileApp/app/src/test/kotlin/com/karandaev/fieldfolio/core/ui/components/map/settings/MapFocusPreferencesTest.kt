package com.karandaev.fieldfolio.core.ui.components.map.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapFocusPreferencesTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @After fun restoreDefault() = MapFocusPreferences.setShowCurrentMapCenter(context, true)

  @Test fun mapCenterIsShownByDefaultAndPersistentlyToggled() {
    context.getSharedPreferences("map_view", Context.MODE_PRIVATE).edit().clear().commit()
    assertTrue(MapFocusPreferences.showCurrentMapCenter(context))
    MapFocusPreferences.setShowCurrentMapCenter(context, false)
    assertFalse(MapFocusPreferences.showCurrentMapCenter(context))
    MapFocusPreferences.setShowCurrentMapCenter(context, true)
    assertTrue(MapFocusPreferences.showCurrentMapCenter(context))
  }
}
