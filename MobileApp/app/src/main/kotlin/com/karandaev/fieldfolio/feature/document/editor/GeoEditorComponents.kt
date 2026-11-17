package com.karandaev.fieldfolio.feature.document.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.map.AdaptiveOsmMap
import com.karandaev.fieldfolio.core.ui.components.map.camera.OsmMapCameraConfig
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr

@Composable
fun GeoMapAction(device: Boolean, pick: () -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(tr("pick_map"), Modifier.weight(1f))
    HintIconButton(pick, enabled = !device, tooltip = tr("pick_map")) {
      Icon(Icons.Default.Map, tr("pick_map"))
    }
  }
}

@Composable
fun GeoMapPreview(id: String, point: MapPoint?, modifier: Modifier, device: Boolean) {
  if (point != null)
    AdaptiveOsmMap(
      listOf(OsmMarkerSpec(id, point, OsmMarkerKind.Selected, tr("location_point"))),
      modifier,
      camera = OsmMapCameraConfig(initialCenter = point, initialZoom = 12.0),
      focusTarget = point,
    )
  else
    Box(modifier, contentAlignment = Alignment.Center) {
      Text(tr(if (device) "device_location" else "pick_map"))
    }
}

@Composable
fun GeoSourceFields(
  identity: com.karandaev.fieldfolio.core.names.ResourceIdentity,
  changed: (com.karandaev.fieldfolio.core.names.ResourceIdentity) -> Unit,
  kind: com.karandaev.fieldfolio.core.document.BlockKind,
  device: Boolean,
  changeDevice: (Boolean) -> Unit,
  deviceLabel: String,
  latitude: String,
  changeLatitude: (String) -> Unit,
  longitude: String,
  changeLongitude: (String) -> Unit,
  extra: @Composable () -> Unit = {},
  pick: () -> Unit,
) {
  NodeIdentityFields(identity, changed, kind)
  ListItem(
    headlineContent = { Text(deviceLabel) },
    trailingContent = { Switch(device, changeDevice) },
  )
  if (!device)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedTextField(
        latitude,
        changeLatitude,
        Modifier.weight(1f),
        singleLine = true,
        label = { Text(tr("latitude")) },
        isError =
          latitude.isNotBlank() &&
            latitude.toDoubleOrNull()?.let { it.isFinite() && it in -90.0..90.0 } != true,
      )
      OutlinedTextField(
        longitude,
        changeLongitude,
        Modifier.weight(1f),
        singleLine = true,
        label = { Text(tr("longitude")) },
        isError =
          longitude.isNotBlank() &&
            longitude.toDoubleOrNull()?.let { it.isFinite() && it in -180.0..180.0 } != true,
      )
    }
  extra()
  GeoMapAction(device, pick)
}
