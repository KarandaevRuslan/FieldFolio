package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.formatter

import android.content.Context
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model.OsmFocusMenuItem
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model.OsmFocusMenuItemKind
import com.karandaev.fieldfolio.core.util.formatters.formatCoordinates
import kotlin.math.roundToInt

internal fun OsmFocusMenuItem.focusMenuLabel(context: Context): String {
  val label =
    context.getString(
      when (kind) {
        OsmFocusMenuItemKind.FocusTarget -> R.string.ui_kind_target
        OsmFocusMenuItemKind.Current -> R.string.ui_kind_current
        OsmFocusMenuItemKind.Tracked -> R.string.ui_kind_current
        OsmFocusMenuItemKind.InitialCenter -> R.string.ui_kind_center
        OsmFocusMenuItemKind.Marker -> R.string.ui_kind_marker
      }
    )
  return if (isOrigin) context.getString(R.string.ui_kind_origin, label) else label
}

internal fun MapPoint.focusMenuPositionText(): String = formatCoordinates(latitude, longitude)

internal fun OsmFocusMenuItem.focusMenuDistanceText(context: Context): String {
  if (isOrigin) return context.getString(R.string.ui_origin)
  return if (distanceFromOriginMeters < 1000.0)
    context.getString(R.string.ui_distance_m, distanceFromOriginMeters.roundToInt())
  else
    context.getString(
      R.string.ui_distance_km,
      String.format(
        context.resources.configuration.locales[0],
        "%.2f",
        distanceFromOriginMeters / 1000.0,
      ),
    )
}
