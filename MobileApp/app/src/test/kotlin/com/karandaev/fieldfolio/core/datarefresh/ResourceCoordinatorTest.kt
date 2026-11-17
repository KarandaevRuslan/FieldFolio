package com.karandaev.fieldfolio.core.datarefresh

import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.domain.model.persisted.AppAccessState
import com.karandaev.fieldfolio.core.domain.model.persisted.LastKnownLocation
import com.karandaev.fieldfolio.core.domain.repository.PersistedAppStateRepository
import com.karandaev.fieldfolio.core.location.AppLocation
import com.karandaev.fieldfolio.core.location.provider.DeviceLocationProvider
import com.karandaev.fieldfolio.core.weather.WeatherInfo
import com.karandaev.fieldfolio.core.weather.WeatherProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ResourceCoordinatorTest {
  private class Persistence : PersistedAppStateRepository {
    val last = MutableStateFlow<LastKnownLocation?>(null)

    override fun observeLastKnownLocation(): Flow<LastKnownLocation?> = last

    override suspend fun setLastKnownLocation(location: LastKnownLocation?) {
      last.value = location
    }

    override fun observeAppAccessState(): Flow<AppAccessState> = MutableStateFlow(AppAccessState())

    override suspend fun setAppAccessState(state: AppAccessState) = Unit
  }

  private class Location : DeviceLocationProvider {
    val automatic = AtomicInteger()
    val forced = AtomicInteger()
    var current: AppLocation? = null
    var responseDelayMs = 0L

    override suspend fun getLocation(): AppLocation? {
      automatic.incrementAndGet()
      if (responseDelayMs > 0) delay(responseDelayMs)
      return current
    }

    override suspend fun getFreshLocation(): AppLocation? {
      forced.incrementAndGet()
      if (responseDelayMs > 0) delay(responseDelayMs)
      return current
    }
  }

  private class Weather : WeatherProvider {
    val count = AtomicInteger()
    var fail = false
    var responseDelayMs = 50L

    override suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherInfo> {
      count.incrementAndGet()
      delay(responseDelayMs)
      return if (fail) Result.failure(IllegalStateException("offline"))
      else Result.success(WeatherInfo(18.0, 1))
    }

    override suspend fun refreshWeather(latitude: Double, longitude: Double) =
      getWeather(latitude, longitude)
  }

  @Test
  fun identicalWeatherNeedsShareOneRequestEvenAcrossRapidRemounts() = runBlocking {
    val weather = Weather()
    val coordinator = ResourceCoordinator(Location(), weather, Persistence(), { true })
    val point = MapPoint(52.123456, 5.123456)
    try {
      val handles = (1..10).map { coordinator.watchWeather(point) }
      withTimeout(3_000) { coordinator.weatherAt(point).first { it.value != null } }
      handles.forEach { it.close() }
      repeat(15) { coordinator.watchWeather(point).close() }
      delay(600)
      assertEquals(1, weather.count.get())
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun brieflyComposedWeatherDoesNotRequestNetwork() = runBlocking {
    val weather = Weather()
    val coordinator = ResourceCoordinator(Location(), weather, Persistence(), { true })
    try {
      repeat(10) { coordinator.watchWeather(MapPoint(51.0, 4.0)).close() }
      delay(600)
      assertEquals(0, weather.count.get())
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun simultaneousManualWeatherRequestsAreCoalesced() = runBlocking {
    val weather = Weather()
    val coordinator = ResourceCoordinator(Location(), weather, Persistence(), { true })
    try {
      (1..12)
        .map { async { coordinator.refreshWeather(MapPoint(52.0, 5.0), force = true) } }
        .awaitAll()
      assertEquals(1, weather.count.get())
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun staleFixIsRetainedThroughPermissionLossAndForcedFailure() = runBlocking {
    val location = Location()
    val persistence = Persistence()
    val now = System.currentTimeMillis()
    persistence.last.value = LastKnownLocation(MapPoint(50.0, 4.0), "", now - 3_600_000L)
    var permission = false
    val coordinator = ResourceCoordinator(location, Weather(), persistence, { permission })
    try {
      withTimeout(2_000) { coordinator.location.first { it.location != null } }
      assertFalse(coordinator.location.value.isCurrent(now))
      assertEquals(MapPoint(50.0, 4.0), coordinator.location.value.point)
      permission = true
      coordinator.permissionChanged()
      coordinator.refreshLocation(force = true)
      assertEquals(MapPoint(50.0, 4.0), coordinator.location.value.point)
      assertFalse(coordinator.location.value.isCurrent(now))
      location.current = AppLocation(51.0, 5.0, timestampMillis = now)
      coordinator.refreshLocation(force = false)
      // Force refresh is separate from the cached read, never fakes a new measuredAt.
      assertEquals(now - 3_600_000L, persistence.last.value?.updatedAt)
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun oldSavedFixStillLoadsWeatherWhenGpsCannotUpdate() = runBlocking {
    val lastMeasured = System.currentTimeMillis() - 3_600_000L
    val savedPoint = MapPoint(50.0, 4.0)
    val persistence =
      Persistence().also { it.last.value = LastKnownLocation(savedPoint, "", lastMeasured) }
    val device = Location() // GPS provider returns null.
    val weather = Weather()
    val coordinator = ResourceCoordinator(device, weather, persistence, { true })
    try {
      withTimeout(2_000) { coordinator.location.first { it.point == savedPoint } }
      coordinator.refreshDeviceWeather()
      assertEquals(1, device.forced.get())
      assertEquals(1, weather.count.get())
      assertTrue(coordinator.weatherAt(savedPoint).value.value != null)
      assertFalse(coordinator.location.value.isCurrent())
      assertEquals(lastMeasured, persistence.last.value?.updatedAt)
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun noPermissionStillAllowsWeatherForPreviouslyMeasuredFix() = runBlocking {
    val lastMeasured = System.currentTimeMillis() - 86_400_000L
    val savedPoint = MapPoint(48.0, 2.0)
    val persistence =
      Persistence().also { it.last.value = LastKnownLocation(savedPoint, "", lastMeasured) }
    val device = Location()
    val weather = Weather()
    val coordinator = ResourceCoordinator(device, weather, persistence, { false })
    try {
      withTimeout(2_000) { coordinator.location.first { it.point == savedPoint } }
      coordinator.refreshDeviceWeather()
      assertEquals(0, device.forced.get())
      assertEquals(1, weather.count.get())
      assertTrue(coordinator.weatherAt(savedPoint).value.value != null)
      assertFalse(coordinator.location.value.isCurrent())
      assertEquals(lastMeasured, persistence.last.value?.updatedAt)
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun noMeasuredOrSavedFixNeverRequestsDeviceWeather() = runBlocking {
    val weather = Weather()
    val coordinator = ResourceCoordinator(Location(), weather, Persistence(), { false })
    try {
      coordinator.refreshDeviceWeather()
      assertEquals(0, weather.count.get())
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun newFixReplacesOldWeatherDestinationBeforeNetworkRequest() = runBlocking {
    val now = System.currentTimeMillis()
    val oldPoint = MapPoint(50.0, 4.0)
    val newPoint = MapPoint(52.0, 5.0)
    val persistence =
      Persistence().also { it.last.value = LastKnownLocation(oldPoint, "", now - 3_600_000L) }
    val device =
      Location().also {
        it.current = AppLocation(newPoint.latitude, newPoint.longitude, timestampMillis = now)
      }
    val weather = Weather()
    val coordinator = ResourceCoordinator(device, weather, persistence, { true })
    try {
      withTimeout(2_000) { coordinator.location.first { it.point == oldPoint } }
      coordinator.refreshDeviceWeather()
      assertEquals(1, weather.count.get())
      assertTrue(coordinator.weatherAt(oldPoint).value.value == null)
      assertTrue(coordinator.weatherAt(newPoint).value.value != null)
      assertEquals(newPoint, coordinator.location.value.point)
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun repeatSubscriptionsUseOneGpsPollingLoop() = runBlocking {
    val location = Location()
    location.current = AppLocation(52.0, 5.0, timestampMillis = System.currentTimeMillis())
    val coordinator = ResourceCoordinator(location, Weather(), Persistence(), { true })
    try {
      val handles = (1..8).map { coordinator.watchLocation() }
      withTimeout(2_000) { coordinator.location.first { it.isCurrent() } }
      assertEquals(1, location.forced.get())
      handles.forEach { it.close() }
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun failedWeatherDoesNotRetryOnEveryScrollRecomposition() = runBlocking {
    val weather = Weather().also { it.fail = true }
    val coordinator = ResourceCoordinator(Location(), weather, Persistence(), { true })
    val point = MapPoint(51.5, -0.12)
    try {
      val handle = coordinator.watchWeather(point)
      withTimeout(3_000) { coordinator.weatherAt(point).first { it.error } }
      handle.close()
      repeat(15) { coordinator.watchWeather(point).close() }
      delay(600)
      assertEquals(1, weather.count.get())
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun slowWeatherManualSpamJoinsOneRequestAndCooldownStartsAfterCompletion() = runBlocking {
    val weather = Weather().also { it.responseDelayMs = 2_350L }
    val coordinator = ResourceCoordinator(Location(), weather, Persistence(), { true })
    val point = MapPoint(52.0, 5.0)
    try {
      (1..12).map { async { coordinator.refreshWeather(point, force = true) } }.awaitAll()
      assertEquals(1, weather.count.get())
      assertFalse(coordinator.weatherAt(point).value.isRefreshing)
      coordinator.refreshWeather(point, force = true)
      assertEquals("manual cooldown must start AFTER the slow request", 1, weather.count.get())
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun leavingLazyListDuringHttpDoesNotCancelWeatherOrStickLoading() = runBlocking {
    val weather = Weather().also { it.responseDelayMs = 650L }
    val coordinator = ResourceCoordinator(Location(), weather, Persistence(), { true })
    val point = MapPoint(52.0, 5.0)
    try {
      val handle = coordinator.watchWeather(point)
      withTimeout(2_000) { coordinator.weatherAt(point).first { it.isRefreshing } }
      handle.close() // Simulates LazyColumn removing a row while HTTP is active.
      withTimeout(3_000) { coordinator.weatherAt(point).first { it.value != null } }
      assertFalse(coordinator.weatherAt(point).value.isRefreshing)
      repeat(15) { coordinator.watchWeather(point).close() }
      delay(800)
      assertEquals(1, weather.count.get())
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun manualDuringAutomaticWeatherLoadGetsOneForcedFollowUp() = runBlocking {
    val weather = Weather().also { it.responseDelayMs = 450L }
    val coordinator = ResourceCoordinator(Location(), weather, Persistence(), { true })
    val point = MapPoint(52.0, 5.0)
    try {
      val handle = coordinator.watchWeather(point)
      withTimeout(2_000) { coordinator.weatherAt(point).first { it.isRefreshing } }
      (1..8).map { async { coordinator.refreshWeather(point, force = true) } }.awaitAll()
      assertEquals("one automatic and ONE manual network call", 2, weather.count.get())
      handle.close()
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun deviceWeatherPullFetchesGpsFirstAndPinsJitterAcrossConsumers() = runBlocking {
    val device = Location()
    val now = System.currentTimeMillis()
    device.current = AppLocation(52.0, 5.0, timestampMillis = now)
    val weather = Weather()
    val coordinator = ResourceCoordinator(device, weather, Persistence(), { true })
    try {
      val point = coordinator.weatherPointForDevice(MapPoint(52.0, 5.0))
      assertEquals(point, coordinator.weatherPointForDevice(MapPoint(52.0001, 5.0001)))
      coordinator.refreshDeviceWeather()
      assertEquals(1, device.forced.get())
      assertEquals(1, weather.count.get())
      assertEquals(
        point,
        coordinator.weatherPointForDevice(
          device.current!!.let { MapPoint(it.latitude, it.longitude) }
        ),
      )
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun queuedOffscreenAutomaticWeatherDoesNotCallHttp() = runBlocking {
    val weather = Weather().also { it.responseDelayMs = 1_200L }
    val coordinator = ResourceCoordinator(Location(), weather, Persistence(), { true })
    try {
      val busy = (1..3).map { coordinator.watchWeather(MapPoint(it.toDouble(), 1.0)) }
      delay(500)
      val fleeting = (4..24).map { coordinator.watchWeather(MapPoint(it.toDouble(), 1.0)) }
      delay(450) // Briefly queued, but all rows have now gone off-screen.
      fleeting.forEach { it.close() }
      delay(2_100)
      assertTrue("only visible/started requests should hit the network", weather.count.get() <= 3)
      busy.forEach { it.close() }
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun slowForcedGpsRequestsAreSingleFlight() = runBlocking {
    val now = System.currentTimeMillis()
    val device =
      Location().also {
        it.responseDelayMs = 450L
        it.current = AppLocation(52.0, 5.0, timestampMillis = now)
      }
    val coordinator = ResourceCoordinator(device, Weather(), Persistence(), { true })
    try {
      (1..8).map { async { coordinator.refreshLocation(force = true) } }.awaitAll()
      assertEquals(1, device.forced.get())
      coordinator.refreshLocation(force = true)
      assertEquals(1, device.forced.get()) // 2 second completion-based cooldown
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun permissionGrantTriggersOneFreshGpsFixForExistingWatchers() = runBlocking {
    var permission = false
    val now = System.currentTimeMillis()
    val device = Location().also { it.current = AppLocation(52.0, 5.0, timestampMillis = now) }
    val coordinator = ResourceCoordinator(device, Weather(), Persistence(), { permission })
    try {
      val watchers = (1..5).map { coordinator.watchLocation() }
      delay(80)
      permission = true
      coordinator.permissionChanged()
      withTimeout(2_000) { coordinator.location.first { it.isCurrent() } }
      assertEquals(1, device.forced.get())
      watchers.forEach { it.close() }
    } finally {
      coordinator.close()
    }
  }

  @Test
  fun weatherReadingMarksOldOrFailedSnapshotsStale() {
    val t = System.currentTimeMillis()
    val value = WeatherInfo(18.0, 1)
    assertFalse(WeatherReading(value, t).isStale(t))
    assertTrue(WeatherReading(value, t - 25 * 60_000L).isStale(t))
    assertTrue(WeatherReading(value, t, error = true).isStale(t))
    assertFalse(WeatherReading(null, 0).isStale(t))
  }

  @Test
  fun refreshScopeDeduplicatesEqualKeys() = runBlocking {
    val scope = RefreshScope()
    val calls = AtomicInteger()
    scope.register("one", "weather@52,5") { calls.incrementAndGet() }
    scope.register("two", "weather@52,5") { calls.incrementAndGet() }
    scope.register("three", "device") { calls.incrementAndGet() }
    scope.refresh()
    assertEquals(2, calls.get())
    scope.unregister("one")
    scope.unregister("two")
    scope.unregister("three")
    assertFalse(scope.hasRefreshableNeeds)
  }
}
