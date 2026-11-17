package com.karandaev.fieldfolio.core.ui.components.fields

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.names.ResourceIdentity

@Composable
fun ResourceIdentityFields(
  value: ResourceIdentity,
  changed: (ResourceIdentity) -> Unit,
  file: Boolean = false,
  presentation: Boolean = false,
  tag: String = "resource-name",
  showVisibility: Boolean = true,
) {
  ResourceNameField(value.name, { changed(value.copy(name = it)) }, file, tag = tag)
  OutlinedTextField(
    value.displayName.orEmpty(),
    { changed(value.copy(displayName = it.take(120))) },
    Modifier.fillMaxWidth().testTag("resource-display-name"),
    singleLine = true,
    enabled = !value.hideTitle,
    label = { Text(stringResource(R.string.finish_display_name)) },
    supportingText = {
      Text(
        stringResource(
          if (value.hideTitle) R.string.next_title_empty else R.string.next_display_help
        )
      )
    },
  )
  if (presentation) {
    ListItem(
      headlineContent = { Text(stringResource(R.string.next_hide_title)) },
      trailingContent = {
        Switch(
          value.hideTitle,
          { changed(value.copy(hideTitle = it)) },
          Modifier.testTag("resource-hide-title"),
        )
      },
    )
    if (showVisibility)
      ListItem(
        headlineContent = { Text(stringResource(R.string.next_visible)) },
        trailingContent = {
          Switch(
            value.visible,
            { changed(value.copy(visible = it)) },
            Modifier.testTag("resource-visible"),
          )
        },
      )
  }
}
