package com.karandaev.fieldfolio.core.location.provider

import android.location.Location
import com.karandaev.fieldfolio.core.location.AppLocation

/** Converts Android framework location objects into app-level location models. */
internal fun Location.toAppLocation(): AppLocation {
  return AppLocation(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    timestampMillis = time.takeIf { it > 0L },
    provider = provider,
    isDefault = false,
  )
}
