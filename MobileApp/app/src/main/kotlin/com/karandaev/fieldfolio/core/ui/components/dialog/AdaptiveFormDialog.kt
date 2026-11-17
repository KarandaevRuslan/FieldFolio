@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.core.ui.components.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll
import com.karandaev.fieldfolio.core.ui.text.tr

@Composable
fun AdaptiveFormDialog(
  title: String,
  dismiss: () -> Unit,
  properties: DialogProperties = DialogProperties(dismissOnClickOutside = false),
  actions: @Composable RowScope.() -> Unit = {},
  sharedScroll: Boolean = false,
  left: @Composable ColumnScope.() -> Unit,
  right: @Composable ColumnScope.() -> Unit,
) {
  SafeAppDialog(dismiss, properties) {
    Surface(Modifier.fillMaxSize()) {
      Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
          TopAppBar(
            title = { Text(title, maxLines = 1) },
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
              HintIconButton(dismiss, tooltip = tr("close")) {
                Icon(Icons.Default.Close, tr("close"))
              }
            },
            actions = actions,
          )
        },
      ) { pad ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
          if (maxWidth >= 600.dp && maxWidth > maxHeight)
            Row(
              Modifier.fillMaxSize()
                .then(
                  if (sharedScroll)
                    Modifier.testTag("adaptive-form-scroll").verticalScroll(rememberScrollState())
                  else Modifier
                )
                .padding(16.dp),
              horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
              Column(
                Modifier.weight(1f)
                  .then(
                    if (sharedScroll) Modifier
                    else Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
                  ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = left,
              )
              Column(
                Modifier.weight(1f)
                  .then(
                    if (sharedScroll) Modifier
                    else Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
                  ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = right,
              )
            }
          else
            Column(
              Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              left()
              right()
            }
        }
      }
    }
  }
}
