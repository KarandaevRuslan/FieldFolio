package com.karandaev.fieldfolio.core.util.mappers

import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.location.LocatedPlace

/**
 * Converts a [LocatedPlace] into a map-level
 * [com.karandaev.fieldfolio.core.domain.model.location.MapPoint].
 */
fun LocatedPlace.toMapPoint(): MapPoint {
  return location.toMapPoint()
}

/** Converts a map-level [MapPoint] into a [LocatedPlace] with the given display name. */
fun MapPoint.toLocatedPlace(
  placeName: String,
  accuracyMeters: Float? = null,
  timestampMillis: Long? = null,
  provider: String? = null,
  isDefault: Boolean = false,
): LocatedPlace {
  return LocatedPlace(
    location =
      toAppLocation(
        accuracyMeters = accuracyMeters,
        timestampMillis = timestampMillis,
        provider = provider,
        isDefault = isDefault,
      ),
    placeName = placeName,
  )
}
