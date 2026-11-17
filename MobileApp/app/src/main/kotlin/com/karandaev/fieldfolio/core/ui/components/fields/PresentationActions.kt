package com.karandaev.fieldfolio.core.ui.components.fields

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.names.ResourceIdentity
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconToggleButton

@Composable
fun RowScope.PresentationActions(
  value: ResourceIdentity,
  change: (ResourceIdentity) -> Unit,
  title: Boolean = true,
) {
  if (title)
    HintIconToggleButton(
      value.hideTitle,
      { change(value.copy(hideTitle = it)) },
      Modifier.testTag("resource-hide-title"),
      tooltip =
        stringResource(if (value.hideTitle) R.string.v16_show_title else R.string.next_hide_title),
    ) {
      Icon(
        if (value.hideTitle) Icons.Default.FormatClear else Icons.Default.Title,
        stringResource(if (value.hideTitle) R.string.v16_show_title else R.string.next_hide_title),
      )
    }
  HintIconToggleButton(
    value.visible,
    { change(value.copy(visible = it)) },
    Modifier.testTag("resource-visible"),
    tooltip =
      stringResource(if (value.visible) R.string.next_hide_object else R.string.next_show_object),
  ) {
    Icon(
      if (value.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
      stringResource(if (value.visible) R.string.next_hide_object else R.string.next_show_object),
    )
  }
}
