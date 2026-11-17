package com.karandaev.fieldfolio.core.document

import android.app.Application
import android.content.*
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import com.karandaev.fieldfolio.feature.models.pickedName
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OriginalImageNameTest {
  @Test
  fun originalProviderDisplayNameWinsOverNumericUriAndColumnOrder() = runBlocking {
    val provider =
      object : ContentProvider() {
        override fun onCreate() = true

        override fun query(
          uri: Uri,
          projection: Array<out String>?,
          selection: String?,
          args: Array<out String>?,
          sort: String?,
        ) =
          MatrixCursor(arrayOf("_id", OpenableColumns.DISPLAY_NAME)).apply {
            addRow(arrayOf(123, "Holiday panorama.png"))
          }

        override fun getType(uri: Uri) = "image/png"

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0

        override fun update(
          uri: Uri,
          values: ContentValues?,
          selection: String?,
          args: Array<out String>?,
        ) = 0
      }
    ShadowContentResolver.registerProviderInternal("original.test", provider)
    assertEquals(
      "Holiday panorama.png",
      pickedName(
        RuntimeEnvironment.getApplication(),
        Uri.parse("content://original.test/images/123.png"),
      ),
    )
  }
}
