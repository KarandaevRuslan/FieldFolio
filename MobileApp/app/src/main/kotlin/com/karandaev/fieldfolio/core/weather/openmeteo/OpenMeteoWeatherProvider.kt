package com.karandaev.fieldfolio.core.weather.openmeteo

import com.karandaev.fieldfolio.core.weather.WeatherInfo
import com.karandaev.fieldfolio.core.weather.WeatherProvider
import com.karandaev.fieldfolio.core.weather.openmeteo.remote.OpenMeteoApi
import com.karandaev.fieldfolio.core.weather.openmeteo.remote.OpenMeteoApiFactory
import com.karandaev.fieldfolio.core.weather.toWeatherError
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

private const val DefaultCacheTtlMillis = 20L * 60L * 1000L
private const val MaxCacheSize = 50

/**
 * Weather provider backed by the Open-Meteo API.
 *
 * Results are cached in memory by rounded coordinates to avoid unnecessary network calls.
 *
 * @param api Open-Meteo API client.
 * @param cache In-memory weather cache.
 */
class OpenMeteoWeatherProvider(
  private val api: OpenMeteoApi = OpenMeteoApiFactory.create(),
  private val cache: WeatherCache =
    WeatherCache(ttlMillis = DefaultCacheTtlMillis, maxSize = MaxCacheSize),
) : WeatherProvider {
  override suspend fun cachedWeather(latitude: Double, longitude: Double): WeatherInfo? =
    cache.get(WeatherCacheKey.from(latitude, longitude))

  /**
   * Returns current weather for the provided coordinates.
   *
   * Cached data is returned when available and not expired.
   */
  // The lock is per rounded coordinate: duplicate widgets and Clock's zone lookup share a fetch.
  private val requestLocks = java.util.concurrent.ConcurrentHashMap<WeatherCacheKey,
    kotlinx.coroutines.sync.Mutex>()
  private val networkSlots = kotlinx.coroutines.sync.Semaphore(3)

  override suspend fun refreshWeather(latitude: Double, longitude: Double): Result<WeatherInfo> =
    fetch(latitude, longitude, force = true)

  override suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherInfo> =
    fetch(latitude, longitude, force = false)

  private suspend fun fetch(latitude: Double, longitude: Double, force: Boolean): Result<WeatherInfo> {
    val key = WeatherCacheKey.from(latitude, longitude)
    val mutex = requestLocks.computeIfAbsent(key) { kotlinx.coroutines.sync.Mutex() }
    return mutex.withLock {
      try {
        if (!force) cache.get(key)?.let { return@withLock Result.success(it) }
        val result = networkSlots.withPermit {
          api.getForecast(latitude = latitude, longitude = longitude).toWeatherInfo()
        }
        cache.put(key, result)
        Result.success(result)
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (error: Throwable) {
        Result.failure(error.toWeatherError())
      }
    }
  }
}
