package com.karandaev.fieldfolio.feature.auth.components.buttons

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.feature.auth.components.buttons.base.AuthTextButton

/** Button that allows the user to continue without signing in. */
@Composable
internal fun ContinueAsGuestButton(
  isLoading: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AuthTextButton(
    text = stringResource(R.string.ui_continue_as_guest),
    isLoading = isLoading,
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
  )
}
