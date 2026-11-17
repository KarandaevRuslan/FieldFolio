package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model

import com.karandaev.fieldfolio.core.domain.model.location.MapPoint

/** An arbitrary live or saved point. Identity, NOT coordinates, controls list membership. */
data class OsmTrackedFocusPoint(
  val id: String,
  val title: String,
  val point: MapPoint,
  val measuredAtMillis: Long? = null,
  val accuracyMeters: Float? = null,
  val stale: Boolean = false,
  val linkedMarkerId: String? = null,
  /** When false the point is only a destination in the focus list, not an OSMDroid marker. */
  val showOnMap: Boolean = true,
)
