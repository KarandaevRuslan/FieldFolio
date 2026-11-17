package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.datarefresh.rememberDeviceFix
import com.karandaev.fieldfolio.core.datarefresh.RegisterDeviceWeatherRefresh
import com.karandaev.fieldfolio.core.datarefresh.rememberWeatherReading
import com.karandaev.fieldfolio.core.datarefresh.rememberWeatherPoint
import com.karandaev.fieldfolio.core.datarefresh.rememberDeviceTimeZone
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.tr
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
  val liveDevice =
    if (node.deviceLocation && node.kind in setOf(BlockKind.LOCATION, BlockKind.WEATHER))
      rememberDeviceFix(includeInPull = node.kind == BlockKind.LOCATION)
    else null
  if (node.kind == BlockKind.WEATHER && node.deviceLocation) {
    // Still refreshable while permission is pending or no GPS fix is available.
    RegisterDeviceWeatherRefresh()
  }
  val point =
    if (node.deviceLocation) liveDevice?.point ?: device
    else node.latitude?.let { lat -> node.longitude?.let { MapPoint(lat, it) } }
  when (node.kind) {
    BlockKind.CLOCK -> {
      val zone =
        if (node.deviceLocation) rememberDeviceTimeZone()
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
      else {
        Text("${point.latitude}, ${point.longitude}", style = MaterialTheme.typography.bodyLarge)
        if (liveDevice != null && !liveDevice.isCurrent()) {
          Text(androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.map_my_last_location),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error)
        }
      }
    }
    BlockKind.WEATHER -> {
      if (point == null) {
        LocationMissing(permission)
        return
      }
      val app = rememberAppServices()
      val scope = rememberCoroutineScope()
      val weatherPoint = if (node.deviceLocation) rememberWeatherPoint(point) else point
      // A measured-but-old device fix is still a usable WEATHER destination. We show its
      // age/status separately instead of treating missing live GPS as missing coordinates.
      // Keeping this subscription active also lets the shared weather cache load the saved point.
      val reading = rememberWeatherReading(
        weatherPoint,
        active = weatherPoint != null,
        includeInPull = !node.deviceLocation,
      )
      val place =
        com.karandaev.fieldfolio.core.location.LocatedPlace(
          com.karandaev.fieldfolio.core.location.AppLocation(point.latitude, point.longitude),
          node.visualTitle,
        )
      val state =
        if (reading.error && reading.value == null)
          com.karandaev.fieldfolio.feature.weather.state.WeatherUiState.Error(tr("error"))
        else reading.value?.let {
          com.karandaev.fieldfolio.feature.weather.state.WeatherUiState.Success(it)
        } ?: com.karandaev.fieldfolio.feature.weather.state.WeatherUiState.Loading
      if (node.deviceLocation && liveDevice?.isCurrent() != true) {
        Text(
          androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.map_my_last_location),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
      // Age changes even when no HTTP request occurs. A stale reading must not look fresh.
      var ageNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
      LaunchedEffect(reading.value, reading.nextManualAllowedAtMillis) {
        while (isActive) {
          ageNow = System.currentTimeMillis()
          delay(if (reading.nextManualAllowedAtMillis > ageNow) 1_000 else 60_000)
        }
      }
      val weatherStatus = when {
        reading.error && reading.value != null ->
          androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.weather_previous_after_error)
        reading.isStale(ageNow) ->
          androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.weather_previous_stale)
        else -> null
      }
      com.karandaev.fieldfolio.feature.shared.components.cards.WeatherCard(
        com.karandaev.fieldfolio.feature.map.location.LocationUiState.Success(place),
        state,
        modifier = androidx.compose.ui.Modifier.fillMaxWidth().testTag("document-weather-widget"),
        isRefreshing = reading.isRefreshing || (node.deviceLocation && liveDevice?.isRefreshing == true),
        refreshEnabled = ageNow >= reading.nextManualAllowedAtMillis,
        statusText = weatherStatus,
        onRetry = {
          scope.launch {
            if (node.deviceLocation) {
              app.resourceCoordinator.refreshDeviceWeather()
            } else {
              weatherPoint?.let { app.resourceCoordinator.refreshWeather(it, force = true) }
            }
          }
        },
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
