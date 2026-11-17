package com.karandaev.fieldfolio.core.di

import com.google.firebase.auth.FirebaseAuth
import com.karandaev.fieldfolio.core.auth.firebase.FirebaseGoogleAuthProvider
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.domain.repository.InspectionReportRepository
import com.karandaev.fieldfolio.core.domain.repository.PersistedAppStateRepository
import com.karandaev.fieldfolio.core.domain.repository.SettingsRepository
import com.karandaev.fieldfolio.core.image.crop.ImageCropper
import com.karandaev.fieldfolio.core.image.sourcefile.ImageSourceFileProvider
import com.karandaev.fieldfolio.core.inspectionapi.InspectionProvider
import com.karandaev.fieldfolio.core.location.place.PlaceNameProvider
import com.karandaev.fieldfolio.core.location.provider.DeviceLocationProvider
import com.karandaev.fieldfolio.core.location.resolver.LocationResolver
import com.karandaev.fieldfolio.core.weather.WeatherProvider
import com.squareup.moshi.Moshi

/**
 * Read-only access to application-owned services; features never import the composition root.
 *
 * Implementations live for the application process. Consumers must not close shared repositories or
 * retain Activity instances in this graph. Report-private state belongs to session owners.
 */
interface AppServices {
  val database: AppDatabase
  val moshi: Moshi
  val reportLibrary: com.karandaev.fieldfolio.core.library.ReportLibrary
  val inspectionReportRepository: InspectionReportRepository
  val modelCatalog: com.karandaev.fieldfolio.core.models.ModelCatalog
  val serverModels: com.karandaev.fieldfolio.core.models.ServerModelRegistry
  val catalogModelRunner: com.karandaev.fieldfolio.core.models.CatalogModelRunner
  val firebaseAuth: FirebaseAuth
  val firebaseGoogleAuthProvider: FirebaseGoogleAuthProvider
  val inspectionProvider: InspectionProvider
  val imageSourceFileProvider: ImageSourceFileProvider
  val imageCropper: ImageCropper
  val settingsRepository: SettingsRepository
  val persistedAppStateRepository: PersistedAppStateRepository
  val placeNameProvider: PlaceNameProvider
  val deviceLocationProvider: DeviceLocationProvider
  val locationResolver: LocationResolver
  val weatherProvider: WeatherProvider
  val resourceCoordinator: com.karandaev.fieldfolio.core.datarefresh.ResourceCoordinator
}

/**
 * Resolves application-owned services without exposing the Android composition root to features.
 */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun rememberAppServices(): AppServices =
  androidx.compose.ui.platform.LocalContext.current.applicationContext as AppServices
