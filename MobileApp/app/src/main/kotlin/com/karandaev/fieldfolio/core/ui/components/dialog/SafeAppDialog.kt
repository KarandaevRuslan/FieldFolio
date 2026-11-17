@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.karandaev.fieldfolio.core.ui.components.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll

val LocalDialogWidth = staticCompositionLocalOf { 360.dp }

/** Center content inside the real window, reserving only the reported system bars and keyboard. */
@Composable
internal fun SafeAppDialog(
  onDismissRequest: () -> Unit,
  properties: DialogProperties = DialogProperties(),
  edgePadding: Dp = 0.dp,
  content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
  AppDialogWindow(onDismissRequest, properties) { insets ->
    Box(
      Modifier.fillMaxSize()
        .testTag("overlay-window")
        .semantics { dialog() }
        .windowInsetsPadding(WindowInsets(insets.left, insets.top, insets.right, insets.bottom))
    ) {
      Box(
        Modifier.fillMaxSize().clipToBounds().testTag("overlay-workspace"),
        contentAlignment = Alignment.Center,
      ) {
        if (properties.dismissOnClickOutside)
          Box(
            Modifier.matchParentSize()
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest,
              )
          )
        BoxWithConstraints(
          Modifier.fillMaxSize().padding(edgePadding).clipToBounds(),
          contentAlignment = Alignment.Center,
          content = { CompositionLocalProvider(LocalDialogWidth provides maxWidth) { content() } },
        )
      }
    }
  }
}

/** Bounded, scrollable alert body with actions reserved inside the safe window rectangle. */
@Composable
internal fun SafeAlertDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  dismissButton: (@Composable () -> Unit)? = null,
  icon: (@Composable () -> Unit)? = null,
  title: (@Composable () -> Unit)? = null,
  text: (@Composable () -> Unit)? = null,
  properties: DialogProperties = DialogProperties(),
) {
  SafeAppDialog(onDismissRequest, properties, edgePadding = 16.dp) {
    val actionsMaxHeight = maxHeight * 0.5f
    val limit = if (properties.usePlatformDefaultWidth) 560.dp else 960.dp
    Surface(
      modifier =
        Modifier.widthIn(max = limit)
          .fillMaxWidth()
          .then(modifier)
          .clipToBounds()
          .testTag("overlay-panel")
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
          ),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
    ) {
      Column {
        Column(
          Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          icon?.let { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { it() } }
          title?.let { ProvideTextStyle(MaterialTheme.typography.headlineSmall) { it() } }
          text?.let { ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() } }
        }
        FlowRow(
          Modifier.fillMaxWidth()
            .heightIn(max = actionsMaxHeight)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
          horizontalArrangement = Arrangement.End,
        ) {
          dismissButton?.invoke()
          confirmButton()
        }
      }
    }
  }
}
