package com.karandaev.fieldfolio.core.datarefresh

import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.domain.model.persisted.LastKnownLocation
import com.karandaev.fieldfolio.core.domain.repository.PersistedAppStateRepository
import com.karandaev.fieldfolio.core.location.AppLocation
import com.karandaev.fieldfolio.core.location.provider.DeviceLocationProvider
import com.karandaev.fieldfolio.core.weather.WeatherInfo
import com.karandaev.fieldfolio.core.weather.WeatherProvider
import com.karandaev.fieldfolio.core.weather.openmeteo.WeatherCacheKey
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val LOCATION_POLL_MS = 30_000L
private const val LOCATION_FRESH_MS = 120_000L
private const val WEATHER_FRESH_MS = 20 * 60_000L
private const val FAILURE_RETRY_MS = 60_000L
private const val RELEASE_GRACE_MS = 7_000L
private const val WEATHER_ENTRY_DELAY_MS = 350L
private const val WEATHER_RECHECK_MS = 60_000L
private const val MANUAL_WEATHER_COOLDOWN_MS = 10_000L
private const val MANUAL_LOCATION_COOLDOWN_MS = 2_000L

/** A fix retains its Android measurement time even when permissions or the GPS signal disappear. */
data class DeviceFixState(
  val location: AppLocation? = null,
  val permissionGranted: Boolean = false,
  val unavailable: Boolean = false,
  val isRefreshing: Boolean = false,
  val ageCheckMillis: Long = 0L,
) {
  val point: MapPoint?
    get() = location?.let { MapPoint(it.latitude, it.longitude) }

  fun isCurrent(nowMillis: Long = System.currentTimeMillis()): Boolean =
    permissionGranted && !unavailable && location?.timestampMillis?.let { measured ->
      nowMillis - measured in 0..LOCATION_FRESH_MS
    } == true
}

data class WeatherReading(
  val value: WeatherInfo? = null,
  /** Time the coordinator obtained this reading (not necessarily Open-Meteo's observation time). */
  val fetchedAtMillis: Long = 0L,
  val isRefreshing: Boolean = false,
  val error: Boolean = false,
  val nextManualAllowedAtMillis: Long = 0L,
) {
  fun isStale(nowMillis: Long = System.currentTimeMillis()): Boolean =
    value != null && (error || fetchedAtMillis <= 0 || nowMillis - fetchedAtMillis >= WEATHER_FRESH_MS)
}

/**
 * A single process-wide owner for location and weather work.
 *
 * A UI subscription NEVER owns a network request: leaving a Lazy list cannot cancel a request,
 * and returning cannot create a second request for the same resource. In-flight work is shared by
 * automatic polling, pull-to-refresh and the weather card's refresh button.
 */
