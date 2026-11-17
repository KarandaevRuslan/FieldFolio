package com.karandaev.fieldfolio.core.image.preprocessing

import android.graphics.BitmapFactory
import com.karandaev.fieldfolio.core.image.bitmap.letterboxTo
import com.karandaev.fieldfolio.core.image.validation.DefaultImageValidationProvider
import com.karandaev.fieldfolio.core.image.validation.ImageValidationProvider
import java.io.File

private const val DefaultJpegQuality = 85

/**
 * Prepares images for TFLite inference.
 *
 * Unlike upload compression, this preprocessor always creates an image with exactly [targetWidth] x
 * [targetHeight] pixels. Small images are resized too.
 *
 * The image is resized with letterboxing, so aspect ratio is preserved.
 */
class TfliteImagePreprocessor(
  private val imageValidationProvider: ImageValidationProvider = DefaultImageValidationProvider(),
  private val jpegQuality: Int = DefaultJpegQuality,
) {

  /** Validates, resizes, compresses, and converts source image to TFLite input. */
  fun preprocess(
    sourcePath: String,
    targetWidth: Int,
    targetHeight: Int,
    isQuantized: Boolean,
  ): TfliteImageInput {
    require(sourcePath.isNotBlank()) { "Source path must not be blank." }

    require(targetWidth > 0) { "Target width must be greater than 0." }

    require(targetHeight > 0) { "Target height must be greater than 0." }

    require(jpegQuality in 0..100) { "JPEG quality must be between 0 and 100." }

    val sourceFile = File(sourcePath)

    require(sourceFile.exists()) { "Source file does not exist." }

    require(sourceFile.isFile) { "Source path does not point to a file." }

    imageValidationProvider.validate(sourceFile.absolutePath)

    val sourceBitmap =
      BitmapFactory.decodeFile(sourceFile.absolutePath)
        ?: error("Source file cannot be decoded as an image.")

    val preparedBitmap =
      sourceBitmap.letterboxTo(targetWidth = targetWidth, targetHeight = targetHeight)

    val processedFile =
      preparedBitmap.saveAsJpegNextTo(
        sourceFile = sourceFile,
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        jpegQuality = jpegQuality,
      )

    imageValidationProvider.validate(processedFile.absolutePath)

    val inputBuffer = preparedBitmap.toModelInputBuffer(isQuantized = isQuantized)

    val transform =
      LetterboxTransform(
        originalWidth = sourceBitmap.width,
        originalHeight = sourceBitmap.height,
        modelWidth = targetWidth,
        modelHeight = targetHeight,
        scale =
          minOf(
            targetWidth.toFloat() / sourceBitmap.width.toFloat(),
            targetHeight.toFloat() / sourceBitmap.height.toFloat(),
          ),
      )

    if (preparedBitmap !== sourceBitmap) {
      preparedBitmap.recycle()
    }

    sourceBitmap.recycle()

    return TfliteImageInput(
      inputBuffer = inputBuffer,
      processedFile = processedFile,
      transform = transform,
    )
  }
}
