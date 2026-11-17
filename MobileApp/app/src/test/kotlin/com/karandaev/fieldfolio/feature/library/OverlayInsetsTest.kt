package com.karandaev.fieldfolio.feature.library

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.room.Room
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.json.MoshiFactory
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.dialog.*
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.state.DetectionPreviewState
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayInsetsTest {
  @get:Rule val testName = org.junit.rules.TestName()
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var db: AppDatabase? = null
  private var library: ReportLibrary? = null

  @After
  fun close() {
    library?.lockAll()
    db?.close()
  }

  // Use measured size and screen position: clipped bounds can conceal overflow.
  private fun bounds(tag: String): Rect {
    val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    return Rect(
      node.positionOnScreen.x,
      node.positionOnScreen.y,
      node.positionOnScreen.x + node.size.width,
      node.positionOnScreen.y + node.size.height,
    )
  }

  private fun bars(
    left: Int = 0,
    top: Int = 24,
    right: Int = 0,
    bottom: Int = 48,
    ime: Int = 0,
    cutoutLeft: Int = 0,
  ) {
    compose.waitForIdle()
    compose.runOnIdle {
      val decor = ShadowDialog.getLatestDialog().window!!.decorView
      val insets =
        WindowInsetsCompat.Builder()
          .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, top, 0, 0))
          .setVisible(WindowInsetsCompat.Type.statusBars(), true)
          .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(left, 0, right, bottom))
          .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
          .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, ime))
          .setVisible(WindowInsetsCompat.Type.ime(), ime > 0)
          .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(cutoutLeft, 0, 0, 0))
          .build()
      ViewCompat.dispatchApplyWindowInsets(decor, insets)
    }
    compose.waitForIdle()
    val window = bounds("overlay-window")
    val workspace = bounds("overlay-workspace")
    checkNativeWindow(window)
    assertEquals(window.left + left, workspace.left, 1f)
    assertEquals(window.top + top, workspace.top, 1f)
    assertEquals(window.right - right, workspace.right, 1f)
    assertEquals(window.bottom - maxOf(bottom, ime), workspace.bottom, 1f)
    compose.runOnIdle {
      val view = ShadowDialog.getLatestDialog().window!!.decorView
      val image =
        android.graphics.Bitmap.createBitmap(
          view.width,
          view.height,
          android.graphics.Bitmap.Config.ARGB_8888,
        )
      view.draw(android.graphics.Canvas(image))
      val directory = java.io.File("build/previews-v2").apply { mkdirs() }
      java.io
        .File(directory, "${testName.methodName}-api${android.os.Build.VERSION.SDK_INT}.png")
        .outputStream()
        .use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
      image.recycle()
    }
  }

  private fun checkNativeWindow(rect: Rect) {
    compose.runOnIdle {
      val native = ShadowDialog.getLatestDialog().window!!
      assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, native.attributes.width)
      assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, native.attributes.height)
      if (android.os.Build.VERSION.SDK_INT >= 30) assertEquals(0, native.attributes.fitInsetsTypes)
      val pixels = compose.activity.resources.displayMetrics
      assertEquals(pixels.widthPixels.toFloat(), rect.width, 1f)
      assertEquals(pixels.heightPixels.toFloat(), rect.height, 1f)
      assertEquals(0f, rect.left, 1f)
      assertEquals(0f, rect.top, 1f)
    }
  }

  private fun contained(tag: String, centered: Boolean = false) {
    val safe = bounds("overlay-workspace")
    val card = bounds(tag)
    assertTrue(
      "$tag outside $safe: $card",
      card.left >= safe.left &&
        card.top >= safe.top &&
        card.right <= safe.right &&
        card.bottom <= safe.bottom,
    )
    assertTrue("Empty $tag", card.width > 0 && card.height > 0)
    if (centered) {
      assertEquals(safe.center.x, card.center.x, 1f)
      assertEquals(safe.center.y, card.center.y, 1f)
    }
  }

  private fun showDestination(): Pair<Long, Long> = runBlocking {
    val database = Room.inMemoryDatabaseBuilder(compose.activity, AppDatabase::class.java).build()
    db = database
    database.libraryDao().state(LibraryState("initialized", "true"))
    val storage = ReportLibrary(compose.activity, database, MoshiFactory.create())
    library = storage
    val report =
      storage.saveWithImage(
        InspectionReport(
          title = "Report",
          content = "Details",
          point = MapPoint(56.0, 92.0),
          lastUpdatedAtMillis = 1234,
        ),
        null,
        null,
        false,
      )
    repeat(45) { storage.createFolder("Folder $it", null) }
    val destination = storage.createFolder("Destination", null)
    val vm = LibraryViewModel(storage)
    compose.setContent {
      MaterialTheme {
        LibraryDestinationDialog(
          vm,
          LibraryClipboard(listOf(report), true),
          {},
          { vm.paste(LibraryClipboard(listOf(report), true), it) },
        )
      }
    }
    compose.waitUntil(15000) {
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      vm.nodes.size == 47
    }
    report to destination
  }

  @Test
  fun portraitMoveActionIsPinnedAndMovesTheReport() {
    val (report, destination) = showDestination()
    bars()
    contained("library-move-here")
    compose.onNodeWithTag("destination-folders").performScrollToNode(hasText("Destination"))
    compose.onNodeWithText("Destination").performClick()
    compose.onNodeWithTag("library-move-here").assertIsDisplayed().assertIsEnabled().performClick()
    compose.waitUntil(15000) {
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      runBlocking { db!!.libraryDao().all().single { it.id == report }.parentId == destination }
    }
  }

  @Test
  @Config(qualifiers = "en-rUS-w900dp-h420dp-land-mdpi")
  fun landscapeMoveActionSurvivesSideNavigationAndKeyboard() {
    showDestination()
    bars(right = 48, bottom = 0, ime = 180)
    contained("library-move-here")
    compose.onNodeWithTag("library-move-here").assertIsDisplayed().assertIsEnabled()
    compose.onNodeWithTag("destination-folders").performScrollToNode(hasText("Destination"))
    compose.onNodeWithText("Destination").assertIsDisplayed()
  }

  @Test
  fun portraitPreviewIsCenteredInTheWorkingArea() {
    compose.setContent {
      MaterialTheme {
        DetectionPreviewDialog(DetectionPreviewState(null, null, 0.75f, emptyList()), {})
      }
    }
    bars()
    contained("detection-preview-card", true)
  }

  @Test
  @Config(qualifiers = "en-rUS-w900dp-h420dp-land-mdpi")
  fun landscapePreviewIsCenteredWithNavigationOnTheLeft() {
    compose.setContent {
      MaterialTheme {
        DetectionPreviewLandscapeDialog(DetectionPreviewState(null, null, 1.5f, emptyList()), {})
      }
    }
    bars(left = 48, bottom = 0)
    contained("detection-preview-card", true)
    compose.onNodeWithContentDescription("Close detection preview").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "en-rUS-w900dp-h420dp-land-mdpi")
  fun longAlertKeepsActionsVisibleAboveTheKeyboard() {
    var confirmed = false
    compose.setContent {
      MaterialTheme {
        MyDialogHost(
          true,
          "Long dialog",
          androidx.compose.material.icons.Icons.Default.Close,
          MaterialTheme.colorScheme.primary,
          "Confirm",
          "Cancel",
          true,
          true,
          {},
          { confirmed = true },
        ) {
          repeat(40) { Text("Line $it") }
        }
      }
    }
    bars(left = 48, bottom = 0, ime = 160)
    contained("overlay-panel", true)
    compose.onNodeWithText("Confirm").assertIsDisplayed().performClick()
    assertTrue(confirmed)
  }

  @Test
  @Config(qualifiers = "ru-rRU-w900dp-h420dp-land-mdpi")
  fun russianPasswordFormCanScrollAboveTheKeyboard() {
    compose.setContent {
      MaterialTheme { LibraryPasswordDialog(R.string.library_password, true, false, {}, {}) }
    }
    bars(right = 48, bottom = 0, ime = 160)
    compose.onNodeWithContentDescription("Продолжить").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "en-rUS-w900dp-h420dp-land-mdpi")
  fun smallerContentDpDoesNotShrinkTheWindowOrMirrorTheNavigationInset() {
    compose.runOnUiThread {
      val resources = compose.activity.resources
      val config = android.content.res.Configuration(resources.configuration)
      val pixels = resources.displayMetrics
      config.screenWidthDp = (pixels.widthPixels / pixels.density).toInt() - 48
      config.screenHeightDp = (pixels.heightPixels / pixels.density).toInt() - 24
      @Suppress("DEPRECATION") resources.updateConfiguration(config, pixels)
    }
    compose.setContent {
      MaterialTheme {
        DetectionPreviewLandscapeDialog(DetectionPreviewState(null, null, 1.5f, emptyList()), {})
      }
    }
    bars(right = 48, bottom = 0, cutoutLeft = 80)
    contained("detection-preview-card", true)
  }

  @Test
  @Config(sdk = [28, 35])
  fun nativeWindowPreservesSecurityAndBackPolicy() {
    var shown by mutableStateOf(true)
    var allowBack by mutableStateOf(false)
    var textInputWindow: android.view.Window? = null
    compose.setContent {
      MaterialTheme {
        if (shown)
          SafeAppDialog(
            { shown = false },
            androidx.compose.ui.window.DialogProperties(
              dismissOnBackPress = allowBack,
              securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn,
            ),
          ) {
            val parent = androidx.compose.ui.platform.LocalView.current.parent
            SideEffect {
              textInputWindow = (parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            }
            Text("Private")
          }
      }
    }
    checkNativeWindow(bounds("overlay-window"))
    compose.runOnIdle {
      val dialog = ShadowDialog.getLatestDialog()
      assertSame("Text input must target the dialog window", dialog.window, textInputWindow)
      assertTrue(
        dialog.window!!.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE !=
          0
      )
      @Suppress("DEPRECATION") dialog.onBackPressed()
      assertTrue(shown)
      allowBack = true
    }
    compose.waitForIdle()
    compose.runOnIdle { @Suppress("DEPRECATION") ShadowDialog.getLatestDialog().onBackPressed() }
    compose.waitForIdle()
    assertFalse(shown)
  }
}
