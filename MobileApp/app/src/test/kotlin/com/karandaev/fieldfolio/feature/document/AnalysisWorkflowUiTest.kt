package com.karandaev.fieldfolio.feature.document

import android.app.Activity
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.state.action
import com.karandaev.fieldfolio.feature.models.*
import java.io.ByteArrayOutputStream
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(
  sdk = [35],
  application = MyApplication::class,
  qualifiers = "en-rUS-w412dp-h915dp-port-mdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalysisWorkflowUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun image(): ByteArray {
    val bitmap =
      Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.BLUE)
      }
    return ByteArrayOutputStream()
      .also {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        bitmap.recycle()
      }
      .toByteArray()
  }

  @Test
  fun separateAnalysisWindowRetainsCameraAndUsesFileProviderCapture() {
    val app = compose.activity.application as MyApplication
    if (FirebaseApp.getApps(app).isEmpty())
      FirebaseApp.initializeApp(
        app,
        FirebaseOptions.Builder()
          .setApplicationId("1:123:android:test")
          .setApiKey("test-key")
          .setProjectId("local-test")
          .build(),
      )
    Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.CAMERA)
    compose.setContent {
      MaterialTheme {
        AnalysisWorkspace(
          ModelChoice("road-server", "Road damage", "DETECTION", server = RoadDamageServerAdapter),
          {},
        )
      }
    }
    compose.onNodeWithTag("analysis-workspace").assertIsDisplayed()
    compose.onNodeWithContentDescription("Take photo").performClick()
    compose.waitForIdle()
    val shadow = Shadows.shadowOf(compose.activity)
    val capture = shadow.nextStartedActivityForResult
    assertNotNull(capture)
    assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, capture.intent.action)
    val uri = capture.intent.getParcelableExtra<android.net.Uri>(MediaStore.EXTRA_OUTPUT)
    assertEquals("content", uri?.scheme)
    assertTrue(app.reportLibrary.externalPicker)
    compose.runOnIdle { shadow.receiveResult(capture.intent, Activity.RESULT_CANCELED, null) }
    assertFalse(app.reportLibrary.externalPicker)
  }

  @Test
  fun existingCropCanvasAppliesAnImageAndKeepsActionsVisible() {
    var result: ByteArray? = null
    compose.setContent { MaterialTheme { AnalysisCropDialog(image(), {}, { result = it }) } }
    compose.waitUntil(10000) {
      compose
        .onAllNodes(hasContentDescription("Apply") and isEnabled())
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    compose.onNodeWithText("1:1").performClick()
    compose.onNodeWithContentDescription("Apply").assertIsDisplayed().performClick()
    compose.waitUntil(10000) { result != null }
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(result!!, 0, result!!.size, bounds)
    assertEquals(bounds.outWidth, bounds.outHeight)
  }

  private fun detectionDetails() {
    val result =
      ModelResultData(
        modelId = "d",
        modelName = "Damage",
        provider = "SERVER",
        task = "DETECTION",
        boxes = listOf(ResultBox("Crack", .9, 0.1, 0.2, 0.6, 0.7)),
      )
    val bytes = image()
    compose.setContent { MaterialTheme { AnalysisDetailsDialog(result, bytes, dismiss = {}) } }
    compose.onNodeWithText("100 × 60 px").assertExists()
    compose.onNodeWithText("Boxes").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Crack", substring = true).assertExists()
    compose.onNodeWithContentDescription("Close").assertIsDisplayed()
  }

  @Test
  fun portraitDetailsReuseBoundingBoxMetrics() {
    detectionDetails()
  }

  @Test
  @Config(qualifiers = "en-rUS-w900dp-h420dp-land-mdpi")
  fun landscapeDetailsReuseBoundingBoxMetrics() {
    detectionDetails()
  }

  @Test
  fun classificationDetailsShowScoresWithoutRequiringBoxes() {
    val result =
      ModelResultData(
        modelId = "c",
        modelName = "Surface",
        provider = "LOCAL",
        task = "CLASSIFICATION",
        scores = listOf(ResultScore("Asphalt", .8)),
      )
    compose.setContent { MaterialTheme { AnalysisDetailsDialog(result, null, dismiss = {}) } }
    compose.onNodeWithText("Image classification").assertExists()
    compose.onNodeWithText("Asphalt").assertExists()
    compose.onNodeWithText("Boxes").assertDoesNotExist()
  }

  @Test
  fun leavingAnalysisClearsImageBytesAndTextInsteadOfRetainingPrivateInputs() {
    val app = compose.activity.application as MyApplication
    if (FirebaseApp.getApps(app).isEmpty())
      FirebaseApp.initializeApp(
        app,
        FirebaseOptions.Builder()
          .setApplicationId("1:123:android:test")
          .setApiKey("test-key")
          .setProjectId("local-test")
          .build(),
      )
    var shown by mutableStateOf(true)
    compose.setContent {
      MaterialTheme {
        if (shown)
          AnalysisWorkspace(
            ModelChoice(
              "road-server",
              "Road damage",
              "DETECTION",
              server = RoadDamageServerAdapter,
            ),
            {},
          )
      }
    }
    val vm =
      androidx.lifecycle
        .ViewModelProvider(
          compose.activity,
          androidx.lifecycle.ViewModelProvider.NewInstanceFactory(),
        )["analysis-true-road-server", AnalysisViewModel::class.java]
    val bytes = image()
    compose.runOnIdle {
      vm.image(bytes)
      vm.text("Private input")
      shown = false
    }
    compose.waitForIdle()
    assertNull(vm.image)
    assertEquals("", vm.text)
    assertTrue(bytes.all { it == 0.toByte() })
  }
}
