package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model

import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.OsmMarkerKind
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.OsmMarkerSpec

/**
 * Show live-feed points on the map as well as in its focus menu.
 *
 * Existing regular markers keep their identity and click behaviour. A tracked point with a
 * matching marker ID updates the position only for the device marker (which can otherwise
 * remain at a saved, stale position on the main map).
 */
fun mergeMarkersWithTrackedPoints(
  markers: List<OsmMarkerSpec>,
  trackedPoints: List<OsmTrackedFocusPoint>,
): List<OsmMarkerSpec> {
  val result = markers.toMutableList()
  val markerIndexes = result.indices.associateBy { result[it].id }.toMutableMap()
  trackedPoints.forEach { tracked ->
    if (!tracked.showOnMap) return@forEach
    val markerId = tracked.linkedMarkerId ?: "tracked-${tracked.id}"
    val existing = markerIndexes[markerId]
    if (existing != null) {
      if (tracked.id == "device:self" && markerId == "current-location") {
        result[existing] = result[existing].copy(point = tracked.point, title = tracked.title)
      }
    } else {
      markerIndexes[markerId] = result.size
      result += OsmMarkerSpec(
        id = markerId,
        point = tracked.point,
        kind = OsmMarkerKind.Current,
        title = tracked.title,
      )
    }
  }
  return result
}

/** Keep the focus button pointed at the currently rendered marker, not its stale caller snapshot. */
fun resolveLinkedFocusTarget(
  focusTarget: MapPoint?,
  focusTargetMarkerId: String?,
  markers: List<OsmMarkerSpec>,
): MapPoint? =
  focusTargetMarkerId?.let { id -> markers.firstOrNull { it.id == id }?.point } ?: focusTarget
