package com.karandaev.fieldfolio.feature.profile.dialogs.avatar

import androidx.compose.runtime.Composable
import com.karandaev.fieldfolio.core.auth.model.UserProfile
import com.karandaev.fieldfolio.core.image.crop.NormalizedCropBox
import com.karandaev.fieldfolio.core.image.sourcefile.ImageSourceFile

/** Selects avatar dialog layout by current orientation. */
@Composable
internal fun ProfileAvatarAdaptiveContent(
  isLandscape: Boolean,
  profile: UserProfile?,
  avatarImageSource: ImageSourceFile?,
  hasSelectedImage: Boolean,
  enabled: Boolean,
  onChooseImageClick: () -> Unit,
  onCropBoxChange: (NormalizedCropBox) -> Unit,
  onClearSelectedImageClick: () -> Unit,
  onResetAvatarClick: () -> Unit,
) {
  if (isLandscape) {
    ProfileAvatarLandscapeContent(
      profile = profile,
      avatarImageSource = avatarImageSource,
      hasSelectedImage = hasSelectedImage,
      enabled = enabled,
      onChooseImageClick = onChooseImageClick,
      onCropBoxChange = onCropBoxChange,
      onClearSelectedImageClick = onClearSelectedImageClick,
      onResetAvatarClick = onResetAvatarClick,
    )
  } else {
    ProfileAvatarPortraitContent(
      profile = profile,
      avatarImageSource = avatarImageSource,
      hasSelectedImage = hasSelectedImage,
      enabled = enabled,
      onChooseImageClick = onChooseImageClick,
      onCropBoxChange = onCropBoxChange,
      onClearSelectedImageClick = onClearSelectedImageClick,
      onResetAvatarClick = onResetAvatarClick,
    )
  }
}
