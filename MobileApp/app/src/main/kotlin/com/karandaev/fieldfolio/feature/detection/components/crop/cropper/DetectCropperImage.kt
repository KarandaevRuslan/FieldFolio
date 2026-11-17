package com.karandaev.fieldfolio.feature.detection.components.crop.cropper

import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.*

/**
 * Decodes image bitmap for cropper and remembers it by image path.
 *
 * @param imagePath Original image file path.
 */
@Composable
internal fun rememberDetectCropperImageBitmap(
  imagePath: String
): androidx.compose.runtime.State<Result<ImageBitmap>?> =
  com.karandaev.fieldfolio.core.image.rememberFileImagePreview(imagePath)

/** Converts [ImageBitmap] dimensions to [IntSize]. */
internal fun ImageBitmap.toIntSize(): IntSize {
  return IntSize(width = width, height = height)
}
