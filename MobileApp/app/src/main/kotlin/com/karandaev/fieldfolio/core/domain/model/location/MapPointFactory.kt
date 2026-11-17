package com.karandaev.fieldfolio.core.domain.model.location

/**
 * Creates a [MapPoint] only when both coordinate values are available.
 *
 * Use this when optional latitude and longitude values should represent either a complete point or
 * no point at all.
 */
fun mapPointOrNull(latitude: Double?, longitude: Double?): MapPoint? {
  return if (latitude != null && longitude != null) {
    MapPoint(latitude, longitude)
  } else {
    null
  }
}
