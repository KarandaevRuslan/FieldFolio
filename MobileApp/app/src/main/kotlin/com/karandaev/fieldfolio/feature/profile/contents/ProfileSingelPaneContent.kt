package com.karandaev.fieldfolio.feature.profile.contents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.auth.model.UserProfile
import com.karandaev.fieldfolio.core.ui.scroll.AppLazyColumn as LazyColumn
import com.karandaev.fieldfolio.feature.profile.components.actions.ProfileLogoutButton
import com.karandaev.fieldfolio.feature.profile.components.cards.ProfileCard
import com.karandaev.fieldfolio.feature.profile.components.cards.ProfileEmptyCard
import com.karandaev.fieldfolio.feature.profile.components.identity.ProfileIdentityCard
import com.karandaev.fieldfolio.feature.profile.components.security.ProfileSecurityCard

@Composable
internal fun ProfileSinglePaneContent(
  profile: UserProfile?,
  isLoading: Boolean,
  onAvatarClick: () -> Unit,
  onDisplayNameClick: () -> Unit,
  onChangePasswordClick: () -> Unit,
  onDeleteAccountClick: () -> Unit,
  onLogoutClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      ProfileIdentityCard(
        profile = profile,
        enabled = !isLoading && profile != null,
        onAvatarClick = onAvatarClick,
      )
    }

    item {
      if (profile != null) {
        ProfileCard(
          profile = profile,
          enabled = !isLoading,
          onDisplayNameEditClick = onDisplayNameClick,
        )
      } else {
        ProfileEmptyCard()
      }
    }

    if (profile != null) {
      item {
        ProfileSecurityCard(
          profile = profile,
          enabled = !isLoading,
          onChangePasswordClick = onChangePasswordClick,
          onDeleteAccountClick = onDeleteAccountClick,
        )
      }
    }

    item { ProfileLogoutButton(isLoading = isLoading, onClick = onLogoutClick) }
  }
}
