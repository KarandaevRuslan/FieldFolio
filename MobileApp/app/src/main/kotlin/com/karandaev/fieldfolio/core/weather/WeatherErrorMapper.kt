package com.karandaev.fieldfolio.core.weather

import com.karandaev.fieldfolio.core.weather.openmeteo.OpenMeteoWeatherParseException
import com.squareup.moshi.JsonDataException
import java.io.IOException
import retrofit2.HttpException

/** Maps low-level weather loading errors to user-facing domain errors. */
internal fun Throwable.toWeatherError(): Throwable {
  return when (this) {
    is HttpException -> IllegalStateException("Weather server error: ${code()}", this)

    is IOException -> IllegalStateException("No connection to the weather service", this)

    is JsonDataException,
    is OpenMeteoWeatherParseException ->
      IllegalStateException("Invalid response from the weather service", this)

    else ->
      IllegalStateException("Weather loading error: ${message ?: this::class.simpleName}", this)
  }
}
