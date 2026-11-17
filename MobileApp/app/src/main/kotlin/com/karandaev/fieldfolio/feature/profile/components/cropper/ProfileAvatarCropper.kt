package com.karandaev.fieldfolio.feature.profile.components.cropper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.image.crop.NormalizedCropBox
import com.karandaev.fieldfolio.core.image.sourcefile.ImageSourceFile

/**
 * Circular avatar cropper controlled with drag and pinch gestures.
 *
 * @param imageSource Selected avatar image source.
 * @param enabled Whether crop gestures are enabled.
 * @param onCropBoxChange Called when crop box changes.
 * @param modifier Modifier applied to the root.
 * @param cropperSize Visible cropper viewport size.
 * @param showGestureHint Whether gesture hint should be shown below the cropper.
 */
@Composable
internal fun ProfileAvatarCropper(
  imageSource: ImageSourceFile,
  enabled: Boolean,
  onCropBoxChange: (NormalizedCropBox) -> Unit,
  modifier: Modifier = Modifier,
  cropperSize: Dp = AvatarCropperSize,
  showGestureHint: Boolean = true,
) {
  val imagePath = imageSource.originalSourcePath
  val result by com.karandaev.fieldfolio.core.image.rememberFileImagePreview(imagePath)
  if (result == null) {
    androidx.compose.material3.CircularProgressIndicator()
    return
  }
  val loadedImage = result?.getOrNull()
  if (loadedImage == null) {
    AvatarCropperLoadError()
    return
  }

  val viewportSizePx = with(LocalDensity.current) { cropperSize.toPx() }

  val cropperState =
    remember(imagePath, viewportSizePx) {
      AvatarCropperState().apply {
        restoreFromCropBox(
          cropBox = imageSource.cropBox,
          imageWidth = loadedImage.width,
          imageHeight = loadedImage.height,
          viewportSize = viewportSizePx,
        )
      }
    }

  LaunchedEffect(imagePath, viewportSizePx, cropperState.scale, cropperState.offset) {
    onCropBoxChange(
      cropperState.calculateCropBox(
        imageWidth = loadedImage.width,
        imageHeight = loadedImage.height,
        viewportSize = viewportSizePx,
      )
    )
  }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(AvatarCropperContentSpacing),
  ) {
    ProfileAvatarCropperCanvas(
      imageBitmap = loadedImage,
      cropperState = cropperState,
      enabled = enabled,
      cropperSize = cropperSize,
    )

    if (showGestureHint) {
      Text(
        text = stringResource(R.string.profile_avatar_cropper_gesture_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Error text shown when selected avatar image cannot be decoded. */
@Composable
private fun AvatarCropperLoadError() {
  Text(
    text = stringResource(R.string.profile_avatar_dialog_image_load_error),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.error,
  )
}
