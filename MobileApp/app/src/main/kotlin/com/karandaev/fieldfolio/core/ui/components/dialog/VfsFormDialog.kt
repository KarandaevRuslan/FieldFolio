@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.core.ui.components.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll

@Composable
internal fun VfsFormDialog(
  title: Int,
  dismiss: () -> Unit,
  secure: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  SafeAppDialog(
    dismiss,
    properties =
      DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnClickOutside = false,
        securePolicy = if (secure) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit,
      ),
  ) {
    Surface(Modifier.fillMaxSize()) {
      Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
          TopAppBar(
            title = { Text(stringResource(title)) },
            navigationIcon = {
              HintIconButton(dismiss, tooltip = stringResource(R.string.library_cancel)) {
                Icon(
                  androidx.compose.material.icons.Icons.Default.Close,
                  stringResource(R.string.library_cancel),
                )
              }
            },
          )
        },
      ) { padding ->
        Column(
          Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(16.dp),
          horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
          Column(
            Modifier.widthIn(max = 720.dp)
              .fillMaxWidth()
              .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
          )
        }
      }
    }
  }
}
