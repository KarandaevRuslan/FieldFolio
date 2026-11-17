package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.datarefresh.rememberDeviceFix
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog
import com.karandaev.fieldfolio.core.ui.components.map.AdaptiveOsmMap
import com.karandaev.fieldfolio.core.ui.components.map.camera.OsmMapCameraConfig
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.tr

@Composable
fun LocationPreviewDialog(
  node: DocumentNode,
  device: MapPoint?,
  dismiss: () -> Unit,
  edit: () -> Unit,
  permission: () -> Unit = {},
  toolbarActions: (@Composable RowScope.() -> Unit)? = null,
) {
  val live = rememberDeviceFix(active = node.deviceLocation, includeInPull = false)
  val point =
    if (node.deviceLocation) live.point ?: device
    else node.latitude?.let { lat -> node.longitude?.let { MapPoint(lat, it) } }
  SafeAppDialog(dismiss) {
    Surface(Modifier.fillMaxSize()) {
      Column {
        Row(
          Modifier.fillMaxWidth(),
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
          HintIconButton(dismiss, tooltip = tr("close")) { Icon(Icons.Default.Close, tr("close")) }
          Text(node.name, Modifier.weight(1f), maxLines = 1)
          if (toolbarActions != null) toolbarActions()
          else HintIconButton(edit, tooltip = tr("edit")) { Icon(Icons.Default.Edit, tr("edit")) }
        }
        if (point != null)
          AdaptiveOsmMap(
            listOf(OsmMarkerSpec(node.id, point, OsmMarkerKind.Selected, node.name)),
            Modifier.fillMaxWidth().weight(1f),
            camera = OsmMapCameraConfig(point, initialZoom = 12.0),
            focusTarget = point,
          )
        else
          Row(
            Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
          ) {
            Text(tr("device_location"), Modifier.weight(1f))
            HintIconButton(permission, tooltip = tr("grant_location")) {
              Icon(Icons.Default.MyLocation, tr("grant_location"))
            }
          }
      }
    }
  }
}
