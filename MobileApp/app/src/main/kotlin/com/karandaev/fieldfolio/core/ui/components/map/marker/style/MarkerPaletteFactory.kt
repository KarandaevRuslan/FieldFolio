package com.karandaev.fieldfolio.core.ui.components.map.marker.style

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.MarkerPalette

/** Creates a marker palette from the current Material theme. */
@Composable
internal fun markerPalette(): MarkerPalette {
  return MarkerPalette(
    noteColor = MaterialTheme.colorScheme.error.toArgb(),
    selectedColor = MaterialTheme.colorScheme.error.toArgb(),
    currentColor = MaterialTheme.colorScheme.primary.toArgb(),
    surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
    textColor = MaterialTheme.colorScheme.onSurface.toArgb(),
    strokeColor = MaterialTheme.colorScheme.surface.toArgb(),
  )
}
