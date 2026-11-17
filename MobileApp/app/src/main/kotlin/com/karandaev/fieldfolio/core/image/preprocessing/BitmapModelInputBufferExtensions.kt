package com.karandaev.fieldfolio.core.image.preprocessing

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val RgbChannelCount = 3
private const val FloatBytes = 4

internal fun Bitmap.toModelInputBuffer(isQuantized: Boolean): ByteBuffer {
  val bytesPerChannel =
    if (isQuantized) {
      1
    } else {
      FloatBytes
    }

  val buffer =
    ByteBuffer.allocateDirect(width * height * RgbChannelCount * bytesPerChannel)
      .order(ByteOrder.nativeOrder())

  val pixels = IntArray(width * height)

  getPixels(pixels, 0, width, 0, 0, width, height)

  pixels.forEach { pixel ->
    val red = Color.red(pixel)
    val green = Color.green(pixel)
    val blue = Color.blue(pixel)

    if (isQuantized) {
      buffer.put(red.toByte())
      buffer.put(green.toByte())
      buffer.put(blue.toByte())
    } else {
      buffer.putFloat(red / 255f)
      buffer.putFloat(green / 255f)
      buffer.putFloat(blue / 255f)
    }
  }

  buffer.rewind()

  return buffer
}
