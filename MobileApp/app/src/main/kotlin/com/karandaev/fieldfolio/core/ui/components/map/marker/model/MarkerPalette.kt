package com.karandaev.fieldfolio.core.ui.components.map.marker.model

/**
 * Color palette used when rendering semantic marker types.
 *
 * @property noteColor Fill color for note markers.
 * @property selectedColor Fill color for selected-location markers.
 * @property currentColor Fill color for current-location markers.
 * @property strokeColor Border color used around marker icons.
 */
internal data class MarkerPalette(
  val noteColor: Int,
  val selectedColor: Int,
  val currentColor: Int,
  val surfaceColor: Int = android.graphics.Color.WHITE,
  val textColor: Int = android.graphics.Color.BLACK,
  val strokeColor: Int,
)
