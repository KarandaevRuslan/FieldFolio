@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAlertDialog
import com.karandaev.fieldfolio.core.ui.state.rememberSession

@Composable
internal fun LibraryNameDialog(
  title: Int,
  initial: String = "",
  dismiss: () -> Unit,
  submit: (String) -> Unit,
) {
  com.karandaev.fieldfolio.core.ui.components.fields.ResourceNameDialog(
    initial,
    dismiss,
    title = stringResource(title),
    submit = submit,
  )
}

@Composable
internal fun LibraryIdentityDialog(
  title: Int,
  dismiss: () -> Unit,
  submit: (String, String?) -> Unit,
) {
  com.karandaev.fieldfolio.core.ui.components.fields.ResourceIdentityDialog(
    "",
    null,
    dismiss,
    title = stringResource(title),
    valid = { runCatching { LibraryTree.name(it) }.isSuccess },
    submit = submit,
  )
}

@Composable
internal fun LibraryPasswordDialog(
  title: Int,
  newPassword: Boolean,
  busy: Boolean,
  dismiss: () -> Unit,
  submit: (CharArray) -> Unit,
) {
  var value by rememberSession { mutableStateOf("") }
  var repeat by rememberSession { mutableStateOf("") }
  val close =
    rememberUnsavedClose(value.isNotEmpty() || repeat.isNotEmpty()) {
      if (!busy) {
        value = ""
        repeat = ""
        dismiss()
      }
    }
  val valid =
    !busy &&
      value.isNotEmpty() &&
      (!newPassword || LibraryPasswordPolicy.accepts(value) && value == repeat)
  AdaptiveFormDialog(
    stringResource(title),
    close,
    properties =
      DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnClickOutside = false,
        securePolicy = SecureFlagPolicy.SecureOn,
      ),
    actions = {
      HintIconButton(
        {
          val password = value.toCharArray()
          value = ""
          repeat = ""
          submit(password)
        },
        enabled = valid,
        tooltip = stringResource(R.string.library_confirm),
      ) {
        Icon(Icons.Default.Check, stringResource(R.string.library_confirm))
      }
    },
    left = {
      LibraryPasswordField(value, { value = it })
      if (newPassword)
        LibraryPasswordField(repeat, { repeat = it }, R.string.library_password_repeat)
      if (newPassword && repeat.isNotEmpty() && value != repeat)
        Text(
          stringResource(R.string.finish_password_mismatch),
          color = MaterialTheme.colorScheme.error,
        )
      if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    },
    right = {
      if (newPassword)
        OutlinedCard(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Lock, null)
            LibraryPasswordGuidance(value)
          }
        }
    },
  )
}

@Composable
internal fun LibraryPasswordField(
  value: String,
  changed: (String) -> Unit,
  label: Int = R.string.library_password_label,
) {
  var visible by rememberSession { mutableStateOf(false) }
  OutlinedTextField(
    value,
    { changed(it.take(1024)) },
    Modifier.fillMaxWidth(),
    label = { Text(stringResource(label)) },
    singleLine = true,
    visualTransformation =
      if (visible) androidx.compose.ui.text.input.VisualTransformation.None
      else PasswordVisualTransformation(),
    trailingIcon = {
      HintIconButton(
        { visible = !visible },
        tooltip = stringResource(R.string.finish_password_visibility),
      ) {
        Icon(
          if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
          stringResource(R.string.finish_password_visibility),
        )
      }
    },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
  )
}

@Composable
internal fun LibraryConfirmDialog(title: Int, body: Int, dismiss: () -> Unit, submit: () -> Unit) {
  SafeAlertDialog(
    onDismissRequest = dismiss,
    title = { Text(stringResource(title)) },
    text = { Text(stringResource(body)) },
    confirmButton = { TextButton(submit) { Text(stringResource(R.string.library_confirm)) } },
    dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.library_cancel)) } },
  )
}

internal fun libraryErrorResource(error: Exception): Int =
  when {
    error is LibraryLockedException || error.message?.contains("Session locked") == true ->
      R.string.library_error_locked
    error is LibraryPasswordException -> R.string.library_error_password
    error is LibraryConflictException || error.message?.contains("Duplicate sibling") == true ->
      R.string.library_error_conflict
    error.message?.contains("descendant") == true || error.message?.contains("depth") == true ->
      R.string.library_error_cycle
    error.message?.contains("name") == true ||
      error.message?.contains("Path") == true ||
      error.message?.contains("path") == true -> R.string.library_error_path
    else -> R.string.library_error_generic
  }

@Composable
internal fun LibraryPasswordGuidance(value: String) {
  Text(stringResource(R.string.next_password_help))
  com.karandaev.fieldfolio.core.ui.components.bars.PasswordStrengthBar(
    LibraryPasswordPolicy.score(value)
  )
  if (value.isNotEmpty() && !LibraryPasswordPolicy.accepts(value))
    Text(stringResource(R.string.next_password_weak), color = MaterialTheme.colorScheme.error)
}
