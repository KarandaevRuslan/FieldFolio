@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.karandaev.fieldfolio.core.ui.components.actions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll
import com.karandaev.fieldfolio.core.ui.text.tr

private val ActionWidth = compositionLocalOf { 300.dp }

/** A bounded, scrollable window shared by add and context actions in both file systems. */
@Composable
fun AdaptiveActionMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  title: String,
  tag: String = "context-actions",
  content: @Composable () -> Unit,
) {
  if (!expanded) return
  SafeAppDialog(onDismissRequest) {
    Scaffold(
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        TopAppBar(
          title = { Text(title, maxLines = 1) },
          windowInsets = WindowInsets(0, 0, 0, 0),
          navigationIcon = {
            HintIconButton(onDismissRequest, tooltip = tr("close")) {
              Icon(Icons.Default.Close, tr("close"))
            }
          },
        )
      },
    ) { pad ->
      BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
        val columns = if (maxWidth >= 600.dp && maxWidth > maxHeight) 2 else 1
        Column(
          Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).testTag(tag)
        ) {
          BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = (maxWidth - 8.dp * (columns - 1)) / columns
            CompositionLocalProvider(ActionWidth provides width) {
              FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                content()
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun AdaptiveActionItem(
  text: @Composable () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  OutlinedCard(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.width(ActionWidth.current),
  ) {
    ListItem(
      headlineContent = text,
      colors =
        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
  }
}
