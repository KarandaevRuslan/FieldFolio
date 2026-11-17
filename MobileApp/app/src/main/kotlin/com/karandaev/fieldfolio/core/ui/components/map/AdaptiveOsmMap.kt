package com.karandaev.fieldfolio.core.ui.components.map

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.location.LocationDefaults
import com.karandaev.fieldfolio.core.ui.components.actions.HintFilledTonalIconButton
import com.karandaev.fieldfolio.core.ui.components.map.camera.OsmMapCameraConfig
import com.karandaev.fieldfolio.core.ui.components.map.camera.focusOn
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model.buildOsmFocusMenuItems
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model.mergeMarkersWithTrackedPoints
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model.resolveLinkedFocusTarget
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model.OsmTrackedFocusPoint
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.source.DeviceFocusPointFeed
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.source.OsmFocusPointFeed
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.MapFocusMenuOverlay
import com.karandaev.fieldfolio.core.ui.components.map.controls.model.OsmMapControls
import com.karandaev.fieldfolio.core.ui.components.map.controls.model.OsmMapInteractions
import com.karandaev.fieldfolio.core.ui.components.map.controls.ui.MapFocusButton
import com.karandaev.fieldfolio.core.ui.components.map.controls.ui.MapLoadingOverlay
import com.karandaev.fieldfolio.core.ui.components.map.controls.ui.MapZoomControls
import com.karandaev.fieldfolio.core.ui.components.map.events.rememberOsmMapView
import com.karandaev.fieldfolio.core.ui.components.map.lifecycle.MapLifecycleEffect
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.OsmMarkerSpec
import com.karandaev.fieldfolio.core.ui.components.map.settings.MapFocusPreferences
import com.karandaev.fieldfolio.core.ui.components.map.marker.style.markerPalette
import com.karandaev.fieldfolio.core.ui.components.map.state.rememberAdaptiveOsmMapState
import com.karandaev.fieldfolio.core.ui.components.map.state.updateAdaptiveOsmMap
import com.karandaev.fieldfolio.core.ui.components.map.viewport.MapContainer
import com.karandaev.fieldfolio.core.ui.components.map.viewport.OsmMapViewport
import com.karandaev.fieldfolio.core.ui.components.map.viewport.mapControlMetricsForViewport
import com.karandaev.fieldfolio.core.util.mappers.toMapPoint
import org.osmdroid.views.MapView

private const val ZOOM_ANIMATION_DURATION_MILLIS = 150L

private fun MapView.zoomInByStep() {
  val nextZoom = (zoomLevelDouble + 1.0).coerceAtMost(maxZoomLevel)

  controller.zoomTo(nextZoom, ZOOM_ANIMATION_DURATION_MILLIS)
}

private fun MapView.zoomOutByStep() {
  val nextZoom = (zoomLevelDouble - 1.0).coerceAtLeast(minZoomLevel)

  controller.zoomTo(nextZoom, ZOOM_ANIMATION_DURATION_MILLIS)
}

