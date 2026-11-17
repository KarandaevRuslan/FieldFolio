@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.document.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.components.fields.PresentationActions
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.*
import com.karandaev.fieldfolio.core.ui.state.ViewerSession
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.viewer.DocumentLiveWidget
import kotlinx.coroutines.CancellationException

/**
 * Clock uses the same location source as weather; map selection resolves its zone automatically.
 */
@Composable
fun ClockPropertiesDialog(
  node: DocumentNode,
  dismiss: () -> Unit,
  resolveZone: (suspend (MapPoint) -> String)? = null,
  save: (DocumentNode, String?) -> Unit,
) {
  val app = rememberAppServices()
  var identity by rememberNodeIdentity(node)
  val name = identity.name
  var device by rememberSession(node.id) { mutableStateOf(node.deviceLocation) }
  var lat by rememberSession(node.id) { mutableStateOf(node.latitude?.toString().orEmpty()) }
  var lon by rememberSession(node.id) { mutableStateOf(node.longitude?.toString().orEmpty()) }
  var zone by rememberSession(node.id) { mutableStateOf(node.zoneId.orEmpty()) }
  var map by rememberSession { mutableStateOf(false) }
  var fullscreenPreview by rememberSession { mutableStateOf(false) }
  val audio = rememberSession { DocumentAudioSession() }
  DisposableEffect(audio) { onDispose { audio.close() } }
  var request by rememberSession(node.id) { mutableStateOf<MapPoint?>(null) }
  var lookup by remember { mutableStateOf(false) }
  var failed by rememberSession { mutableStateOf(false) }
  val latitude = lat.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 }
  val longitude = lon.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 }
  val point = if (latitude != null && longitude != null) MapPoint(latitude, longitude) else null
  val zoneValid = device || runCatching { java.time.ZoneId.of(zone) }.isSuccess
  val valid =
    identity.valid &&
      zoneValid &&
      (device || point != null || lat.isBlank() && lon.isBlank()) &&
      !lookup
  val resolver by rememberUpdatedState(resolveZone)
  LaunchedEffect(request, device, lat, lon) {
    val chosen = request
    if (device || chosen == null || chosen != point) {
      lookup = false
      return@LaunchedEffect
    }
    lookup = true
    failed = false
    try {
      val resolved =
        resolver?.invoke(chosen)
          ?: requireNotNull(
            app.weatherProvider.getWeather(chosen.latitude, chosen.longitude).getOrThrow().zoneId
          )
      java.time.ZoneId.of(resolved)
      zone = resolved
      request = null
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      failed = true
      if (request == chosen) request = null
    } finally {
      lookup = false
    }
  }
  val close =
    rememberUnsavedClose(
      identity != node.identity ||
        device != node.deviceLocation ||
        lat != node.latitude?.toString().orEmpty() ||
        lon != node.longitude?.toString().orEmpty() ||
        zone != node.zoneId.orEmpty(),
      dismiss,
    )
  @Composable
  fun fields() {
    GeoSourceFields(
      identity,
      { identity = it },
      node.kind,
      device,
      { device = it },
      tr("device_time_zone"),
      lat,
      {
        lat = it
        request = null
      },
      lon,
      {
        lon = it
        request = null
      },
      extra = {
        if (!device) {
          OutlinedTextField(
            zone,
            {
              zone = it
              request = null
            },
            Modifier.fillMaxWidth().testTag("clock-zone"),
            label = { Text(tr("zone")) },
            singleLine = true,
            isError = !zoneValid && !lookup,
            supportingText = { Text(tr("zone_hint")) },
            trailingIcon = {
              HintIconButton(
                { request = point },
                enabled = point != null && !lookup,
                tooltip = tr("resolve_zone"),
              ) {
                Icon(Icons.Default.AutoFixHigh, tr("resolve_zone"))
              }
            },
          )
          if (lookup) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("clock-zone-loading"))
          if (failed) Text(tr("lookup_failed"), color = MaterialTheme.colorScheme.error)
        }
      },
      pick = { map = true },
    )
  }
  SafeAppDialog(close) {
    Surface(Modifier.fillMaxSize()) {
      Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
          TopAppBar(
            title = { Text(tr("clock")) },
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
              HintIconButton(close, tooltip = tr("close")) {
                Icon(Icons.Default.Close, tr("close"))
              }
            },
            actions = {
              com.karandaev.fieldfolio.core.ui.components.actions.EditorActionRow {
                PresentationActions(identity, { identity = it })
                HintIconButton({ fullscreenPreview = true }, tooltip = tr("view")) {
                  Icon(Icons.Default.Preview, tr("view"))
                }
              }
              HintIconButton(
                {
                  save(
                    node.copy(
                      name = name.trim(),
                      displayName =
                        com.karandaev.fieldfolio.core.names.ItemNames.display(identity.displayName),
                      hideTitle = identity.hideTitle,
                      visible = identity.visible,
                      deviceLocation = device,
                      latitude = if (device) null else latitude,
                      longitude = if (device) null else longitude,
                      zoneId = zone.takeIf { it.isNotBlank() },
                    ),
                    null,
                  )
                },
                enabled = valid,
                tooltip = tr("save"),
              ) {
                Icon(Icons.Default.Save, tr("save"))
              }
            },
          )
        },
      ) { pad ->
        com.karandaev.fieldfolio.core.ui.layout.ElementWorkspace(
          Modifier.padding(pad),
          fields = { fields() },
          preview = { modifier ->
            ViewerSession("clock-editor:${node.id}") {
              if (!device && point != null)
                Box(modifier) {
                  GeoMapPreview(
                    node.id,
                    point,
                    Modifier.fillMaxSize().testTag("clock-map-preview"),
                    device,
                  )
                }
              else
                Box(modifier, contentAlignment = Alignment.Center) {
                  Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                    DocumentLiveWidget(
                      identity
                        .asDraft(node)
                        .copy(deviceLocation = device, zoneId = zone.takeIf { it.isNotBlank() }),
                      null,
                      0,
                      {},
                    )
                  }
                }
            }
          },
        )
      }
    }
  }
  if (map)
    DocumentPointPicker(point, { map = false }) { selected ->
      lat = selected.latitude.toString()
      lon = selected.longitude.toString()
      zone = ""
      request = selected
      map = false
    }
  if (fullscreenPreview)
    DocumentElementPreviewDialog(
      identity
        .asDraft(node)
        .copy(
          deviceLocation = device,
          latitude = latitude,
          longitude = longitude,
          zoneId = zone.takeIf { it.isNotBlank() },
        ),
      emptyMap(),
      null,
      0,
      audio,
      {},
      { fullscreenPreview = false },
    )
}
