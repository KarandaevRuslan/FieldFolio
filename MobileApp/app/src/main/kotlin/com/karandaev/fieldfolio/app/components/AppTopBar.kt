package com.karandaev.fieldfolio.app.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton

/**
 * Top app bar used by MapNotes screens.
 *
 * @param title Title displayed in the app bar.
 * @param showBackButton Whether the navigation back button should be displayed.
 * @param onBackClick Callback invoked when the back button is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, showBackButton: Boolean, onBackClick: () -> Unit) {
  TopAppBar(
    title = { Text(title) },
    navigationIcon = {
      if (showBackButton) {
        HintIconButton(onClick = onBackClick, tooltip = stringResource(R.string.ui_back)) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.ui_back),
          )
        }
      }
    },
    colors =
      TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
      ),
  )
}
