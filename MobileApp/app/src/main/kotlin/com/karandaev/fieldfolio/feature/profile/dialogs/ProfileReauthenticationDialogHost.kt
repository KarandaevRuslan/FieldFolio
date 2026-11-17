package com.karandaev.fieldfolio.feature.profile.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.ui.components.dialog.MyDialogHost
import com.karandaev.fieldfolio.core.ui.components.notification.MiniUiMessageCard
import com.karandaev.fieldfolio.core.ui.message.UiMessage
import com.karandaev.fieldfolio.feature.shared.components.fields.MyPasswordField

/** Dialog for confirming current password before sensitive account operations. */
@Composable
internal fun ProfileReauthenticationDialogHost(
  showDialog: Boolean,
  originalPassword: String,
  message: UiMessage?,
  enabled: Boolean,
  onOriginalPasswordChange: (String) -> Unit,
  onOriginalPasswordClear: () -> Unit,
  onClearMessageClick: () -> Unit,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  MyDialogHost(
    showDialog = showDialog,
    title = stringResource(R.string.profile_reauthentication_dialog_title),
    icon = Icons.Default.Lock,
    iconTint = MaterialTheme.colorScheme.primary,
    confirmButtonText = stringResource(R.string.profile_dialog_confirm),
    dismissButtonText = stringResource(R.string.profile_dialog_cancel),
    confirmButtonEnabled = enabled,
    dismissButtonEnabled = enabled,
    onDismiss = onDismiss,
    onConfirm = onConfirm,
  ) {
    MiniUiMessageCard(message = message, onClearMessageClick = onClearMessageClick)

    MyPasswordField(
      password = originalPassword,
      enabled = enabled,
      onPasswordChange = onOriginalPasswordChange,
      onPasswordClear = onOriginalPasswordClear,
      label = stringResource(R.string.profile_reauthentication_dialog_current_password),
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
