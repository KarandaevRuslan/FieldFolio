package com.karandaev.fieldfolio.core.image.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivateImageCropTest {
  @Test
  fun cropUsesOriginalNormalizedRectangleWithoutCreatingAPlaintextFile() {
    val original = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
    val source =
      ByteArrayOutputStream()
        .also { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
        .toByteArray()
    original.recycle()
    val cropped = ImageCropper().cropBytes(source, NormalizedCropBox(.2, .1, .7, .6))
    val image = BitmapFactory.decodeByteArray(cropped, 0, cropped.size)
    assertEquals(50, image.width)
    assertEquals(30, image.height)
    image.recycle()
    val unchanged = BitmapFactory.decodeByteArray(source, 0, source.size)
    assertEquals(100, unchanged.width)
    unchanged.recycle()
  }
}
