package com.karandaev.fieldfolio.core.image.preprocessing

import android.graphics.Bitmap
import com.karandaev.fieldfolio.core.image.bitmap.saveAsJpegTo
import com.karandaev.fieldfolio.core.image.cache.CompressedImageFilePrefix
import java.io.File

private const val ProcessedFileExtension = "jpg"

internal fun Bitmap.saveAsJpegNextTo(
  sourceFile: File,
  targetWidth: Int,
  targetHeight: Int,
  jpegQuality: Int,
): File {
  val outputFile =
    File(
      sourceFile.parentFile,
      "$CompressedImageFilePrefix${sourceFile.nameWithoutExtension}_${targetWidth}x${targetHeight}.$ProcessedFileExtension",
    )

  saveAsJpegTo(outputFile = outputFile, quality = jpegQuality)

  return outputFile
}
