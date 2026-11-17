package com.karandaev.fieldfolio.feature.settings.components.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.auth.model.UserProfile
import com.karandaev.fieldfolio.core.auth.model.effectivePhotoUrl
import com.karandaev.fieldfolio.feature.settings.components.account.AccountAvatar

/**
 * Settings section that displays current account summary.
 *
 * If [profile] exists, the card opens profile screen. If [profile] is null, the card opens sign-in
 * screen.
 *
 * @param profile Current user profile, or null for guest/signed-out access.
 * @param onProfileClick Called when profile card is clicked.
 * @param onSignInClick Called when sign-in card is clicked.
 */
@Composable
internal fun SettingsAccountSection(
  profile: UserProfile?,
  onProfileClick: () -> Unit,
  onSignInClick: () -> Unit,
) {
  val isSignedIn = profile != null

  SettingsSection(title = stringResource(R.string.ui_account)) {
    Row(
      modifier =
        Modifier.clickable(
            onClick =
              if (isSignedIn) {
                onProfileClick
              } else {
                onSignInClick
              }
          )
          .padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AccountAvatar(photoUrl = profile?.effectivePhotoUrl, isSignedIn = isSignedIn)

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text =
            if (isSignedIn) {
              profile?.displayName
                ?: profile?.email
                ?: stringResource(R.string.message_signed_in_user)
            } else {
              stringResource(R.string.ui_sign_in)
            },
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
          text =
            if (isSignedIn) {
              profile?.email ?: stringResource(R.string.message_open_profile)
            } else {
              stringResource(R.string.message_use_an_account_to_sync_your_profile)
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
