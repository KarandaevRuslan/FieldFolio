package com.karandaev.fieldfolio.feature.weathercard.mapper

import com.karandaev.fieldfolio.core.location.LocatedPlace
import com.karandaev.fieldfolio.core.util.formatters.formatTemperature
import com.karandaev.fieldfolio.feature.weather.state.WeatherUiState
import com.karandaev.fieldfolio.feature.weathercard.WeatherCardUiModel

fun WeatherUiState.Success.toWeatherCardUiModel(locatedPlace: LocatedPlace): WeatherCardUiModel {
  val weatherCodeUi = data.weatherCode.toWeatherCodeUi()

  return WeatherCardUiModel(
    temperature = data.temperature.formatTemperature(),
    descriptionRes = weatherCodeUi.descriptionRes,
    locationText = locatedPlace.placeName.takeIf { it.isNotBlank() },
    icon = weatherCodeUi.icon,
  )
}
