package com.karandaev.fieldfolio.core.ui.components.dialog

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R

/**
 * Shows a confirmation dialog only when it is requested.
 *
 * @param showDialog Whether the dialog should be visible.
 * @param title Dialog title.
 * @param message Dialog message.
 * @param confirmText Confirm button text.
 * @param dismissText Dismiss button text.
 * @param isConfirming Whether confirm action is currently running.
 * @param onDismiss Called when the dialog should be dismissed.
 * @param onConfirm Called when the user confirms the action.
 */
@Composable
fun ConfirmationDialogHost(
  showDialog: Boolean,
  title: String,
  message: String,
  confirmText: String,
  dismissText: String = stringResource(R.string.ui_cancel),
  isConfirming: Boolean = false,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  if (!showDialog) return

  SafeAlertDialog(
    onDismissRequest = {
      if (!isConfirming) {
        onDismiss()
      }
    },
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = {
      TextButton(enabled = !isConfirming, onClick = onConfirm) { Text(confirmText) }
    },
    dismissButton = {
      TextButton(enabled = !isConfirming, onClick = onDismiss) { Text(dismissText) }
    },
  )
}
