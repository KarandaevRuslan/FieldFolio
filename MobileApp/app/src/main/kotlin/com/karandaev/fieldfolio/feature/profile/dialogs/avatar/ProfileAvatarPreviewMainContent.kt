package com.karandaev.fieldfolio.feature.profile.dialogs.avatar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.auth.model.UserProfile

/** Current avatar preview with choose hint. */
@Composable
internal fun ProfileAvatarPreviewMainContent(profile: UserProfile?, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(ProfileAvatarContentSpacing),
  ) {
    ProfileAvatarPreview(profile = profile)

    Text(
      text = stringResource(R.string.profile_avatar_dialog_choose_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
