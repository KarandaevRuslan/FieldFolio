package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model

import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.OsmMarkerKind
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.OsmMarkerSpec

/**
 * Builds focus destinations ordered by proximity to the selected origin.
 * The origin and all points at that position come first; other destinations follow by distance.
 * Individual participants keep separate identities even when their coordinates coincide.
 */
fun buildOsmFocusMenuItems(
  focusTarget: MapPoint?,
  initialCenter: MapPoint,
  focusTargetTitle: String,
  initialCenterTitle: String,
  markers: List<OsmMarkerSpec>,
  trackedPoints: List<OsmTrackedFocusPoint> = emptyList(),
  explicitDistanceOrigin: MapPoint? = null,
  focusTargetMarkerId: String? = null,
  showCurrentMapCenter: Boolean = true,
): List<OsmFocusMenuItem> {
  val distanceOrigin = explicitDistanceOrigin ?: focusTarget ?: initialCenter

  val distanceOriginKey = distanceOrigin.toMapPointKey()
  val usedIds = mutableSetOf<String>()
  // The focus target is not a new entity. If its destination is already listed, an
  // extra row would only repeat the same navigation action. Different participants
  // at that position still retain their own rows, keyed by identity.
  val targetKey = focusTarget?.toMapPointKey()
  val representedFocusTarget = targetKey != null && (
    markers.any { marker ->
      marker.point.toMapPointKey() == targetKey ||
        (focusTargetMarkerId != null && marker.id == focusTargetMarkerId)
    } || trackedPoints.any { tracked ->
      tracked.point.toMapPointKey() == targetKey ||
        (focusTargetMarkerId != null && tracked.linkedMarkerId == focusTargetMarkerId)
    }
  )

  val unsorted = buildList {
    focusTarget?.takeUnless { representedFocusTarget }?.let { point ->
      addIfNotDuplicate(
        item =
          point.toFocusMenuItem(
            id = "focus-target",
            title = focusTargetTitle,
            kind = OsmFocusMenuItemKind.FocusTarget,
            distanceOrigin = distanceOrigin,
            distanceOriginKey = distanceOriginKey,
          ),
        usedIds = usedIds,
      )
    }

    trackedPoints.forEach { tracked ->
      addIfNotDuplicate(
        item = tracked.point.toFocusMenuItem(
          id = "tracked-${tracked.id}",
          title = tracked.title,
          kind = OsmFocusMenuItemKind.Tracked,
          distanceOrigin = distanceOrigin,
          distanceOriginKey = distanceOriginKey,
        ).copy(
          measuredAtMillis = tracked.measuredAtMillis,
          accuracyMeters = tracked.accuracyMeters,
          stale = tracked.stale,
        ),
        usedIds = usedIds,
      )
    }

    val representedMarkers = trackedPoints.map { it.linkedMarkerId ?: "tracked-${it.id}" }.toSet()
    markers.asSequence()
      .filter { it.kind == OsmMarkerKind.Current && it.id !in representedMarkers }
      .forEach { marker ->
      addIfNotDuplicate(
        item =
          marker.toFocusMenuItem(
            id = "current-${marker.id}",
            kind = OsmFocusMenuItemKind.Current,
            distanceOrigin = distanceOrigin,
            distanceOriginKey = distanceOriginKey,
          ),
        usedIds = usedIds,
      )
    }

    if (showCurrentMapCenter) {
      addIfNotDuplicate(
        item =
          initialCenter.toFocusMenuItem(
            id = "initial-center",
            title = initialCenterTitle,
            kind = OsmFocusMenuItemKind.InitialCenter,
            distanceOrigin = distanceOrigin,
            distanceOriginKey = distanceOriginKey,
          ),
        usedIds = usedIds,
      )
    }

    markers.asSequence().filter { it.kind != OsmMarkerKind.Current }.forEach { marker ->
      addIfNotDuplicate(
        item =
          marker.toFocusMenuItem(
            id = "marker-${marker.id}",
            kind = OsmFocusMenuItemKind.Marker,
            distanceOrigin = distanceOrigin,
            distanceOriginKey = distanceOriginKey,
          ),
        usedIds = usedIds,
      )
    }
  }
  // Stable sorting: origin (including all collocated identities) first, then nearest
  // destinations. A "Current" marker never outranks a closer selected marker.
  return unsorted.sortedWith(
    compareByDescending<OsmFocusMenuItem> { it.isOrigin }
      .thenBy { it.distanceFromOriginMeters }
  )
}

/** Converts a marker to a focus menu item. */
private fun OsmMarkerSpec.toFocusMenuItem(
  id: String,
  kind: OsmFocusMenuItemKind,
  distanceOrigin: MapPoint,
  distanceOriginKey: MapPointKey,
): OsmFocusMenuItem {
  return point.toFocusMenuItem(
    id = id,
    title = title,
    kind = kind,
    distanceOrigin = distanceOrigin,
    distanceOriginKey = distanceOriginKey,
  )
}

/** Converts a map point to a focus menu item. */
private fun MapPoint.toFocusMenuItem(
  id: String,
  title: String,
  kind: OsmFocusMenuItemKind,
  distanceOrigin: MapPoint,
  distanceOriginKey: MapPointKey,
): OsmFocusMenuItem {
  return OsmFocusMenuItem(
    id = id,
    title = title,
    point = this,
    kind = kind,
    distanceFromOriginMeters = sphericalDistanceTo(distanceOrigin),
    isOrigin = toMapPointKey() == distanceOriginKey,
  )
}

/** Only the SAME resource identity is a duplicate. Different people may stand together. */
private fun MutableList<OsmFocusMenuItem>.addIfNotDuplicate(
  item: OsmFocusMenuItem,
  usedIds: MutableSet<String>,
) {
  if (usedIds.add(item.id)) add(item)
}
