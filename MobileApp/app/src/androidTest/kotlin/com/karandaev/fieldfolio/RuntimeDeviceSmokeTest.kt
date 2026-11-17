package com.karandaev.fieldfolio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.karandaev.fieldfolio.core.di.AppServices
import com.karandaev.fieldfolio.core.document.runtime.DocumentWebClient
import com.karandaev.fieldfolio.core.document.runtime.createDocumentWebView
import com.karandaev.fieldfolio.core.document.runtime.documentHost
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Device-only smoke checks; compilation does not imply that a device/emulator run occurred. */
@RunWith(AndroidJUnit4::class)
class RuntimeDeviceSmokeTest {
  @Test
  fun applicationGraphAndBuiltInModelArePresent() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    assertTrue(context is AppServices)
    assertEquals("com.karandaev.fieldfolio", context.packageName)
    context.assets.open("models/road_damage_yolo_int8.tflite").use { input ->
      val header = ByteArray(8)
      assertEquals(8, input.read(header))
      assertEquals("TFL3", header.copyOfRange(4, 8).toString(Charsets.US_ASCII))
    }
  }

  @Test
  fun documentWebViewDisablesDirectFileAndPersistentStorageAccess() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val flags = AtomicReference<List<Boolean>>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val scope = UUID.randomUUID().toString()
      val client =
        DocumentWebClient(
          context,
          documentHost("device-test", "document", scope),
          scope,
          null,
          {},
          {},
        )
      val view = createDocumentWebView(context, client)
      flags.set(
        listOf(
          view.settings.allowFileAccess,
          view.settings.allowContentAccess,
          view.settings.domStorageEnabled,
        )
      )
      view.destroy()
      client.close()
    }
    assertEquals(listOf(false, false, false), flags.get())
  }
}
