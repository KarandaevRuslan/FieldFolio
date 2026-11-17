package com.karandaev.fieldfolio.core.datarefresh

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.weather.openmeteo.WeatherCacheKey
import kotlinx.coroutines.awaitCancellation

private object DeviceRefreshKey

private object DeviceWeatherRefreshKey

/** Registers a GPS need only when actively composed and the screen is RESUMED. */
@Composable
fun rememberDeviceFix(active: Boolean = true, includeInPull: Boolean = true): DeviceFixState {
  val coordinator = rememberAppServices().resourceCoordinator
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val value by coordinator.location.collectAsStateWithLifecycle()

  LaunchedEffect(coordinator, lifecycle, active) {
    if (active) {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        coordinator.permissionChanged()
        val subscription = coordinator.watchLocation()
        try {
          awaitCancellation()
        } finally {
          subscription.close()
        }
      }
    }
  }
  if (includeInPull && active) {
    RegisterRefreshNeed(DeviceRefreshKey) { coordinator.refreshLocation(force = true) }
  }
  return value
}

/** One coordinator entry is shared by all Weather widgets for the same rounded point. */
@Composable
fun rememberWeatherReading(
  point: MapPoint?,
  active: Boolean = true,
  includeInPull: Boolean = true,
): WeatherReading {
  val coordinator = rememberAppServices().resourceCoordinator
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val key = point?.let { WeatherCacheKey.from(it.latitude, it.longitude) }
  val flow = remember(coordinator, key) { point?.let { coordinator.weatherAt(it) } }
  val reading by
    (flow ?: remember { kotlinx.coroutines.flow.MutableStateFlow(WeatherReading()) })
      .collectAsStateWithLifecycle()
  LaunchedEffect(coordinator, lifecycle, key, active) {
    if (active && point != null) {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        val subscription = coordinator.watchWeather(point)
        try {
          awaitCancellation()
        } finally {
          subscription.close()
        }
      }
    }
  }
  if (active && includeInPull && key != null && point != null) {
    RegisterRefreshNeed(key) { coordinator.refreshWeather(point, force = true) }
  }
  return reading
}

/** The device-weather key is owned by the coordinator, so Lazy list remounts retain it. */
@Composable
fun rememberWeatherPoint(devicePoint: MapPoint?): MapPoint? =
  rememberAppServices().resourceCoordinator.weatherPointForDevice(devicePoint)

/** A device-weather refresh is one dependency chain, not two parallel pull actions. */
@Composable
fun RegisterDeviceWeatherRefresh(active: Boolean = true) {
  val coordinator = rememberAppServices().resourceCoordinator
  if (active) RegisterRefreshNeed(DeviceWeatherRefreshKey) { coordinator.refreshDeviceWeather() }
}
