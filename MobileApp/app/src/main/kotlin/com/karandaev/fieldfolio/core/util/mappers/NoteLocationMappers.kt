package com.karandaev.fieldfolio.core.util.mappers

import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.location.LocatedPlace

/**
 * Converts note coordinates to a map-level
 * [com.karandaev.fieldfolio.core.domain.model.location.MapPoint].
 */
fun InspectionReport.toMapPoint(): MapPoint {
  return MapPoint(latitude = latitude, longitude = longitude)
}

/** Converts note coordinates to a [LocatedPlace]. */
fun InspectionReport.toLocatedPlace(): LocatedPlace {
  return toMapPoint().toLocatedPlace(placeName = title.ifBlank { "Note location" })
}
