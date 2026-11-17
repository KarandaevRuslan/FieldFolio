package com.karandaev.fieldfolio.feature.weathercard

import com.karandaev.fieldfolio.core.ui.components.card.AppCardMedia

data class WeatherCardUiModel(
  val temperature: String,
  val descriptionRes: Int,
  val locationText: String?,
  val icon: AppCardMedia,
)
