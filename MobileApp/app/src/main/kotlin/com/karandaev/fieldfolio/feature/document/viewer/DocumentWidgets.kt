package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.core.weather.WeatherInfo
import com.karandaev.fieldfolio.feature.map.location.LocationUiState
import java.time.*
import java.time.format.*
import kotlinx.coroutines.*

@Composable
fun DocumentLiveWidget(
  node: DocumentNode,
  device: MapPoint?,
  refresh: Long,
  permission: () -> Unit,
) {
  val point =
    if (node.deviceLocation) device
    else node.latitude?.let { lat -> node.longitude?.let { MapPoint(lat, it) } }
  when (node.kind) {
    BlockKind.CLOCK -> {
      val zone =
        if (node.deviceLocation) ZoneId.systemDefault()
        else runCatching { ZoneId.of(node.zoneId) }.getOrNull()
      if (zone != null)
        com.karandaev.fieldfolio.feature.home.components.HomeDigitalClockCard(
          modifier = androidx.compose.ui.Modifier.testTag("document-clock-widget"),
          zoneId = zone,
          title = node.visualTitle,
        )
      else Text(tr("zone"))
      Text(zone?.id.orEmpty(), style = MaterialTheme.typography.labelMedium)
    }
    BlockKind.LOCATION -> {
      if (point == null) LocationMissing(permission)
      else Text("${point.latitude}, ${point.longitude}", style = MaterialTheme.typography.bodyLarge)
    }
    BlockKind.WEATHER -> {
      if (point == null) {
        LocationMissing(permission)
        return
      }
      val app = rememberAppServices()
      var retry by
        com.karandaev.fieldfolio.core.ui.state.rememberViewerSession("weather-retry", node.id) {
          mutableLongStateOf(0)
        }
      var issue by
        com.karandaev.fieldfolio.core.ui.state.rememberViewerSession("weather-error", node.id) {
          mutableStateOf(false)
        }
      var weather by
        com.karandaev.fieldfolio.core.ui.state.rememberViewerSession(
          "weather-info",
          node.id,
          point,
        ) {
          mutableStateOf<WeatherInfo?>(null)
        }
      var completedRequest by
        com.karandaev.fieldfolio.core.ui.state.rememberViewerSession(
          "weather-request",
          node.id,
          point,
        ) {
          mutableStateOf<Long?>(null)
        }
      LaunchedEffect(point, refresh, retry) {
        val request = refresh + retry
        if (completedRequest == request && (weather != null || issue)) return@LaunchedEffect
        issue = false
        val next =
          (if (refresh + retry > 0)
              app.weatherProvider.refreshWeather(point.latitude, point.longitude)
            else app.weatherProvider.getWeather(point.latitude, point.longitude))
            .onFailure { issue = true }
            .getOrNull()
        if (next != null || weather == null) weather = next
        completedRequest = request
      }
      val place =
        com.karandaev.fieldfolio.core.location.LocatedPlace(
          com.karandaev.fieldfolio.core.location.AppLocation(point.latitude, point.longitude),
          node.visualTitle,
        )
      val state =
        if (issue) com.karandaev.fieldfolio.feature.weather.state.WeatherUiState.Error(tr("error"))
        else
          weather?.let { com.karandaev.fieldfolio.feature.weather.state.WeatherUiState.Success(it) }
            ?: com.karandaev.fieldfolio.feature.weather.state.WeatherUiState.Loading
      com.karandaev.fieldfolio.feature.shared.components.cards.WeatherCard(
        com.karandaev.fieldfolio.feature.map.location.LocationUiState.Success(place),
        state,
        modifier = androidx.compose.ui.Modifier.fillMaxWidth().testTag("document-weather-widget"),
        onRetry = { retry++ },
      )
    }
    else -> Unit
  }
}

@Composable
private fun LocationMissing(permission: () -> Unit) {
  Text(tr("location_missing"))
  TextButton(permission) { Text(tr("grant_location")) }
}
