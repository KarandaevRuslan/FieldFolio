@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.document.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.datarefresh.rememberDeviceFix
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.components.fields.PresentationActions
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.*
import com.karandaev.fieldfolio.core.ui.layout.ElementWorkspace
import com.karandaev.fieldfolio.core.ui.state.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.viewer.DocumentLiveWidget

@Composable
fun GeoNodeDialog(node: DocumentNode, dismiss: () -> Unit, save: (DocumentNode, String?) -> Unit) {
  var identity by rememberNodeIdentity(node)
  var device by rememberSession(node.id) { mutableStateOf(node.deviceLocation) }
  var lat by rememberSession(node.id) { mutableStateOf(node.latitude?.toString().orEmpty()) }
  var lon by rememberSession(node.id) { mutableStateOf(node.longitude?.toString().orEmpty()) }
  var picker by rememberSession { mutableStateOf(false) }
  var preview by rememberSession { mutableStateOf(false) }
  val audio = rememberSession { DocumentAudioSession() }
  DisposableEffect(audio) { onDispose { audio.close() } }
  val location = rememberDeviceFix(active = device)
  val current = if (device) location.point else null
  val latitude = lat.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 }
  val longitude = lon.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 }
  val point =
    if (device) current
    else if (latitude != null && longitude != null) MapPoint(latitude, longitude) else null
  val draft =
    identity
      .asDraft(node)
      .copy(
        deviceLocation = device,
        latitude = if (device) null else latitude,
        longitude = if (device) null else longitude,
      )
  val close = rememberUnsavedClose(draft != node, dismiss)
  SafeAppDialog(close) {
    Scaffold(
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        TopAppBar(
          title = { Text(node.kind.label()) },
          windowInsets = WindowInsets(0, 0, 0, 0),
          navigationIcon = {
            HintIconButton(close, tooltip = tr("close")) { Icon(Icons.Default.Close, tr("close")) }
          },
          actions = {
            com.karandaev.fieldfolio.core.ui.components.actions.EditorActionRow {
              PresentationActions(identity, { identity = it })
              HintIconButton({ preview = true }, tooltip = tr("view")) {
                Icon(Icons.Default.Preview, tr("view"))
              }
            }
            HintIconButton(
              {
                save(
                  identity
                    .asDraft(node)
                    .copy(
                      deviceLocation = device,
                      latitude = if (device) null else lat.toDoubleOrNull(),
                      longitude = if (device) null else lon.toDoubleOrNull(),
                    ),
                  null,
                )
              },
              enabled = identity.valid && (device || point != null),
              tooltip = tr("save"),
            ) {
              Icon(Icons.Default.Save, tr("save"))
            }
          },
        )
      },
    ) { pad ->
      ElementWorkspace(
        Modifier.padding(pad),
        fields = {
          GeoSourceFields(
            identity,
            { identity = it },
            node.kind,
            device,
            { device = it },
            tr("device_location"),
            lat,
            { lat = it },
            lon,
            { lon = it },
            pick = { picker = true },
          )
        },
        preview = { modifier ->
          ViewerSession("geo-editor:${node.id}") {
            if (node.kind == BlockKind.LOCATION) GeoMapPreview(node.id, point, modifier, device)
            else
              Box(modifier, contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                  DocumentLiveWidget(draft, current, 0, {})
                }
              }
          }
        },
      )
    }
  }
  if (picker)
    DocumentPointPicker(point, { picker = false }) {
      lat = it.latitude.toString()
      lon = it.longitude.toString()
      picker = false
    }
  if (preview)
    DocumentElementPreviewDialog(draft, emptyMap(), current, 0, audio, {}, { preview = false })
}
