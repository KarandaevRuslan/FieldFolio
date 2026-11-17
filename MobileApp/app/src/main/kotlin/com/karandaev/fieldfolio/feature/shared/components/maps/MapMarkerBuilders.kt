package com.karandaev.fieldfolio.feature.shared.components.maps

import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.OsmMarkerKind
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.OsmMarkerSpec
import com.karandaev.fieldfolio.core.ui.components.map.marker.utils.noteSnippet

internal fun buildBrowseMapMarkers(
  inspectionReports: List<InspectionReport>,
  currentLocationTitle: String,
  currentLocationPoint: MapPoint?,
): List<OsmMarkerSpec> {
  return buildList {
    addNoteMarkers(inspectionReports = inspectionReports)

    currentLocationPoint?.let { point ->
      addCurrentLocationMarker(point = point, title = currentLocationTitle)
    }
  }
}

internal fun buildMiniMapMarkers(
  selectedPoint: MapPoint?,
  currentLocationTitle: String,
  selectedLocationTitle: String,
  currentLocationPoint: MapPoint?,
): List<OsmMarkerSpec> {
  return buildList {
    currentLocationPoint?.let { point ->
      addCurrentLocationMarker(point = point, title = currentLocationTitle)
    }

    selectedPoint?.let { point ->
      addSelectedLocationMarker(point = point, title = selectedLocationTitle)
    }
  }
}

private fun MutableList<OsmMarkerSpec>.addNoteMarkers(inspectionReports: List<InspectionReport>) {
  inspectionReports.forEach { note ->
    add(
      OsmMarkerSpec(
        id = noteMarkerId(note.id),
        point = MapPoint(latitude = note.latitude, longitude = note.longitude),
        kind = OsmMarkerKind.Note,
        title = note.title,
        snippet = noteSnippet(note.content),
        clickable = true,
      )
    )
  }
}

private fun MutableList<OsmMarkerSpec>.addCurrentLocationMarker(point: MapPoint, title: String) {
  add(
    OsmMarkerSpec(
      id = CURRENT_LOCATION_MARKER_ID,
      point = point,
      kind = OsmMarkerKind.Current,
      title = title,
    )
  )
}

private fun MutableList<OsmMarkerSpec>.addSelectedLocationMarker(point: MapPoint, title: String) {
  add(
    OsmMarkerSpec(
      id = SELECTED_LOCATION_MARKER_ID,
      point = point,
      kind = OsmMarkerKind.Selected,
      title = title,
    )
  )
}
