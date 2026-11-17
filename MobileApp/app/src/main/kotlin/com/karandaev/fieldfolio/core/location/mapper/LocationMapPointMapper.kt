package com.karandaev.fieldfolio.core.location.mapper

import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.domain.model.location.mapPointOrNull
import com.karandaev.fieldfolio.core.location.AppLocation
import com.karandaev.fieldfolio.core.location.LocatedPlace

/**
 * Converts an [AppLocation] into a domain-level
 * [com.karandaev.fieldfolio.core.domain.model.location.MapPoint].
 */
fun AppLocation.toMapPoint(): MapPoint {
  return MapPoint(latitude = latitude, longitude = longitude)
}

/** Converts a [LocatedPlace] into a domain-level [MapPoint]. */
fun LocatedPlace.toMapPoint(): MapPoint {
  return MapPoint(latitude = latitude, longitude = longitude)
}

/** Creates a [MapPoint] from a nullable [AppLocation]. */
fun mapPointOrNull(location: AppLocation?): MapPoint? {
  return location?.let { mapPointOrNull(latitude = it.latitude, longitude = it.longitude) }
}

/** Creates a [MapPoint] from a nullable [LocatedPlace]. */
fun mapPointOrNull(place: LocatedPlace?): MapPoint? {
  return place?.let { mapPointOrNull(latitude = it.latitude, longitude = it.longitude) }
}
