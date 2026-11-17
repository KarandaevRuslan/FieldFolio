package com.karandaev.fieldfolio.core.weather.openmeteo

import com.karandaev.fieldfolio.core.weather.WeatherInfo
import com.karandaev.fieldfolio.core.weather.WeatherProvider
import com.karandaev.fieldfolio.core.weather.openmeteo.remote.OpenMeteoApi
import com.karandaev.fieldfolio.core.weather.openmeteo.remote.OpenMeteoApiFactory
import com.karandaev.fieldfolio.core.weather.toWeatherError

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
  override suspend fun refreshWeather(latitude: Double, longitude: Double): Result<WeatherInfo> {
    cache.remove(WeatherCacheKey.from(latitude, longitude))
    return getWeather(latitude, longitude)
  }

  override suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherInfo> {
    return runCatching {
        val cacheKey = WeatherCacheKey.from(latitude = latitude, longitude = longitude)

        cache.get(cacheKey)?.let { cachedWeather ->
          return@runCatching cachedWeather
        }

        val response = api.getForecast(latitude = latitude, longitude = longitude)

        val weatherInfo = response.toWeatherInfo()

        cache.put(key = cacheKey, weatherInfo = weatherInfo)

        weatherInfo
      }
      .recoverCatching { error -> throw error.toWeatherError() }
  }
}