class ResourceCoordinator(
  private val device: DeviceLocationProvider,
  private val weather: WeatherProvider,
  private val persisted: PersistedAppStateRepository,
  private val hasPermission: () -> Boolean,
  private val clock: () -> Long = System::currentTimeMillis,
  private val pollIntervalMillis: Long = LOCATION_POLL_MS,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val weatherConcurrency = Semaphore(3)
  private val lock = Any()
  private val _location = MutableStateFlow(DeviceFixState())
  val location: StateFlow<DeviceFixState> = _location

  private var locationUsers = 0
  private var locationPolling: kotlinx.coroutines.Job? = null
  private var locationStop: kotlinx.coroutines.Job? = null
  private var locationInFlight: LocationRequest? = null
  private var queuedForcedLocation: Deferred<Unit>? = null
  private var lastLocationAttemptAt = Long.MIN_VALUE
  private var lastManualLocationFinishedAt = Long.MIN_VALUE
  private var pinnedDeviceWeatherPoint: MapPoint? = null

  private data class LocationRequest(val forced: Boolean, val work: Deferred<Unit>)

  private class WeatherSlot(val point: MapPoint) {
    val state = MutableStateFlow(WeatherReading())
    var users = 0
    var lastAttemptFinishedAt = Long.MIN_VALUE
    var lastManualFinishedAt = Long.MIN_VALUE
    var inFlight: WeatherRequest? = null
    var queuedForce: Deferred<Unit>? = null
    var poll: kotlinx.coroutines.Job? = null
    var initialLoad: kotlinx.coroutines.Job? = null
    var stop: kotlinx.coroutines.Job? = null
  }

  private data class WeatherRequest(val forced: Boolean, val work: Deferred<Unit>)

  private val weatherStates = ConcurrentHashMap<WeatherCacheKey, WeatherSlot>()

  fun close() { scope.cancel() }

  init {
    scope.launch {
      persisted.observeLastKnownLocation().collect { saved ->
        if (saved != null) {
          val previous = _location.value.location?.timestampMillis ?: 0L
          if (saved.updatedAt > previous) {
            _location.value = _location.value.copy(
              location = AppLocation(saved.latitude, saved.longitude, timestampMillis = saved.updatedAt),
            )
          }
        }
      }
    }
  }

  /** Also called after a permission grant and when returning from Android Settings. */
  fun permissionChanged() {
    val granted = hasPermission()
    val previous = _location.value.permissionGranted
    _location.value = _location.value.copy(permissionGranted = granted, unavailable = !granted)
    if (granted && !previous && synchronized(lock) { locationUsers > 0 }) {
      // A grant must not be blocked by a cooldown from a previous denied attempt.
      synchronized(lock) { lastManualLocationFinishedAt = Long.MIN_VALUE }
      scope.launch { refreshLocation(force = true) }
    }
  }

  fun watchLocation(): AutoCloseable {
    synchronized(lock) {
      locationStop?.cancel()
      locationUsers++
      if (locationPolling?.isActive != true) {
        locationPolling = scope.launch {
          val wasGranted = _location.value.permissionGranted
          permissionChanged()
          if (wasGranted) refreshLocation(force = false)
          while (true) {
            delay(pollIntervalMillis)
            if (synchronized(lock) { locationUsers > 0 }) refreshLocation(force = false)
          }
        }
      }
    }
    val closed = AtomicBoolean(false)
    return AutoCloseable {
      if (!closed.compareAndSet(false, true)) return@AutoCloseable
      synchronized(lock) {
        locationUsers = (locationUsers - 1).coerceAtLeast(0)
        if (locationUsers == 0) {
          locationStop?.cancel()
          locationStop = scope.launch {
            delay(RELEASE_GRACE_MS)
            synchronized(lock) {
              if (locationUsers == 0) {
                locationPolling?.cancel()
                locationPolling = null
              }
            }
          }
        }
      }
    }
  }

  /**
   * A manual fix bypasses the Android location cache. Multiple callers join the same attempt.
   * If an automatic (potentially cached) read is in progress, exactly ONE forced read follows it.
   * UI cancellation never cancels the shared provider request.
   */
  suspend fun refreshLocation(force: Boolean = true) = refreshLocationInternal(force, skipPending = false)

  private suspend fun refreshLocationInternal(force: Boolean, skipPending: Boolean) {
    val operation: Deferred<Unit>? = synchronized(lock) {
      val running = locationInFlight?.takeIf { !it.work.isCompleted }
      val pending = queuedForcedLocation?.takeIf { !it.isCompleted }
      when {
        force && !skipPending && pending != null -> pending
        running != null && (!force || running.forced) -> running.work
        running != null -> {
          queuedForcedLocation?.takeIf { !it.isCompleted } ?: run {
            val queued = scope.async(start = CoroutineStart.LAZY) {
              try { running.work.await() } catch (_: CancellationException) { /* scope is closing */ }
              refreshLocationInternal(force = true, skipPending = true)
            }
            queuedForcedLocation = queued
            queued.invokeOnCompletion {
              synchronized(lock) { if (queuedForcedLocation === queued) queuedForcedLocation = null }
            }
            queued.start()
            queued
          }
        }
        force && lastManualLocationFinishedAt != Long.MIN_VALUE &&
          clock() - lastManualLocationFinishedAt in 0 until MANUAL_LOCATION_COOLDOWN_MS -> null
        !force && lastLocationAttemptAt != Long.MIN_VALUE &&
          clock() - lastLocationAttemptAt in 0 until pollIntervalMillis -> null
        else -> {
          lastLocationAttemptAt = clock()
          val request = scope.async(start = CoroutineStart.LAZY) { obtainLocation(force) }
          locationInFlight = LocationRequest(force, request)
          request.invokeOnCompletion {
            synchronized(lock) { if (locationInFlight?.work === request) locationInFlight = null }
          }
          request.start()
          request
        }
      }
    }
    operation?.await()
  }

  private suspend fun obtainLocation(force: Boolean) {
    val now = clock()
    _location.value = _location.value.copy(ageCheckMillis = now)
    if (!hasPermission()) {
      _location.value = _location.value.copy(permissionGranted = false, unavailable = true)
      return
    }
    _location.value = _location.value.copy(permissionGranted = true, isRefreshing = true)
    try {
      val fix = if (force) device.getFreshLocation() else device.getLocation()
      if (fix == null || fix.isDefault) {
        _location.value = _location.value.copy(unavailable = true)
      } else {
        val measuredAt = fix.timestampMillis ?: now
        val current = _location.value.location
        if (current?.timestampMillis == null || measuredAt >= current.timestampMillis) {
          val measured = fix.copy(timestampMillis = measuredAt)
          _location.value = _location.value.copy(location = measured, unavailable = false)
          if (current?.timestampMillis != measuredAt || current.latitude != measured.latitude ||
            current.longitude != measured.longitude) {
            // A failed persistence write should not throw away the valid in-memory GPS fix.
            runCatching {
              persisted.setLastKnownLocation(
                LastKnownLocation(MapPoint(measured.latitude, measured.longitude), "", measuredAt)
              )
            }
          }
        }
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      _location.value = _location.value.copy(unavailable = true)
    } finally {
      if (force) synchronized(lock) { lastManualLocationFinishedAt = clock() }
      _location.value = _location.value.copy(isRefreshing = false)
    }
  }

  private fun slot(point: MapPoint): WeatherSlot =
    weatherStates.computeIfAbsent(WeatherCacheKey.from(point.latitude, point.longitude)) {
      WeatherSlot(point)
    }

  fun weatherAt(point: MapPoint): StateFlow<WeatherReading> = slot(point).state

  fun watchWeather(point: MapPoint): AutoCloseable {
    val entry = slot(point)
    synchronized(lock) {
      entry.stop?.cancel()
      entry.users++
      if (entry.users == 1) {
        entry.initialLoad?.cancel()
        entry.initialLoad = scope.launch {
          delay(WEATHER_ENTRY_DELAY_MS)
          if (synchronized(lock) { entry.users > 0 }) refreshWeather(entry.point, force = false)
        }
      }
      if (entry.poll?.isActive != true) {
        entry.poll = scope.launch {
          while (true) {
            delay(WEATHER_RECHECK_MS)
            if (synchronized(lock) { entry.users > 0 }) refreshWeather(entry.point, force = false)
          }
        }
      }
    }
    val closed = AtomicBoolean(false)
    return AutoCloseable {
      if (!closed.compareAndSet(false, true)) return@AutoCloseable
      synchronized(lock) {
        entry.users = (entry.users - 1).coerceAtLeast(0)
        if (entry.users == 0) {
          // Cancels only the short debounce, never a started HTTP request.
          entry.initialLoad?.cancel()
          entry.stop?.cancel()
          entry.stop = scope.launch {
            delay(RELEASE_GRACE_MS)
            synchronized(lock) {
              if (entry.users == 0) {
                entry.poll?.cancel()
                entry.poll = null
              }
            }
          }
        }
      }
    }
  }

  /**
   * Single flight for a rounded weather coordinate, across ALL windows and refresh triggers.
   * The cooldown is measured from completion (not from the start of a slow request).
   */
  suspend fun refreshWeather(point: MapPoint, force: Boolean = true) =
    refreshWeatherInternal(point, force, skipPending = false)

  private suspend fun refreshWeatherInternal(point: MapPoint, force: Boolean, skipPending: Boolean) {
    val entry = slot(point)
    val operation: Deferred<Unit>? = synchronized(lock) {
      val running = entry.inFlight?.takeIf { !it.work.isCompleted }
      val pending = entry.queuedForce?.takeIf { !it.isCompleted }
      when {
        force && !skipPending && pending != null -> pending
        running != null && (!force || running.forced) -> running.work
        running != null -> {
          // An AUTO read may return a cached value. The first manual request schedules
          // exactly one force-read after it; all later manual callers join that work.
          entry.queuedForce?.takeIf { !it.isCompleted } ?: run {
            val queued = scope.async(start = CoroutineStart.LAZY) {
              try { running.work.await() } catch (_: CancellationException) { /* closing */ }
              refreshWeatherInternal(point, force = true, skipPending = true)
            }
            entry.queuedForce = queued
            queued.invokeOnCompletion {
              synchronized(lock) { if (entry.queuedForce === queued) entry.queuedForce = null }
            }
            queued.start()
            queued
          }
        }
        else -> {
          val now = clock()
          val reading = entry.state.value
          val recentlyForced = entry.lastManualFinishedAt != Long.MIN_VALUE &&
            now - entry.lastManualFinishedAt in 0 until MANUAL_WEATHER_COOLDOWN_MS
          val fresh = reading.value != null && !reading.error &&
            now - reading.fetchedAtMillis in 0 until WEATHER_FRESH_MS
          val retryTooSoon = entry.lastAttemptFinishedAt != Long.MIN_VALUE &&
            now - entry.lastAttemptFinishedAt in 0 until FAILURE_RETRY_MS
          when {
            force && recentlyForced -> null
            !force && (fresh || retryTooSoon || entry.users <= 0) -> null
            else -> {
              val request = scope.async(start = CoroutineStart.LAZY) { obtainWeather(entry, force) }
              entry.inFlight = WeatherRequest(force, request)
              entry.state.value = reading.copy(isRefreshing = true, error = false)
              request.invokeOnCompletion {
                synchronized(lock) { if (entry.inFlight?.work === request) entry.inFlight = null }
              }
              request.start()
              request
            }
          }
        }
      }
    }
    operation?.await()
  }

  private suspend fun obtainWeather(entry: WeatherSlot, force: Boolean) {
    var attempted = false
    try {
      // The waiting queue may contain many different coordinates from a fast Lazy list scroll.
      // After receiving the global permit, discard off-screen AUTOMATIC work before HTTP begins.
      weatherConcurrency.withPermit {
        if (!force && synchronized(lock) { entry.users <= 0 }) return@withPermit
        attempted = true
        val result = if (force) weather.refreshWeather(entry.point.latitude, entry.point.longitude)
          else weather.getWeather(entry.point.latitude, entry.point.longitude)
        result.onSuccess {
          entry.state.value = WeatherReading(
            value = it,
            fetchedAtMillis = clock(),
            nextManualAllowedAtMillis = entry.state.value.nextManualAllowedAtMillis,
          )
        }.onFailure {
          entry.state.value = entry.state.value.copy(error = true)
        }
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      entry.state.value = entry.state.value.copy(error = true)
    } finally {
      synchronized(lock) {
        if (attempted) {
          entry.lastAttemptFinishedAt = clock()
          if (force) entry.lastManualFinishedAt = clock()
        }
      }
      entry.state.value = entry.state.value.copy(
        isRefreshing = false,
        nextManualAllowedAtMillis = if (force && attempted) clock() + MANUAL_WEATHER_COOLDOWN_MS
          else entry.state.value.nextManualAllowedAtMillis,
      )
    }
  }

  /** Stable across Lazy list disposal: ordinary GPS jitter must not create new weather keys. */
  fun weatherPointForDevice(fix: MapPoint?): MapPoint? = synchronized(lock) {
    if (fix == null) return@synchronized pinnedDeviceWeatherPoint
    val previous = pinnedDeviceWeatherPoint
    if (previous == null || separationMeters(previous, fix) >= 1_000.0) {
      pinnedDeviceWeatherPoint = fix
    }
    pinnedDeviceWeatherPoint
  }

  private fun separationMeters(a: MapPoint, b: MapPoint): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 12_742_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
  }

  /**
   * Device Weather first tries to refresh GPS, then loads weather for the resulting position.
   * If GPS is denied or unavailable, the stored last fix is still a valid *stale* destination:
   * weather must not disappear just because the device cannot currently obtain a new fix.
   * The UI remains responsible for labeling that destination as last known, never current.
   */
  suspend fun refreshDeviceWeather() {
    refreshLocation(force = true)
    weatherPointForDevice(location.value.point)?.let { refreshWeather(it, force = true) }
  }
}
