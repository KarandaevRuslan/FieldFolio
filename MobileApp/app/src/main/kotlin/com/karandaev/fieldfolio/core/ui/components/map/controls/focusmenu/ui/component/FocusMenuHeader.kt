package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R

/** Displays the focus menu title. */
@Composable
internal fun FocusMenuHeader() {
  Text(
    text = stringResource(R.string.ui_focus_destination),
    modifier = Modifier.padding(horizontal = 20.dp),
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onSurface,
  )
}
