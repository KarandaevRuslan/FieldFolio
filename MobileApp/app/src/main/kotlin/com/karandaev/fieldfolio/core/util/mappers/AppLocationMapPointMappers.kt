package com.karandaev.fieldfolio.core.util.mappers

import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.location.AppLocation

/**
 * Converts an app-level [AppLocation] into a map-level
 * [com.karandaev.fieldfolio.core.domain.model.location.MapPoint].
 */
fun AppLocation.toMapPoint(): MapPoint {
  return MapPoint(latitude = latitude, longitude = longitude)
}

/**
 * Converts a map-level [MapPoint] into an app-level [AppLocation].
 *
 * Since [MapPoint] only stores coordinates, metadata can be provided optionally.
 */
fun MapPoint.toAppLocation(
  accuracyMeters: Float? = null,
  timestampMillis: Long? = null,
  provider: String? = null,
  isDefault: Boolean = false,
): AppLocation {
  return AppLocation(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    timestampMillis = timestampMillis,
    provider = provider,
    isDefault = isDefault,
  )
}
