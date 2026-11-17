package com.karandaev.fieldfolio.core.image.compression

import android.graphics.BitmapFactory
import com.karandaev.fieldfolio.core.image.bitmap.letterboxToSquare
import com.karandaev.fieldfolio.core.image.bitmap.resizeLargestSideTo
import com.karandaev.fieldfolio.core.image.bitmap.saveAsJpegTo
import com.karandaev.fieldfolio.core.image.cache.CompressedImageFilePrefix
import java.io.File

private const val DefaultCompressionQuality = 85
private const val DefaultTargetImageSizePx = 768
private const val CompressedFileExtension = "jpg"

/**
 * Compresses image files into JPEG format.
 *
 * The output image is resized to [DefaultTargetImageSizePx] target size while preserving the
 * original aspect ratio.
 *
 * When [isLetterboxEnabled] is `false`, the largest image side is resized to [targetImageSizePx].
 *
 * When [isLetterboxEnabled] is `true`, the output image is created with exactly [targetImageSizePx]
 * x [targetImageSizePx] pixels using black letterbox padding.
 *
 * The output file is always created as JPEG, regardless of the source image format. If the source
 * file cannot be decoded as an image, compression is skipped.
 *
 * @param quality JPEG compression quality from 0 to 100.
 * @param targetImageSizePx Target image size in pixels.
 * @param isLetterboxEnabled Whether to create an exact square image with letterbox padding.
 */
class JpegImageFileCompressor(
  private val quality: Int = DefaultCompressionQuality,
  private val targetImageSizePx: Int = DefaultTargetImageSizePx,
  private val isLetterboxEnabled: Boolean = false,
) : FileCompressor {

  /**
   * Compresses the image located at the provided path into a resized JPEG file.
   *
   * The compressed file is created in the same parent directory as the source file. If the source
   * file cannot be decoded as an image, this method returns `null`.
   *
   * @param sourcePath Path to the source image file.
   * @return Resized compressed JPEG file, or `null` when the source file cannot be decoded.
   * @throws IllegalArgumentException if quality is outside the 0..100 range.
   * @throws IllegalArgumentException if [targetImageSizePx] is not positive.
   * @throws IllegalArgumentException if the source file does not exist.
   * @throws IllegalStateException if the image cannot be compressed.
   */
  override fun compress(sourcePath: String): File? {
    require(quality in 0..100) { "JPEG compression quality must be between 0 and 100." }

    require(targetImageSizePx > 0) { "Target image size must be greater than 0." }

    val sourceFile = File(sourcePath)

    require(sourceFile.exists()) { "Source file does not exist." }

    val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return null

    val resizedBitmap =
      if (isLetterboxEnabled) {
        bitmap.letterboxToSquare(targetSizePx = targetImageSizePx)
      } else {
        bitmap.resizeLargestSideTo(targetSizePx = targetImageSizePx)
      }

    val compressedFile =
      File(
        sourceFile.parentFile,
        "$CompressedImageFilePrefix${sourceFile.nameWithoutExtension}.$CompressedFileExtension",
      )

    resizedBitmap.saveAsJpegTo(outputFile = compressedFile, quality = quality)

    if (resizedBitmap !== bitmap) {
      bitmap.recycle()
    }

    resizedBitmap.recycle()

    return compressedFile
  }
}
