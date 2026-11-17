package com.karandaev.fieldfolio.core.ui.components.map.controls.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import com.karandaev.fieldfolio.R

/**
 * Displays a focus button over the map.
 *
 * The caller owns the focus target and behavior. A regular click focuses the default destination,
 * while a long click may open the focus destination menu.
 */
@Composable
internal fun BoxScope.MapFocusButton(
  text: String,
  metrics: MapControlMetrics,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
) {
  val description = stringResource(R.string.map_focus_target_action)
  MapOverlayButton(
    text = text,
    hint = description,
    size = metrics.primaryButtonSize,
    modifier =
      Modifier.semantics {
          contentDescription = description
          onClick {
            onClick()
            true
          }
        }
        .align(Alignment.BottomEnd)
        .padding(end = metrics.edgePadding, bottom = metrics.edgePadding),
    onClick = onClick,
    onLongClick = onLongClick,
  )
}
