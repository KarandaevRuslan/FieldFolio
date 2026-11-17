package com.karandaev.fieldfolio.core.weather

/**
 * Provides weather information for geographic coordinates.
 *
 * Implementations may load weather from network APIs, local cache, fake test data, or any other
 * source.
 */
interface WeatherProvider {
  /** Read-only consumers may inspect existing data without granting network access. */
  suspend fun cachedWeather(latitude: Double, longitude: Double): WeatherInfo? = null

  /** Explicit user refresh may bypass the implementation's cache. */
  suspend fun refreshWeather(latitude: Double, longitude: Double): Result<WeatherInfo> =
    getWeather(latitude, longitude)

  /**
   * Returns current weather for the provided coordinates.
   *
   * @param latitude Latitude in degrees.
   * @param longitude Longitude in degrees.
   * @return Result containing weather information or a mapped loading error.
   */
  suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherInfo>
}
