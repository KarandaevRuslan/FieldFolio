package com.karandaev.fieldfolio.core.location.mapper

import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import org.osmdroid.util.GeoPoint

/** Converts a domain-level [MapPoint] into an OSMDroid [GeoPoint]. */
fun MapPoint.toGeoPoint(): GeoPoint {
  return GeoPoint(latitude, longitude)
}

/** Converts an OSMDroid [GeoPoint] into a domain-level [MapPoint]. */
fun GeoPoint.toMapPoint(): MapPoint {
  return MapPoint(latitude = latitude, longitude = longitude)
}
