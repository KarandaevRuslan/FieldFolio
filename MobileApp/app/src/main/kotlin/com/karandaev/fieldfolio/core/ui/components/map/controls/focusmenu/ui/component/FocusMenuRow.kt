package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model.OsmFocusMenuItem
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.formatter.focusMenuDistanceText
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.formatter.focusMenuLabel
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.formatter.focusMenuPositionText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Displays one selectable focus destination row. */
@Composable
internal fun FocusMenuRow(item: OsmFocusMenuItem, onClick: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val colors = focusMenuRowColors(isOrigin = item.isOrigin)

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 2.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(colors.background)
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = item.title,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.title,
      )

      Text(
        text = item.focusMenuLabel(context),
        style = MaterialTheme.typography.labelSmall,
        color = colors.label,
      )
    }

    item.measuredAtMillis?.let { measuredAt ->
      // The row can remain composed while GPS is unavailable. A one-time clock read would
      // freeze "Last fix: N min" until some unrelated UI event forces a recomposition.
      // This lightweight ticker lives only as long as this focus-menu row is visible.
      var now by remember(measuredAt, item.stale) { mutableLongStateOf(System.currentTimeMillis()) }
      val lifecycle = LocalLifecycleOwner.current.lifecycle
      LaunchedEffect(measuredAt, item.stale, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
          while (isActive) {
            now = System.currentTimeMillis()
            delay(nextFixAgeTickDelayMillis(measuredAt, now, item.stale))
          }
        }
      }
      val ageSeconds = fixAgeSeconds(measuredAt, now)
      Text(
        text =
          if (item.stale)
            context.getString(com.karandaev.fieldfolio.R.string.map_last_fix, ageSeconds / 60L)
          else context.getString(com.karandaev.fieldfolio.R.string.map_fix_age, ageSeconds),
        style = MaterialTheme.typography.labelSmall,
        color = if (item.stale) MaterialTheme.colorScheme.error else colors.secondary,
      )
    }

    Text(
      text = item.point.focusMenuPositionText(),
      modifier = Modifier.padding(top = 2.dp),
      style = MaterialTheme.typography.bodySmall,
      color = colors.secondary,
    )

    Text(
      text = item.focusMenuDistanceText(context),
      modifier = Modifier.padding(top = 2.dp),
      style = MaterialTheme.typography.bodySmall,
      color = colors.secondary,
    )
  }
}

/** Returns color values used by a focus menu row. */
@Composable
private fun focusMenuRowColors(isOrigin: Boolean): FocusMenuRowColors {
  return if (isOrigin) {
    FocusMenuRowColors(
      background = MaterialTheme.colorScheme.primaryContainer,
      title = MaterialTheme.colorScheme.onPrimaryContainer,
      secondary = MaterialTheme.colorScheme.onPrimaryContainer,
      label = MaterialTheme.colorScheme.onPrimaryContainer,
    )
  } else {
    FocusMenuRowColors(
      background = MaterialTheme.colorScheme.surface,
      title = MaterialTheme.colorScheme.onSurface,
      secondary = MaterialTheme.colorScheme.onSurfaceVariant,
      label = MaterialTheme.colorScheme.primary,
    )
  }
}

/** Color values used by a focus menu row. */
private data class FocusMenuRowColors(
  val background: Color,
  val title: Color,
  val secondary: Color,
  val label: Color,
)
