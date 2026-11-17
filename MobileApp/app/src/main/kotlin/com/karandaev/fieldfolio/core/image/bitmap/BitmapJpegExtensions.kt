package com.karandaev.fieldfolio.core.image.bitmap

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/** Saves this bitmap as JPEG to [outputFile]. */
internal fun Bitmap.saveAsJpegTo(outputFile: File, quality: Int) {
  FileOutputStream(outputFile).use { output ->
    val isCompressed = compress(Bitmap.CompressFormat.JPEG, quality, output)

    check(isCompressed) { "Could not compress image file." }
  }
}
