package com.karandaev.fieldfolio.core.ui.components.fields

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.names.*
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.state.rememberSession

@Composable
fun ResourceIdentityDialog(
  initial: String,
  displayName: String?,
  dismiss: () -> Unit,
  file: Boolean = false,
  valid: (String) -> Boolean = ItemNames::valid,
  showDisplayName: Boolean = true,
  title: String? = null,
  submit: (String, String?) -> Unit,
) =
  ResourceIdentityDialog(
    ResourceIdentity(initial, displayName),
    dismiss,
    file,
    valid,
    showDisplayName = showDisplayName,
    title = title,
  ) {
    submit(it.name, it.displayName)
  }

@Composable
fun ResourceIdentityDialog(
  initial: ResourceIdentity,
  dismiss: () -> Unit,
  file: Boolean = false,
  valid: (String) -> Boolean = ItemNames::valid,
  showDisplayName: Boolean = true,
  presentation: Boolean = false,
  showVisibility: Boolean = true,
  title: String? = null,
  submit: (ResourceIdentity) -> Unit,
) {
  var draft by rememberSession(initial) { mutableStateOf(initial) }
  val close = rememberUnsavedClose(draft != initial, dismiss)
  SafeAlertDialog(
    close,
    title = { Text(title ?: stringResource(R.string.library_rename)) },
    text = {
      Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showDisplayName)
          ResourceIdentityFields(
            draft,
            { draft = it },
            file,
            presentation,
            "library-name",
            showVisibility,
          )
        else
          ResourceNameField(
            draft.name,
            { draft = draft.copy(name = it) },
            file,
            tag = "library-name",
          )
      }
    },
    confirmButton = {
      TextButton(
        { submit(draft.normalized()) },
        enabled = valid(draft.name.trim()) && draft.valid,
      ) {
        Text(stringResource(R.string.library_save))
      }
    },
    dismissButton = { TextButton(close) { Text(stringResource(R.string.library_cancel)) } },
  )
}
