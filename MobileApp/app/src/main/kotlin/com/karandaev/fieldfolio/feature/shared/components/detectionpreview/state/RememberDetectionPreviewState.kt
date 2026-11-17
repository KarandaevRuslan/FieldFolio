package com.karandaev.fieldfolio.feature.shared.components.detectionpreview.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.karandaev.fieldfolio.core.domain.model.detection.Detection
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.defaults.DetectionPreviewFallbackAspectRatio
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.image.readImageSize
import java.io.File

/**
 * Creates detection preview state from already prepared display data.
 *
 * This function does not resolve report-specific paths and does not know anything about inspection
 * reports. It only prepares image metadata for rendering.
 *
 * @param detections Detections to display.
 * @param imagePath Optional local image path.
 */
@Composable
internal fun rememberDetectionPreviewState(
  detections: List<Detection>,
  imagePath: String?,
): DetectionPreviewState {
  val imageFile =
    remember(imagePath) {
      imagePath
        ?.takeIf { path -> path.isNotBlank() }
        ?.let { path -> File(path) }
        ?.takeIf { file ->
          (file.exists() && file.isFile) ||
            com.karandaev.fieldfolio.core.library.LibraryImages.data(file.absolutePath) != null
        }
    }

  val imageSize = remember(imageFile?.absolutePath) { imageFile?.readImageSize() }

  val imageAspectRatio =
    imageSize?.let { size -> size.width.toFloat() / size.height.toFloat() }
      ?: DetectionPreviewFallbackAspectRatio

  return DetectionPreviewState(
    imageFile = imageFile,
    imageSize = imageSize,
    imageAspectRatio = imageAspectRatio,
    detections = detections,
  )
}
