package com.karandaev.fieldfolio.core.models

import android.graphics.*
import android.media.ExifInterface

fun decodeModelImage(bytes: ByteArray, maxDimension: Int = 4096): Bitmap {
  val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, info)
  require(
    info.outWidth > 0 &&
      info.outHeight > 0 &&
      info.outWidth.toLong() * info.outHeight <= 120_000_000
  ) {
    "Invalid or oversized image"
  }
  val options = BitmapFactory.Options().apply { inSampleSize = 1 }
  while (maxOf(info.outWidth, info.outHeight) / options.inSampleSize > maxDimension) options
    .inSampleSize *= 2
  val image = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
  val orientation =
    runCatching {
        ExifInterface(bytes.inputStream())
          .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
      }
      .getOrDefault(1)
  val matrix =
    Matrix().apply {
      when (orientation) {
        2 -> setScale(-1f, 1f)
        3 -> setRotate(180f)
        4 -> setScale(1f, -1f)
        5 -> {
          setRotate(90f)
          postScale(-1f, 1f)
        }
        6 -> setRotate(90f)
        7 -> {
          setRotate(270f)
          postScale(-1f, 1f)
        }
        8 -> setRotate(270f)
      }
    }
  if (matrix.isIdentity) return image
  return Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true).also {
    if (it !== image) image.recycle()
  }
}
