package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model

import androidx.compose.runtime.Immutable
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint

/**
 * Describes a selectable focus destination displayed in the focus menu.
 *
 * @property id Stable item identifier used by lazy lists and click handling.
 * @property title Human-readable destination title.
 * @property point Destination position.
 * @property kind Semantic destination type used for labels and ordering.
 * @property distanceFromOriginMeters Spherical distance from the menu distance origin in meters.
 * @property isOrigin Whether this destination is used as the distance origin.
 */
@Immutable
data class OsmFocusMenuItem(
  val id: String,
  val title: String,
  val point: MapPoint,
  val kind: OsmFocusMenuItemKind,
  val distanceFromOriginMeters: Double,
  val isOrigin: Boolean,
  val measuredAtMillis: Long? = null,
  val accuracyMeters: Float? = null,
  val stale: Boolean = false,
)

/** Describes the semantic source of a focus menu item. */
@Immutable
enum class OsmFocusMenuItemKind {
  FocusTarget,
  Current,
  Tracked,
  InitialCenter,
  Marker,
}
