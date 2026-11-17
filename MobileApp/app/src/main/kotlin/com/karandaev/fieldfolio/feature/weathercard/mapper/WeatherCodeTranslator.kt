package com.karandaev.fieldfolio.feature.weathercard.mapper

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import com.karandaev.fieldfolio.core.ui.components.card.AppCardMedia

/** UI interpretation of a weather code. */
data class WeatherCodeUi(val descriptionRes: Int, val icon: AppCardMedia)

/**
 * Maps weather provider codes to UI labels and icons.
 *
 * Open-Meteo uses WMO weather interpretation codes.
 */
fun Int.toWeatherCodeUi(): WeatherCodeUi {
  return when (this) {
    0 ->
      WeatherCodeUi(
        descriptionRes = com.karandaev.fieldfolio.R.string.ui_weather_clear,
        icon = AppCardMedia.Icon(Icons.Default.Cloud),
      )

    1,
    2,
    3 ->
      WeatherCodeUi(
        descriptionRes = com.karandaev.fieldfolio.R.string.ui_weather_cloudy,
        icon = AppCardMedia.Icon(Icons.Default.Cloud),
      )

    45,
    48 ->
      WeatherCodeUi(
        descriptionRes = com.karandaev.fieldfolio.R.string.ui_weather_fog,
        icon = AppCardMedia.Icon(Icons.Default.Cloud),
      )

    51,
    53,
    55,
    56,
    57 ->
      WeatherCodeUi(
        descriptionRes = com.karandaev.fieldfolio.R.string.ui_weather_drizzle,
        icon = AppCardMedia.Icon(Icons.Default.WaterDrop),
      )

    61,
    63,
    65,
    66,
    67,
    80,
    81,
    82 ->
      WeatherCodeUi(
        descriptionRes = com.karandaev.fieldfolio.R.string.ui_weather_rain,
        icon = AppCardMedia.Icon(Icons.Default.Umbrella),
      )

    71,
    73,
    75,
    77,
    85,
    86 ->
      WeatherCodeUi(
        descriptionRes = com.karandaev.fieldfolio.R.string.ui_weather_snow,
        icon = AppCardMedia.Icon(Icons.Default.Grain),
      )

    95,
    96,
    99 ->
      WeatherCodeUi(
        descriptionRes = com.karandaev.fieldfolio.R.string.ui_weather_thunderstorm,
        icon = AppCardMedia.Icon(Icons.Default.Thunderstorm),
      )

    else ->
      WeatherCodeUi(
        descriptionRes = com.karandaev.fieldfolio.R.string.ui_weather_unknown,
        icon = AppCardMedia.Icon(Icons.Default.Cloud),
      )
  }
}
