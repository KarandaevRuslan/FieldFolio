package com.karandaev.fieldfolio.feature.library

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.json.MoshiFactory
import com.karandaev.fieldfolio.core.library.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var db: AppDatabase
  private lateinit var library: ReportLibrary
  private lateinit var vm: LibraryViewModel

  @After
  fun close() {
    if (::library.isInitialized) library.lockAll()
    if (::db.isInitialized) db.close()
  }

  private fun show() = runBlocking {
    db = Room.inMemoryDatabaseBuilder(compose.activity, AppDatabase::class.java).build()
    db.libraryDao().state(LibraryState("initialized", "true"))
    library = ReportLibrary(compose.activity, db, MoshiFactory.create())
    val folder = library.createFolder("Survey", null)
    library.saveWithImage(
      InspectionReport(
        title = "Road report",
        content = "Details",
        point = MapPoint(56.0, 92.0),
        lastUpdatedAtMillis = 1234,
      ),
      folder,
      null,
      false,
    )
    vm = LibraryViewModel(library)
    compose.setContent {
      MaterialTheme { Surface(Modifier.fillMaxSize()) { LibraryBrowser(vm, {}, {}, {}) } }
    }
    folder
  }

  private fun waitText(value: String) {
    compose.waitUntil(15000) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun screenshot(name: String) {
    compose.waitForIdle()
    compose.runOnIdle {
      val view = compose.activity.window.decorView
      val bitmap =
        android.graphics.Bitmap.createBitmap(
          view.width,
          view.height,
          android.graphics.Bitmap.Config.ARGB_8888,
        )
      view.draw(android.graphics.Canvas(bitmap))
      java.io.File("build/previews").mkdirs()
      java.io.File("build/previews/$name.png").outputStream().use {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
      }
      bitmap.recycle()
    }
  }

  @Test
  fun phoneBrowsesHistoryAndCopiesAWholeSubtreeInsideTheVirtualLibrary() {
    show()
    waitText("Survey")
    screenshot("library-phone-en")
    compose.onNodeWithText("Survey").performClick()
    waitText("Road report")
    compose.onNodeWithContentDescription("Back").performClick()
    waitText("Survey")
    compose.onNodeWithContentDescription("Forward").performClick()
    waitText("Road report")
    compose.onNodeWithContentDescription("Parent folder").performClick()
    waitText("Survey")
    compose.onNodeWithContentDescription("Select all").performClick()
    compose.onNodeWithContentDescription("Copy").performClick()
    compose.onNodeWithContentDescription("Paste").performClick()
    waitText("Survey (2)")
    compose.runOnIdle {
      runBlocking {
        val all = db.libraryDao().all()
        val copied = all.single { it.name == "Survey (2)" }
        assertTrue(all.any { it.parentId == copied.id && it.name == "Road report" })
      }
    }
  }

  @Test
  @Config(qualifiers = "ru-rRU-w1100dp-h820dp-land-mdpi")
  fun tabletKeepsRussianActionsAndFolderSelectionVisible() {
    show()
    waitText("Survey")
    compose.onNodeWithContentDescription("Новая папка").assertIsDisplayed()
    compose.onNodeWithContentDescription("Импорт архива / JSON").assertIsDisplayed()
    compose.onNodeWithContentDescription("Экспорт архива").assertIsDisplayed()
    compose.onNodeWithContentDescription("Выбрать всё").performClick()
    compose.onNodeWithContentDescription("Копировать").assertIsDisplayed()
    compose.onNodeWithContentDescription("Вырезать").assertIsDisplayed()
    compose.onNodeWithContentDescription("Переместить").assertIsDisplayed()
    screenshot("library-tablet-ru-selection")
  }

  @Test
  fun createsEmptyFolderAndNavigatesByEnteredAbsolutePath() {
    show()
    waitText("Survey")
    compose.onNodeWithContentDescription("New folder").performClick()
    compose.onNodeWithTag("library-name").performTextInput("Empty")
    compose.onNodeWithText("Save").performClick()
    waitText("Empty")
    compose.onNodeWithText("Empty").performClick()
    waitText("This folder is empty")
    compose.onNodeWithContentDescription("Go to path").performClick()
    compose.onNodeWithTag("library-path").performTextReplacement("/Survey")
    compose.onNodeWithContentDescription("Open").performClick()
    waitText("Road report")
    compose.onNodeWithContentDescription("Parent folder").assertIsDisplayed()
  }
}
