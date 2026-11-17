package com.karandaev.fieldfolio.core.location.resolver

import com.karandaev.fieldfolio.core.domain.model.persisted.LastKnownLocation
import com.karandaev.fieldfolio.core.domain.repository.PersistedAppStateRepository
import com.karandaev.fieldfolio.core.location.AppLocation
import com.karandaev.fieldfolio.core.location.LocatedPlace
import com.karandaev.fieldfolio.core.location.mapper.toMapPoint
import kotlinx.coroutines.flow.first

/**
 * Location resolver decorator that stores the last successfully resolved real device location.
 *
 * This resolver delegates location resolving to [delegate]. When [delegate.getLocatedPlace] returns
 * a non-null [LocatedPlace], this resolver saves its coordinates, place name, and update timestamp
 * into [persistedAppStateRepository].
 *
 * Default fallback locations and explicitly resolved coordinates are intentionally not cached, so
 * app defaults or fixed-coordinate screens do not overwrite the user's last real known location.
 */
class CachingLocationResolver(
  private val delegate: LocationResolver,
  private val persistedAppStateRepository: PersistedAppStateRepository,
  private val currentTimeMillis: () -> Long = { System.currentTimeMillis() },
) : LocationResolver {

  /** Returns the best available real located place and saves it when it is available. */
  override suspend fun getLocatedPlace(): LocatedPlace? {
    val locatedPlace = delegate.getLocatedPlace()

    if (locatedPlace != null) {
      cacheLocatedPlace(locatedPlace)
    }

    return locatedPlace
  }

  /**
   * Returns the best available located place or the app default.
   *
   * This result is not cached because it may contain the app default location.
   */
  override suspend fun getLocatedPlaceOrDefault(): LocatedPlace {
    return delegate.getLocatedPlaceOrDefault()
  }

  /**
   * Resolves the given coordinates into a located place without saving it as the last device
   * location.
   */
  override suspend fun resolvePlace(location: AppLocation): LocatedPlace {
    return delegate.resolvePlace(location)
  }

  /** Saves the located place as the last known real device location. */
  private suspend fun cacheLocatedPlace(locatedPlace: LocatedPlace) {
    val measuredAt =
      locatedPlace.location.timestampMillis?.takeIf { it > 0L } ?: currentTimeMillis()
    // A legacy location resolver and the shared coordinator can both write this record.
    // Never let an old framework cache read overwrite a newer fix after a screen resumes.
    val previous = persistedAppStateRepository.observeLastKnownLocation().first()
    if (previous != null && previous.updatedAt > measuredAt) return
    persistedAppStateRepository.setLastKnownLocation(
      LastKnownLocation(
        point = locatedPlace.toMapPoint(),
        placeName = locatedPlace.placeName,
        updatedAt = measuredAt,
      )
    )
  }
}
