package com.karandaev.fieldfolio.feature.shared.components.detectionpreview.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.placeholder.DetectionPreviewPlaceholder
import java.io.File

/**
 * Displays report image or placeholder when image is missing.
 *
 * @param imageFile Local report image file.
 * @param imageAspectRatio Aspect ratio used to fit placeholder.
 * @param containerSize Preview container size.
 * @param modifier Modifier applied to image area.
 */
@Composable
internal fun DetectionPreviewImage(
  imageFile: File?,
  imageAspectRatio: Float,
  containerSize: IntSize,
  modifier: Modifier = Modifier,
) {
  if (imageFile != null) {
    AsyncImage(
      model =
        coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
          .data(
            com.karandaev.fieldfolio.core.library.LibraryImages.data(imageFile.absolutePath)
              ?: imageFile
          )
          .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
          .diskCachePolicy(coil.request.CachePolicy.DISABLED)
          .build(),
      contentDescription =
        stringResource(R.string.inspection_report_detection_preview_image_content_description),
      modifier = modifier.fillMaxSize(),
      contentScale = ContentScale.Fit,
    )
  } else {
    DetectionPreviewPlaceholder(
      containerSize = containerSize,
      imageAspectRatio = imageAspectRatio,
      modifier = modifier.fillMaxSize(),
    )
  }
}
