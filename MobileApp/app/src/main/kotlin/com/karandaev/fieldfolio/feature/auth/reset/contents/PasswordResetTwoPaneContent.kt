package com.karandaev.fieldfolio.feature.auth.reset.contents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.ui.scroll.AppLazyColumn as LazyColumn
import com.karandaev.fieldfolio.feature.auth.components.navigation.AuthHeader
import com.karandaev.fieldfolio.feature.auth.reset.components.PasswordResetFormList
import com.karandaev.fieldfolio.feature.auth.state.model.AuthUiState

@Composable
internal fun PasswordResetTwoPaneContent(
  state: AuthUiState,
  onEmailChange: (String) -> Unit,
  onEmailClear: () -> Unit,
  onResetClick: () -> Unit,
  onLoginClick: () -> Unit,
  onContinueAsGuestClick: () -> Unit,
  onClearMessageClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    LazyColumn(
      modifier = Modifier.weight(0.9f).fillMaxHeight(),
      contentPadding = PaddingValues(start = 16.dp, top = 16.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item { AuthHeader(title = stringResource(id = R.string.destination_auth_password_reset)) }

      item {
        Text(
          text = stringResource(id = R.string.auth_password_reset_description),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    PasswordResetFormList(
      state = state,
      contentPadding = PaddingValues(top = 16.dp, end = 16.dp, bottom = 16.dp),
      onEmailChange = onEmailChange,
      onEmailClear = onEmailClear,
      onResetClick = onResetClick,
      onLoginClick = onLoginClick,
      onContinueAsGuestClick = onContinueAsGuestClick,
      onClearMessageClick = onClearMessageClick,
      modifier = Modifier.weight(1.1f).fillMaxHeight(),
    )
  }
}
