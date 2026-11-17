package com.karandaev.fieldfolio.feature.shared.components.maps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.ui.components.map.AdaptiveOsmMap
import com.karandaev.fieldfolio.core.ui.components.map.controls.model.OsmMapControls
import com.karandaev.fieldfolio.core.ui.components.map.controls.model.OsmMapInteractions
import com.karandaev.fieldfolio.core.ui.components.map.viewport.OsmMapViewport
import com.karandaev.fieldfolio.feature.map.location.LocationUiState
import org.osmdroid.views.MapView

/**
 * Full-size interactive map for browsing notes.
 *
 * This legacy-compatible wrapper maps the existing note-map API onto [AdaptiveOsmMap]. Current
 * location is provided through [LocationUiState], so this composable only renders the state and
 * does not resolve location by itself.
 *
 * Location updates and camera focus are intentionally separated: location polling may update the
 * current-location marker, while [focusRequest] controls when the camera should move to the current
 * location.
 */
@Composable
fun OsmMapView(
  inspectionReports: List<InspectionReport>,
  locationState: LocationUiState,
  modifier: Modifier = Modifier,
  focusRequest: Int = 0,
  showCurrentLocationButton: Boolean = true,
  showZoomControls: Boolean = true,
  onFocusClick: (() -> Unit)? = null,
  onNoteMarkerClick: (Long) -> Unit = {},
  onLongPress: (Double, Double) -> Unit = { _, _ -> },
  mapRef: (MapView) -> Unit = {},
) {
  val currentLocationPoint = locationState.currentLocationPoint

  val currentLocationTitle =
    androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.ui_current_location)
  val selectedLocationTitle =
    androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.ui_selected_location)
  val markers =
    remember(currentLocationTitle, selectedLocationTitle, inspectionReports, currentLocationPoint) {
      buildBrowseMapMarkers(
        currentLocationTitle = currentLocationTitle,
        inspectionReports = inspectionReports,
        currentLocationPoint = currentLocationPoint,
      )
    }

  AdaptiveOsmMap(
    markers = markers,
    modifier = modifier,
    viewport = OsmMapViewport.Fill,
    controls =
      OsmMapControls(
        showZoomControls = showZoomControls,
        showFocusButton = showCurrentLocationButton,
        focusButtonText = "⌖",
        onFocusClick = onFocusClick,
      ),
    interactions =
      OsmMapInteractions(
        onLongPress = { point -> onLongPress(point.latitude, point.longitude) },
        onMarkerClick = { markerId -> markerId.toNoteIdOrNull()?.let(onNoteMarkerClick) },
      ),
    focusTarget = currentLocationPoint,
    focusRequest = focusRequest,
    isLoading = locationState.isInitialLocationLoading,
    mapRef = mapRef,
  )
}
