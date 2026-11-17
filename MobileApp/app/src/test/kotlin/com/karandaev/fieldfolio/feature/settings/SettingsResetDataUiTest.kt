package com.karandaev.fieldfolio.feature.settings

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.json.MoshiFactory
import com.karandaev.fieldfolio.core.library.LibraryState
import com.karandaev.fieldfolio.core.library.ReportLibrary
import com.karandaev.fieldfolio.feature.settings.dialogs.SettingsResetDataDialogHost
import com.karandaev.fieldfolio.feature.settings.reset.rememberResetNotesToDefaultsAction
import com.karandaev.fieldfolio.feature.settings.state.RESET_DATA_CONFIRMATION_WORD
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsResetDataUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var db: AppDatabase
  private lateinit var library: ReportLibrary
  private var showDialog by mutableStateOf(true)
  private var resetting by mutableStateOf(false)
  private var calls = 0

  @After
  fun close() {
    if (::library.isInitialized) library.lockAll()
    if (::db.isInitialized) db.close()
  }

  private fun show(fail: Boolean = false) = runBlocking {
    db = Room.inMemoryDatabaseBuilder(compose.activity, AppDatabase::class.java).build()
    db.libraryDao().state(LibraryState("initialized", "true"))
    library = ReportLibrary(compose.activity, db, MoshiFactory.create())
    library.insert(
      InspectionReport(
        title = "Delete me",
        content = "Report",
        lastUpdatedAtMillis = 123,
        point = MapPoint(56.0, 92.0),
      )
    )
    compose.setContent {
      MaterialTheme {
        var confirmation by remember { mutableStateOf("") }
        val reset =
          rememberResetNotesToDefaultsAction(
            resetNotesToDefaults = {
              calls++
              if (fail) throw IOException("Storage failed")
              library.resetToDefaults()
            },
            onResettingChange = { resetting = it },
            onResetSuccess = { showDialog = false },
          )
        SettingsResetDataDialogHost(
          showDialog = showDialog,
          confirmationText = confirmation,
          requiredConfirmationWord = RESET_DATA_CONFIRMATION_WORD,
          isResetting = resetting,
          onConfirmationTextChange = { confirmation = it },
          onConfirmationTextClear = { confirmation = "" },
          onDismiss = { showDialog = false },
          onConfirm = reset,
        )
      }
    }
  }

  @Test
  fun typingResetEnablesConfirmationAndClickActuallyClearsReports() {
    show()
    compose.onNodeWithText("Reset", substring = false).assertIsNotEnabled()
    compose.onNode(hasSetTextAction()).performTextInput("wrong")
    compose.onNodeWithText("Reset", substring = false).assertIsNotEnabled()
    compose.onNode(hasSetTextAction()).performTextReplacement(" RESET ")
    compose.onNodeWithText("Reset", substring = false).assertIsEnabled()
    compose.runOnIdle { assertEquals(0, calls) }
    compose.onNodeWithText("Reset", substring = false).performClick()
    compose.waitUntil(15000) {
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      !showDialog && !resetting
    }
    assertEquals(1, calls)
    runBlocking { assertTrue(db.libraryDao().all().isEmpty()) }
  }

  @Test
  fun failureKeepsDialogAndReportsAndAllowsRetry() {
    show(fail = true)
    compose.onNode(hasSetTextAction()).performTextInput("RESET")
    compose.onNodeWithText("Reset", substring = false).performClick()
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals(1, calls)
      assertTrue(showDialog)
      assertFalse(resetting)
    }
    compose.onNodeWithText("Reset", substring = false).assertIsEnabled().performClick()
    compose.runOnIdle { assertEquals(2, calls) }
    runBlocking { assertEquals(1, db.libraryDao().all().size) }
  }
}
