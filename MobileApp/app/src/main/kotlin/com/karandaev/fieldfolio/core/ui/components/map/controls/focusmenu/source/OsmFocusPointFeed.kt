package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.source

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.datarefresh.rememberDeviceFix
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.location.permissionchecker.LocationPermissionChecker
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model.OsmTrackedFocusPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A feed can contribute points and a user action when its points aren't available yet. */
data class OsmFocusPointFeedState(
  val points: List<OsmTrackedFocusPoint> = emptyList(),
  val action: OsmFocusPointAction? = null,
)

/** An action is not a map coordinate. It never moves the camera. */
enum class OsmFocusPointActionPlacement {
  Header,
  List,
}

enum class OsmFocusPointActionIcon {
  RefreshLocation,
  GrantLocation,
}

data class OsmFocusPointAction(
  val id: String,
  val title: String,
  val enabled: Boolean = true,
  val onClick: () -> Unit,
  val placement: OsmFocusPointActionPlacement = OsmFocusPointActionPlacement.List,
  val icon: OsmFocusPointActionIcon = OsmFocusPointActionIcon.RefreshLocation,
  val busy: Boolean = false,
)

/** Other feeds (participants, etc.) can implement the same interface. */
interface OsmFocusPointFeed {
  @Composable fun snapshot(active: Boolean): OsmFocusPointFeedState
}

/** Pass an empty feed list to AdaptiveOsmMap to disable device location entirely. */
object DeviceFocusPointFeed : OsmFocusPointFeed {
  @Composable
  override fun snapshot(active: Boolean): OsmFocusPointFeedState {
    val context = LocalContext.current
    val coordinator = rememberAppServices().resourceCoordinator
    val coroutineScope = rememberCoroutineScope()
    val fix = rememberDeviceFix(active = active, includeInPull = false)
    val launcher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // On grant, the existing active subscription fetches one fresh fix automatically.
        coordinator.permissionChanged()
      }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
      while (active) {
        now = System.currentTimeMillis()
        delay(15_000)
      }
    }

    val point = fix.point
    val current = fix.isCurrent(now)
    val points =
      point
        ?.let {
          listOf(
            OsmTrackedFocusPoint(
              id = "device:self",
              title =
                stringResource(
                  if (current) R.string.map_my_current_location else R.string.map_my_last_location
                ),
              point = it,
              measuredAtMillis = fix.location?.timestampMillis,
              accuracyMeters = fix.location?.accuracyMeters,
              stale = !current,
              linkedMarkerId = "current-location",
              showOnMap = true,
            )
          )
        }
        .orEmpty()

    // Previously this feed returned an empty list until GPS succeeded. That left the map
    // without any way to ask for permission or retry an unavailable device location.
    val hasPermission = LocationPermissionChecker.hasLocationPermission(context)
    val action =
      when {
        !active -> null
        !hasPermission ->
          OsmFocusPointAction(
            id = "device-permission",
            title = stringResource(R.string.map_grant_location_for_map),
            onClick = {
              launcher.launch(
                arrayOf(
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION,
                )
              )
            },
            placement = OsmFocusPointActionPlacement.Header,
            icon = OsmFocusPointActionIcon.GrantLocation,
          )
        else ->
          OsmFocusPointAction(
            id = "device-retry",
            title =
              stringResource(
                if (fix.isRefreshing) R.string.map_locating_device
                else R.string.map_retry_device_location
              ),
            enabled = !fix.isRefreshing,
            onClick = { coroutineScope.launch { coordinator.refreshLocation(force = true) } },
            placement = OsmFocusPointActionPlacement.Header,
            icon = OsmFocusPointActionIcon.RefreshLocation,
            busy = fix.isRefreshing,
          )
      }
    return OsmFocusPointFeedState(points, action)
  }
}
