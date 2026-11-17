package com.karandaev.fieldfolio.app.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R

@Composable
fun CreateNoteFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
  com.karandaev.fieldfolio.core.ui.components.actions.IconHint(
    stringResource(R.string.ui_new_note),
    modifier = modifier,
  ) {
    FloatingActionButton(
      onClick = onClick,
      modifier = Modifier,
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
      Icon(
        imageVector = Icons.Default.Add,
        contentDescription = stringResource(R.string.ui_new_note),
      )
    }
  }
}
