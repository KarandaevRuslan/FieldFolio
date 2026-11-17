package com.karandaev.fieldfolio.core.location.source

import com.karandaev.fieldfolio.core.location.LocatedPlace
import com.karandaev.fieldfolio.core.location.resolver.LocationResolver

/** Location source backed by current device location resolving. */
class CurrentLocationSource(private val locationResolver: LocationResolver) : LocationSource {

  override suspend fun resolve(): LocatedPlace? {
    return locationResolver.getLocatedPlace()
  }
}
