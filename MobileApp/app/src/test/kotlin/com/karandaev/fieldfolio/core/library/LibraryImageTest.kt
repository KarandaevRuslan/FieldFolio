package com.karandaev.fieldfolio.core.library

import android.app.Application
import android.graphics.Bitmap
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.image.readImageSize
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryImageTest {
  @Test
  fun sessionImagesRenderAndExposeDimensionsWithoutCreatingPlaintextFiles() {
    val image = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
    val out = ByteArrayOutputStream()
    image.compress(Bitmap.CompressFormat.PNG, 100, out)
    image.recycle()
    LibraryImages.put(42, out.toByteArray(), "detection.png")
    val path = LibraryImages.path(42)!!
    assertFalse(File(path).exists())
    assertEquals(32, File(path).readImageSize()!!.width)
    val decoded = LibraryImages.decode(path)!!
    assertEquals(24, decoded.height)
    decoded.recycle()
    LibraryImages.clear()
    assertNull(LibraryImages.path(42))
    assertNull(LibraryImages.data(path))
    assertNull(LibraryImages.decode(path))
  }
}
