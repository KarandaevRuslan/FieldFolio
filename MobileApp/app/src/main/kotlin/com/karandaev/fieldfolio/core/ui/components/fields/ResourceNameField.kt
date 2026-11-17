package com.karandaev.fieldfolio.core.ui.components.fields

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExtensionOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton

fun withoutFileExtension(name: String): String {
  val dot = name.lastIndexOf('.')
  return if (dot > 0 && dot < name.lastIndex) name.substring(0, dot) else name
}

@Composable
fun ResourceNameField(
  value: String,
  change: (String) -> Unit,
  file: Boolean = false,
  modifier: Modifier = Modifier,
  tag: String = "resource-name",
  label: String? = null,
) {
  OutlinedTextField(
    value,
    { change(it.take(120)) },
    modifier.fillMaxWidth().testTag(tag),
    label = { Text(label ?: stringResource(R.string.finish_logical_name)) },
    singleLine = true,
    trailingIcon = {
      if (file)
        HintIconButton(
          { change(withoutFileExtension(value)) },
          enabled = withoutFileExtension(value) != value,
          tooltip = stringResource(R.string.polish_remove_extension),
        ) {
          Icon(Icons.Default.ExtensionOff, stringResource(R.string.polish_remove_extension))
        }
    },
  )
}

@Composable
fun ResourceNameDialog(
  initial: String,
  dismiss: () -> Unit,
  file: Boolean = false,
  title: String = stringResource(R.string.library_rename),
  submit: (String) -> Unit,
) {
  ResourceIdentityDialog(
    initial,
    null,
    dismiss,
    file = file,
    showDisplayName = false,
    title = title,
  ) { name, _ ->
    submit(name)
  }
}
