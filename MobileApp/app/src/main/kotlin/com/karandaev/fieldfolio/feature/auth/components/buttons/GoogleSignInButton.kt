package com.karandaev.fieldfolio.feature.auth.components.buttons

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.feature.auth.components.buttons.base.AuthOutlinedButton

/** Google sign-in button. */
@Composable
internal fun GoogleSignInButton(
  isLoading: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AuthOutlinedButton(
    text = stringResource(R.string.ui_continue_with_google),
    isLoading = isLoading,
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
  )
}
