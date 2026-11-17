package com.karandaev.fieldfolio.feature.auth.reset.contents

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.karandaev.fieldfolio.core.ui.adaptive.AdaptiveLayoutType
import com.karandaev.fieldfolio.core.ui.adaptive.rememberAdaptiveLayoutType
import com.karandaev.fieldfolio.feature.auth.state.model.AuthUiState

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun PasswordResetContent(
  state: AuthUiState,
  onEmailChange: (String) -> Unit,
  onEmailClear: () -> Unit,
  onResetClick: () -> Unit,
  onLoginClick: () -> Unit,
  onContinueAsGuestClick: () -> Unit,
  onClearMessageClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val layoutType = rememberAdaptiveLayoutType(maxWidth = maxWidth, maxHeight = maxHeight)

    when (layoutType) {
      AdaptiveLayoutType.SinglePane -> {
        PasswordResetSinglePaneContent(
          state = state,
          onEmailChange = onEmailChange,
          onEmailClear = onEmailClear,
          onResetClick = onResetClick,
          onLoginClick = onLoginClick,
          onContinueAsGuestClick = onContinueAsGuestClick,
          onClearMessageClick = onClearMessageClick,
          modifier = Modifier.fillMaxSize(),
        )
      }

      AdaptiveLayoutType.TwoPane -> {
        PasswordResetTwoPaneContent(
          state = state,
          onEmailChange = onEmailChange,
          onEmailClear = onEmailClear,
          onResetClick = onResetClick,
          onLoginClick = onLoginClick,
          onContinueAsGuestClick = onContinueAsGuestClick,
          onClearMessageClick = onClearMessageClick,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }
}
