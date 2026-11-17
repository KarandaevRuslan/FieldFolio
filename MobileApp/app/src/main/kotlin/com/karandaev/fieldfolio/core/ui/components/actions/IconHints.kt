@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.core.ui.components.actions

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.*

/** Standard tooltips; observing a hold never consumes drag/seek/map repeat gestures. */
@Composable
fun IconHint(
  label: String?,
  observeHold: Boolean = false,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  if (label.isNullOrBlank()) {
    Box(modifier) { content() }
    return
  }
  val state = rememberTooltipState()
  val scope = rememberCoroutineScope()
  val config = LocalViewConfiguration.current
  val gestureModifier =
    if (!observeHold) Modifier
    else
      Modifier.pointerInput(label) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
          val job =
            scope.launch {
              delay(config.longPressTimeoutMillis)
              state.show()
            }
          try {
            var pressed = true
            while (pressed) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              val pointer = event.changes.firstOrNull { it.id == down.id }
              if (pointer == null) break
              if ((pointer.position - down.position).getDistance() > config.touchSlop) {
                job.cancel()
                state.dismiss()
              }
              pressed = pointer.pressed
            }
          } finally {
            job.cancel()
            state.dismiss()
          }
        }
      }
  // Parent alignment belongs to our outer layout, not TooltipBox's internal anchor layout.
  Box(modifier) {
    TooltipBox(
      TooltipDefaults.rememberPlainTooltipPositionProvider(),
      { PlainTooltip { Text(label) } },
      state,
      modifier = gestureModifier,
      enableUserInput = !observeHold,
      focusable = !observeHold,
      content = content,
    )
  }
}

@Composable
fun HintIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
  interactionSource: MutableInteractionSource? = null,
  tooltip: String?,
  observeHold: Boolean = false,
  anchorModifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  IconHint(tooltip, observeHold, anchorModifier) {
    IconButton(onClick, modifier, enabled, colors, interactionSource, content)
  }
}

@Composable
fun HintIconToggleButton(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: IconToggleButtonColors = IconButtonDefaults.iconToggleButtonColors(),
  interactionSource: MutableInteractionSource? = null,
  tooltip: String?,
  observeHold: Boolean = false,
  anchorModifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  IconHint(tooltip, observeHold, anchorModifier) {
    IconToggleButton(
      checked,
      onCheckedChange,
      modifier,
      enabled,
      colors,
      interactionSource,
      content,
    )
  }
}

@Composable
fun HintFilledIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
  interactionSource: MutableInteractionSource? = null,
  tooltip: String?,
  observeHold: Boolean = false,
  anchorModifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  IconHint(tooltip, observeHold, anchorModifier) {
    FilledIconButton(
      onClick,
      modifier,
      enabled,
      colors = colors,
      interactionSource = interactionSource,
      content = content,
    )
  }
}

@Composable
fun HintFilledTonalIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
  interactionSource: MutableInteractionSource? = null,
  tooltip: String?,
  observeHold: Boolean = false,
  anchorModifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  IconHint(tooltip, observeHold, anchorModifier) {
    FilledTonalIconButton(
      onClick,
      modifier,
      enabled,
      colors = colors,
      interactionSource = interactionSource,
      content = content,
    )
  }
}
