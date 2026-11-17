package com.karandaev.fieldfolio.core.ui.components.fields

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.ui.clipboard.copyPathToClipboard
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.VfsFormDialog
import com.karandaev.fieldfolio.core.ui.state.rememberSession

@Composable
internal fun VfsPathDialog(
  initial: String,
  dismiss: () -> Unit,
  busy: Boolean = false,
  tag: String = "vfs-path",
  error: String? = null,
  open: (String) -> Unit,
) {
  var path by rememberSession { mutableStateOf(initial) }
  val context = LocalContext.current
  VfsFormDialog(R.string.library_path, dismiss) {
    OutlinedTextField(
      path,
      { path = it.take(12100) },
      Modifier.fillMaxWidth().testTag(tag),
      singleLine = true,
      label = { Text(stringResource(R.string.library_path)) },
      isError = error != null,
      supportingText = { if (error != null) Text(error) },
    )
    Row(Modifier.fillMaxWidth()) {
      HintIconButton(
        { open(path) },
        enabled = !busy,
        tooltip = stringResource(R.string.library_open),
      ) {
        Icon(Icons.Default.ArrowForward, stringResource(R.string.library_open))
      }
      HintIconButton(
        { copyPathToClipboard(context, path) },
        tooltip = stringResource(R.string.library_copy_path),
      ) {
        Icon(Icons.Default.ContentCopy, stringResource(R.string.library_copy_path))
      }
    }
  }
}
