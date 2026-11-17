package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R

/** Displays a title search field for focus menu destinations. */
@Composable
internal fun FocusMenuSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  onClearClick: () -> Unit,
) {
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    singleLine = true,
    label = { Text(text = stringResource(R.string.ui_search_by_title)) },
    trailingIcon = {
      if (query.isNotEmpty()) {
        TextButton(onClick = onClearClick) { Text(text = stringResource(R.string.ui_clear)) }
      }
    },
  )
}