/**
 * Universal Compose wrapper around an OSMDroid map.
 *
 * This composable is the high-level entry point for map rendering. It wires together the OSMDroid
 * [MapView], Compose layout, marker rendering, camera updates, lifecycle handling, map gestures,
 * loading state, and optional overlay controls.
 *
 * Feature-specific behavior should be passed through configuration models and callbacks instead of
 * creating separate map composables for each screen.
 *
 * @param markers Marker descriptions that should be rendered on the map.
 * @param modifier Modifier applied to the outer map container.
 * @param viewport Layout mode that defines how the map is sized and decorated.
 * @param camera Initial camera position, zoom bounds, and focus zoom configuration.
 * @param controls Optional overlay control configuration, such as zoom and focus buttons.
 * @param interactions Map interaction callbacks for taps, long presses, and marker clicks.
 * @param focusTarget Optional point used by focus requests and the default focus button action. If
 *   null, the focus button falls back to [OsmMapCameraConfig.initialCenter].
 * @param focusRequest Monotonic value that triggers a camera focus when changed.
 * @param centerOnMarkerChange Whether the map should recenter to [focusTarget] after marker
 *   changes.
 * @param isLoading Whether to display a loading overlay above the map.
 * @param mapRef Callback that exposes the underlying OSMDroid [MapView] for interop scenarios.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AdaptiveOsmMap(
  markers: List<OsmMarkerSpec>,
  modifier: Modifier = Modifier,
  viewport: OsmMapViewport = OsmMapViewport.Fill,
  camera: OsmMapCameraConfig =
    OsmMapCameraConfig(initialCenter = LocationDefaults.location.toMapPoint()),
  controls: OsmMapControls = OsmMapControls(),
  interactions: OsmMapInteractions = OsmMapInteractions(),
  focusTarget: MapPoint? = null,
  focusTargetMarkerId: String? = null,
  focusRequest: Int = 0,
  centerOnMarkerChange: Boolean = false,
  isLoading: Boolean = false,
  focusPointFeeds: List<OsmFocusPointFeed> = listOf(DeviceFocusPointFeed),
  extraFocusPoints: List<OsmTrackedFocusPoint> = emptyList(),
  mapRef: (MapView) -> Unit = {},
) {
  val context = LocalContext.current
  val showCurrentMapCenter by MapFocusPreferences.rememberShowCurrentMapCenter()

  val currentOnTap = rememberUpdatedState(interactions.onTap)
  val currentOnLongPress = rememberUpdatedState(interactions.onLongPress)
  val currentOnMarkerClick = rememberUpdatedState(interactions.onMarkerClick)
  val currentMapRef = rememberUpdatedState(mapRef)

  // Keep feed subscriptions for the lifetime of the visible map, not only while the menu
  // is open. This makes the device pin visible and lets a late GPS fix update the map.
  // The application coordinator still performs only ONE GPS polling loop for all maps.
  val feedStates = focusPointFeeds.map { it.snapshot(active = true) }
  val trackedPoints = extraFocusPoints + feedStates.flatMap { it.points }
  val visibleMarkers = mergeMarkersWithTrackedPoints(markers, trackedPoints)
  val feedActions = feedStates.mapNotNull { it.action }
  // A linked marker may have moved since the caller's last snapshot. Keep the focus
  // button and focus requests aligned with the single destination shown in the menu.
  val effectiveFocusTarget =
    resolveLinkedFocusTarget(focusTarget, focusTargetMarkerId, visibleMarkers)

  val palette = markerPalette()
  val mapState = rememberAdaptiveOsmMapState(focusRequest = focusRequest)

  var isFocusMenuVisible by
    com.karandaev.fieldfolio.core.ui.state.rememberViewerSession("map-focus-menu") {
      mutableStateOf(false)
    }

  val acceptingCamera = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
  val cameraReady = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
  val savedCamera =
    com.karandaev.fieldfolio.core.ui.state.rememberViewerSession(
      "map-camera",
      retainUntilOwnerCleared = true,
    ) {
      mutableStateOf<List<Double>?>(null)
    }
  val restoredCamera = remember { savedCamera.value }
  val startingCamera = remember {
    restoredCamera?.let { camera.copy(initialCenter = MapPoint(it[0], it[1]), initialZoom = it[2]) }
      ?: camera
  }
  val mapView =
    rememberOsmMapView(
      context = context,
      camera = startingCamera,
      currentOnTap = currentOnTap,
      currentOnLongPress = currentOnLongPress,
    )

  androidx.compose.runtime.DisposableEffect(mapView) {
    restoredCamera?.getOrNull(3)?.let { mapView.mapOrientation = it.toFloat() }
    fun capture() {
      if (
        !acceptingCamera.get() ||
          !cameraReady.get() ||
          mapView.width <= 0 ||
          mapView.height <= 0 ||
          !mapView.isAttachedToWindow
      )
        return
      savedCamera.value =
        listOf(
          mapView.mapCenter.latitude,
          mapView.mapCenter.longitude,
          mapView.zoomLevelDouble,
          mapView.mapOrientation.toDouble(),
        )
    }
    val listener =
      object : org.osmdroid.events.MapListener {
        override fun onScroll(event: org.osmdroid.events.ScrollEvent): Boolean {
          capture()
          return false
        }

        override fun onZoom(event: org.osmdroid.events.ZoomEvent): Boolean {
          capture()
          return false
        }
      }
    val firstFrame =
      object : android.view.ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
          if (mapView.width > 0 && mapView.height > 0) {
            cameraReady.set(true)
            capture()
            if (mapView.viewTreeObserver.isAlive)
              mapView.viewTreeObserver.removeOnPreDrawListener(this)
          }
          return true
        }
      }
    mapView.viewTreeObserver.addOnPreDrawListener(firstFrame)
    mapView.addMapListener(listener)
    onDispose {
      mapView.removeMapListener(listener)
      if (mapView.viewTreeObserver.isAlive)
        mapView.viewTreeObserver.removeOnPreDrawListener(firstFrame)
    }
  }
  LaunchedEffect(mapView) { currentMapRef.value(mapView) }

  BoxWithConstraints(modifier = modifier.clipToBounds()) {
    val metrics =
      mapControlMetricsForViewport(viewport = viewport, maxWidth = maxWidth, maxHeight = maxHeight)

    MapContainer(viewport = viewport, modifier = Modifier.fillMaxSize()) {
      AndroidView(
        factory = {
          android.widget.FrameLayout(context).apply {
            clipChildren = true
            clipToPadding = true
            addView(mapView, android.widget.FrameLayout.LayoutParams(-1, -1))
          }
        },
        modifier = Modifier.fillMaxSize().clipToBounds().testTag("native-map-frame"),
        update = { host ->
          val view = mapView
          currentMapRef.value(view)

          view.updateAdaptiveOsmMap(
            markers = visibleMarkers,
            palette = palette,
            camera = camera,
            focusTarget = effectiveFocusTarget,
            focusRequest = focusRequest,
            centerOnMarkerChange = centerOnMarkerChange,
            currentOnMarkerClick = currentOnMarkerClick,
            state = mapState,
          )
        },
      )

      if (isLoading) {
        MapLoadingOverlay(metrics = metrics)
      }

      if (controls.showZoomControls) {
        MapZoomControls(
          metrics = metrics,
          onZoomIn = { mapView.zoomInByStep() },
          onZoomOut = { mapView.zoomOutByStep() },
          centered = viewport == OsmMapViewport.Fill,
        )
      }

      val focusButtonTarget = effectiveFocusTarget ?: camera.initialCenter

      if (controls.showFocusButton) {
        MapFocusButton(
          text = controls.focusButtonText,
          metrics = metrics,
          onClick = {
            mapView.focusOnDefaultTarget(point = focusButtonTarget, camera = camera)

            controls.onFocusClick?.invoke()
          },
        )
      }

      HintFilledTonalIconButton(
        { isFocusMenuVisible = true },
        Modifier.padding(8.dp).size(40.dp),
        anchorModifier = Modifier.align(Alignment.TopStart),
        tooltip =
          androidx.compose.ui.res.stringResource(
            com.karandaev.fieldfolio.R.string.map_instance_points
          ),
      ) {
        Icon(
          Icons.Default.FormatListBulleted,
          androidx.compose.ui.res.stringResource(
            com.karandaev.fieldfolio.R.string.map_instance_points
          ),
        )
      }
      if (isFocusMenuVisible) {
        com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog({
          isFocusMenuVisible = false
        }) {
          MapFocusMenuOverlay(
            items =
              buildOsmFocusMenuItems(
                focusTargetTitle =
                  androidx.compose.ui.res.stringResource(
                    com.karandaev.fieldfolio.R.string.ui_focus_target
                  ),
                initialCenterTitle =
                  androidx.compose.ui.res.stringResource(
                    com.karandaev.fieldfolio.R.string.map_current_center
                  ),
                focusTarget = effectiveFocusTarget,
                focusTargetMarkerId = focusTargetMarkerId,
                initialCenter =
                  com.karandaev.fieldfolio.core.domain.model.location.MapPoint(
                    mapView.mapCenter.latitude,
                    mapView.mapCenter.longitude,
                  ),
                markers = visibleMarkers,
                trackedPoints = trackedPoints,
                showCurrentMapCenter = showCurrentMapCenter,
              ),
            showSearch = true,
            actions = feedActions,
            onItemClick = { item ->
              isFocusMenuVisible = false

              mapView.focusOnDefaultTarget(point = item.point, camera = camera)
            },
            onDismiss = { isFocusMenuVisible = false },
          )
        }
      }
    }
  }

  MapLifecycleEffect(
    mapView = mapView,
    onSaveCamera = {
      if (
        acceptingCamera.getAndSet(false) &&
          cameraReady.get() &&
          mapView.width > 0 &&
          mapView.height > 0 &&
          mapView.isAttachedToWindow
      )
        savedCamera.value =
          listOf(
            mapView.mapCenter.latitude,
            mapView.mapCenter.longitude,
            mapView.zoomLevelDouble,
            mapView.mapOrientation.toDouble(),
          )
    },
  )
}

private fun MapView.focusOnDefaultTarget(point: MapPoint, camera: OsmMapCameraConfig) {
  focusOn(
    point = point,
    zoom = camera.focusZoom,
    minZoom = camera.minZoom,
    maxZoom = camera.maxZoom,
  )
}
