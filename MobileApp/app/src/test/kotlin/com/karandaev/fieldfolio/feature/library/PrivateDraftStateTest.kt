package com.karandaev.fieldfolio.feature.library

import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.feature.document.editor.TextPropertiesDialog
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MyApplication::class)
class PrivateDraftStateTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun savedContains(privateMode: Boolean): Boolean {
    compose.setContent {
      MaterialTheme {
        TextPropertiesDialog(
          DocumentNode(
            id = "private-text",
            name = "PRIVATE_FIXTURE_937281",
            kind = BlockKind.TEXT,
            body = "PRIVATE_FIXTURE_937281",
          ),
          emptyMap(),
          {},
          save = { _, _ -> },
        )
      }
    }
    val bundle = Bundle()
    compose.runOnIdle {
      org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(
        compose.activity,
        "onSaveInstanceState",
        org.robolectric.util.ReflectionHelpers.ClassParameter.from(Bundle::class.java, bundle),
      )
    }
    val parcel = Parcel.obtain()
    return try {
      parcel.writeBundle(bundle)
      val bytes = parcel.marshall()
      listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE).any { charset ->
        val secret = "PRIVATE_FIXTURE_937281".toByteArray(charset)
        bytes.asList().windowed(secret.size).any { it.toByteArray().contentEquals(secret) }
      }
    } finally {
      parcel.recycle()
    }
  }

  @Test
  fun privateDraftIsNeverWrittenToTheAndroidSavedStateBundle() {
    assertFalse(savedContains(true))
  }

  @Test
  fun allCurrentReportDraftsKeepTextOutOfTheAndroidSavedStateBundle() {
    assertFalse(savedContains(false))
  }
}
