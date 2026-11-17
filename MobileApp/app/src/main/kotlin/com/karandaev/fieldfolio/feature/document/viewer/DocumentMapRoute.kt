package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.document.DocumentLocation
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.state.PersistedAppStateViewModel
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAlertDialog
import com.karandaev.fieldfolio.core.ui.components.map.AdaptiveOsmMap
import com.karandaev.fieldfolio.core.ui.components.map.controls.model.*
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.*
import com.karandaev.fieldfolio.core.ui.scroll.AppLazyColumn as LazyColumn
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.map.state.rememberMapLocationController
import com.karandaev.fieldfolio.feature.shared.components.maps.currentLocationPoint

/** Coordinate collisions retain every (report, block) identity, with an explicit choice on tap. */
data class ReportMapPin(
  val reportId: Long,
  val reportTitle: String,
  val location: DocumentLocation,
  val point: MapPoint,
)

fun reportMapPins(reports: List<InspectionReport>, device: MapPoint?): List<ReportMapPin> =
  reports.flatMap { r ->
    val points =
      r.documentLocations
        ?: listOf(DocumentLocation("legacy-location", r.title, r.latitude, r.longitude))
    points.mapNotNull { l ->
      val point =
        if (l.device) device
        else if (l.latitude != null && l.longitude != null) MapPoint(l.latitude, l.longitude)
        else null
      point?.let { ReportMapPin(r.id, r.title, l, it) }
    }
  }

fun mapPinGroups(pins: List<ReportMapPin>) =
  pins.groupBy {
    java.lang.String.format(java.util.Locale.US, "%.5f,%.5f", it.point.latitude, it.point.longitude)
  }

@Composable
fun DocumentMapRoute(
  reports: List<InspectionReport>,
  persisted: PersistedAppStateViewModel,
  open: (Long, String?) -> Unit,
  create: (Double, Double) -> Unit,
) {
  val app = rememberAppServices()
  val controller =
    rememberMapLocationController(
      locationViewModelKey = "location-map",
      permissionViewModelKey = "location-permission-map",
      locationResolver = app.locationResolver,
      persistedAppStateViewModel = persisted,
      requestPermissionOnStart = false,
      enablePolling = true,
    )
  val device = controller.locationState.currentLocationPoint
  val pins = remember(reports, device) { reportMapPins(reports, device) }
  val groups = remember(pins) { mapPinGroups(pins) }
  var selecting by rememberSession { mutableStateOf<Set<Pair<Long, String>>?>(null) }
  var nativeMap by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }
  val touchRadius = with(LocalDensity.current) { 48.dp.toPx() }

  val markers =
    groups.map { (key, list) ->
      OsmMarkerSpec(
        "pins:$key",
        list.first().point,
        OsmMarkerKind.Note,
        if (list.size == 1) "${list.first().reportTitle} · ${list.first().location.label}"
        else "${list.size} · ${list.first().reportTitle}",
        clickable = true,
      )
    } +
      listOfNotNull(
        // Same identity as DeviceFocusPointFeed. When GPS is late, this marker keeps the
        // saved fallback visible; when GPS arrives, the feed replaces it, not duplicates it.
        device?.let { OsmMarkerSpec("current-location", it, OsmMarkerKind.Current, if (controller.isStale)
          androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.map_my_last_location)
        else tr("device_location")) }
      )
  Box(Modifier.fillMaxSize()) {
    AdaptiveOsmMap(
      markers,
      Modifier.fillMaxSize(),
      mapRef = { nativeMap = it },
      focusTarget = device,
      focusTargetMarkerId = "current-location",
      focusRequest = controller.focusRequest,
      controls = OsmMapControls(onFocusClick = controller::focusLocation),
      interactions =
        OsmMapInteractions(
          onMarkerClick = { key ->
            if (key.startsWith("pins:")) {
              val group = groups[key.removePrefix("pins:")].orEmpty()
              val origin = group.firstOrNull()?.point
              val nearby =
                nativeMap
                  ?.let { map ->
                    origin?.let { point ->
                      val projection = map.projection
                      val center =
                        projection.toPixels(
                          org.osmdroid.util.GeoPoint(point.latitude, point.longitude),
                          null,
                        )
                      pins.filter { pin ->
                        val p =
                          projection.toPixels(
                            org.osmdroid.util.GeoPoint(pin.point.latitude, pin.point.longitude),
                            null,
                          )
                        kotlin.math.hypot(
                          (p.x - center.x).toDouble(),
                          (p.y - center.y).toDouble(),
                        ) <= touchRadius
                      }
                    }
                  }
                  .orEmpty()
                  .ifEmpty { group }
              if (nearby.size == 1) open(nearby[0].reportId, nearby[0].location.parentId)
              else selecting = nearby.map { it.reportId to it.location.nodeId }.toSet()
            }
          },
          onLongPress = { create(it.latitude, it.longitude) },
        ),
    )
    if (pins.isEmpty())
      Surface(
        Modifier.align(Alignment.TopCenter).padding(12.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
      ) {
        Text(tr("no_points"), Modifier.padding(12.dp))
      }
  }
  selecting?.let { identities ->
    SafeAlertDialog(
      { selecting = null },
      title = { Text(tr("points")) },
      text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
          items(
            pins.filter { (it.reportId to it.location.nodeId) in identities },
            key = { "${it.reportId}:${it.location.nodeId}" },
          ) { pin ->
            ListItem(
              headlineContent = { Text(pin.reportTitle) },
              supportingContent = { Text(pin.location.label) },
              modifier =
                Modifier.clickable {
                  selecting = null
                  open(pin.reportId, pin.location.parentId)
                },
            )
          }
        }
      },
      confirmButton = { TextButton({ selecting = null }) { Text(tr("close")) } },
    )
  }
}
