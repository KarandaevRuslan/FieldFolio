package com.karandaev.fieldfolio.app

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.karandaev.fieldfolio.core.auth.firebase.FirebaseGoogleAuthProvider
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.data.repository.LocalPersistedAppStateRepository
import com.karandaev.fieldfolio.core.data.repository.LocalSettingsRepository
import com.karandaev.fieldfolio.core.domain.repository.InspectionReportRepository
import com.karandaev.fieldfolio.core.domain.repository.PersistedAppStateRepository
import com.karandaev.fieldfolio.core.domain.repository.SettingsRepository
import com.karandaev.fieldfolio.core.image.cache.ImageCacheCleaner
import com.karandaev.fieldfolio.core.image.crop.ImageCropper
import com.karandaev.fieldfolio.core.image.sourcefile.DefaultImageSourceFileProvider
import com.karandaev.fieldfolio.core.image.sourcefile.ImageSourceFileProvider
import com.karandaev.fieldfolio.core.inspectionapi.DefaultInspectionProvider
import com.karandaev.fieldfolio.core.inspectionapi.InspectionProvider
import com.karandaev.fieldfolio.core.json.MoshiFactory
import com.karandaev.fieldfolio.core.location.place.AndroidPlaceNameProvider
import com.karandaev.fieldfolio.core.location.place.PlaceNameProvider
import com.karandaev.fieldfolio.core.location.provider.AndroidLocationProvider
import com.karandaev.fieldfolio.core.location.provider.DeviceLocationProvider
import com.karandaev.fieldfolio.core.location.resolver.CachingLocationResolver
import com.karandaev.fieldfolio.core.location.resolver.DefaultLocationResolver
import com.karandaev.fieldfolio.core.location.resolver.LocationResolver
import com.karandaev.fieldfolio.core.weather.WeatherProvider
import com.karandaev.fieldfolio.core.weather.openmeteo.OpenMeteoWeatherProvider
import com.squareup.moshi.Moshi
import java.io.File
import org.osmdroid.config.Configuration

/**
 * Application class for initializing the database, app services, JSON serialization, and osmdroid.
 * Registered in AndroidManifest.xml using android:name.
 */
class MyApplication : Application(), com.karandaev.fieldfolio.core.di.AppServices {

  /** Lazy database initialization - created on first access. */
  override val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

  /** Shared Moshi instance for JSON serialization. */
  override val moshi: Moshi by lazy { MoshiFactory.create() }

  override val reportLibrary: com.karandaev.fieldfolio.core.library.ReportLibrary by lazy {
    com.karandaev.fieldfolio.core.library.ReportLibrary(this, database, moshi)
  }

  /** Repository for report operations. */
  override val inspectionReportRepository: InspectionReportRepository by lazy { reportLibrary }

  override val modelCatalog: com.karandaev.fieldfolio.core.models.ModelCatalog by lazy {
    com.karandaev.fieldfolio.core.models.ModelCatalog(this, moshi, database.inspectionModelDao())
  }
  override val serverModels: com.karandaev.fieldfolio.core.models.ServerModelRegistry by lazy {
    com.karandaev.fieldfolio.core.models.ServerModelRegistry()
  }
  override val catalogModelRunner: com.karandaev.fieldfolio.core.models.CatalogModelRunner by lazy {
    com.karandaev.fieldfolio.core.models.CatalogModelRunner(
      modelCatalog,
      inspectionProvider,
      serverModels,
    )
  }

  /** Shared Firebase authentication instance. */
  override val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

  /** Provider for signing users into Firebase with Google. */
  override val firebaseGoogleAuthProvider: FirebaseGoogleAuthProvider by lazy {
    FirebaseGoogleAuthProvider(auth = firebaseAuth)
  }

  /** DetectProvider for loading road crack detections from a configurable backend. */
  override val inspectionProvider: InspectionProvider by lazy {
    DefaultInspectionProvider(auth = firebaseAuth, initialBaseUrl = null)
  }

  /** Provider for copying selected Android content into local source image files. */
  override val imageSourceFileProvider: ImageSourceFileProvider by lazy {
    DefaultImageSourceFileProvider()
  }

  /** Cropper for creating cropped local image files. */
  override val imageCropper: ImageCropper by lazy { ImageCropper() }

  /** Repository for user-configurable app settings. */
  override val settingsRepository: SettingsRepository by lazy {
    LocalSettingsRepository(database.settingsDao())
  }

  /** Repository for app runtime state persisted between sessions. */
  override val persistedAppStateRepository: PersistedAppStateRepository by lazy {
    LocalPersistedAppStateRepository(
      persistedAppStateDao = database.persistedAppStateDao(),
      moshi = moshi,
    )
  }

  /** Provider for converting coordinates to human-readable place names. */
  override val placeNameProvider: PlaceNameProvider by lazy { AndroidPlaceNameProvider(this) }

  /** Provider for obtaining the current device location. */
  override val deviceLocationProvider: DeviceLocationProvider by lazy {
    AndroidLocationProvider(this)
  }

  /** Resolver that combines device location and place name providers. */
  override val locationResolver: LocationResolver by lazy {
    CachingLocationResolver(
      delegate =
        DefaultLocationResolver(
          locationProvider = deviceLocationProvider,
          placeNameProvider = placeNameProvider,
        ),
      persistedAppStateRepository = persistedAppStateRepository,
    )
  }

  /** WeatherProvider for loading weather in a specific place. */
  override val weatherProvider: WeatherProvider by lazy { OpenMeteoWeatherProvider() }

  override val resourceCoordinator:
    com.karandaev.fieldfolio.core.datarefresh.ResourceCoordinator by lazy {
    com.karandaev.fieldfolio.core.datarefresh.ResourceCoordinator(
      deviceLocationProvider,
      weatherProvider,
      persistedAppStateRepository,
      hasPermission = {
        com.karandaev.fieldfolio.core.location.permissionchecker.LocationPermissionChecker
          .hasLocationPermission(this)
      },
    )
  }

  override fun onCreate() {
    super.onCreate()
    clearStartupImageCache()
    initOsmdroid()
  }

  /**
   * osmdroid setup:
   * - userAgentValue is required; otherwise OSM tile servers may block clients that use the default
   *   user agent;
   * - the tile cache is stored in filesDir instead of cacheDir, because Android may clear cacheDir
   *   when storage is low, causing the app to repeatedly hit the OSM tile servers.
   */
  private fun initOsmdroid() {
    runCatching {
        val prefs = getSharedPreferences(OSMDROID_PREFS, MODE_PRIVATE)
        val config = Configuration.getInstance()
        config.load(this, prefs)
        config.userAgentValue = packageName
        config.osmdroidBasePath = File(filesDir, "osmdroid").apply { mkdirs() }
        config.osmdroidTileCache = File(config.osmdroidBasePath, "tiles").apply { mkdirs() }
      }
      .onFailure { error -> Log.e(LOG_TAG, "Can't initialize osmdroid: ${error.message}", error) }
  }

  /**
   * Clears temporary app image cache on every app process start.
   *
   * This does not touch osmdroid map cache.
   */
  private fun clearStartupImageCache() {
    runCatching {
        ImageCacheCleaner.clearStartupImageCache(this)
        cacheDir
          .listFiles()
          ?.filter { it.name.startsWith("geoinspect-export-") }
          ?.forEach { it.delete() }
      }
      .onFailure { error -> Log.w(LOG_TAG, "Can't clear image cache: ${error.message}", error) }
  }

  private companion object {
    const val OSMDROID_PREFS = "osmdroid_prefs"
    const val LOG_TAG = "Fieldfolio"
  }
}
