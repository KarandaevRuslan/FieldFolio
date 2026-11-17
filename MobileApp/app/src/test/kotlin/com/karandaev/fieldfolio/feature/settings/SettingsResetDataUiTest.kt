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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
  private val calls = AtomicInteger(0)
  private val message = AtomicReference<String?>(null)

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
              calls.incrementAndGet()
              if (fail) throw IOException("Storage failed")
              library.resetToDefaults()
            },
            onResettingChange = { resetting = it },
            onResetSuccess = { showDialog = false },
            // A Robolectric Compose continuation may run off the Android Looper thread.
            // Observe notification behavior without attempting to construct a real Toast.
            onMessage = { message.set(it) },
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
    compose.runOnIdle { assertEquals(0, calls.get()) }
    compose.onNodeWithText("Reset", substring = false).performClick()
    compose.waitUntil(15000) {
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      !showDialog && !resetting && calls.get() == 1
    }
    assertEquals(1, calls.get())
    assertEquals("Reports cleared.", message.get())
    runBlocking { assertTrue(db.libraryDao().all().isEmpty()) }
  }

  @Test
  fun failureKeepsDialogAndReportsAndAllowsRetry() {
    show(fail = true)
    compose.onNode(hasSetTextAction()).performTextInput("RESET")
    compose.onNodeWithText("Reset", substring = false).performClick()
    compose.waitUntil(15000) {
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      calls.get() == 1 && !resetting && message.get() == "Reset failed: Storage failed"
    }
    compose.runOnIdle { assertTrue(showDialog) }
    compose.onNodeWithText("Reset", substring = false).assertIsEnabled().performClick()
    compose.waitUntil(15000) {
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      calls.get() == 2 && !resetting
    }
    compose.runOnIdle { assertTrue(showDialog) }
    runBlocking { assertEquals(1, db.libraryDao().all().size) }
  }
}
