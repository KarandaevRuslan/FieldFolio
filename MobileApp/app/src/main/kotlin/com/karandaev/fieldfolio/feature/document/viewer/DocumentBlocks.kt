package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.map.AdaptiveOsmMap
import com.karandaev.fieldfolio.core.ui.components.map.camera.OsmMapCameraConfig
import com.karandaev.fieldfolio.core.ui.components.map.controls.model.OsmMapControls
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.*
import com.karandaev.fieldfolio.core.ui.scroll.AppLazyColumn as LazyColumn
import com.karandaev.fieldfolio.core.ui.state.LocalViewerSession
import com.karandaev.fieldfolio.core.ui.state.ViewerSession
import com.karandaev.fieldfolio.core.ui.state.rememberViewerSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession

fun BlockKind.icon() =
  when (this) {
    BlockKind.FOLDER -> Icons.Default.Folder
    BlockKind.DOCUMENT -> Icons.Default.Web
    BlockKind.IMAGE -> Icons.Default.Image
    BlockKind.AUDIO -> Icons.Default.Audiotrack
    BlockKind.LOCATION -> Icons.Default.LocationOn
    BlockKind.WEATHER -> Icons.Default.Cloud
    BlockKind.CLOCK -> Icons.Default.Schedule
    BlockKind.MODEL_RESULT -> Icons.Default.Memory
    else -> Icons.Default.Description
  }

@Composable
fun ResultDetails(
  result: ModelResultData,
  image: ByteArray? = null,
  showDetailsAction: Boolean = false,
  resources: Map<String, ByteArray> = emptyMap(),
) {
  Text(result.modelName, style = MaterialTheme.typography.titleMedium)
  Text(
    "${result.provider} · ${result.task} · ${result.elapsedMillis} ms",
    style = MaterialTheme.typography.labelSmall,
  )
  Text(tr("model_snapshot"), style = MaterialTheme.typography.bodySmall)
  if (result.boxes.isNotEmpty()) Text("${tr("result")}: ${result.boxes.size}")
  if (result.inputTruncated) Text(tr("input_truncated"), color = MaterialTheme.colorScheme.tertiary)
  var allScores by rememberViewerSession("scores", result.id) { mutableStateOf(false) }
  result.scores.take(20).forEach { ScoreRow(it) }
  if (result.scores.size > 20) TextButton({ allScores = true }) { Text(tr("all_scores")) }
  if (allScores)
    com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog({ allScores = false }) {
      Surface(Modifier.fillMaxSize()) {
        Column {
          HintIconButton({ allScores = false }, tooltip = tr("close")) {
            Icon(Icons.Default.Close, tr("close"))
          }
          LazyColumn(
            Modifier.fillMaxSize(),
            state =
              rememberViewerSession("scores-list", result.id) {
                androidx.compose.foundation.lazy.LazyListState()
              },
            contentPadding = PaddingValues(16.dp),
          ) {
            items(result.scores.size) { index -> ScoreRow(result.scores[index]) }
          }
        }
      }
    }
  result.values.forEach { value ->
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(value.label, Modifier.weight(1f))
      Text("${"%.6g".format(value.value)} ${value.unit.orEmpty()}")
    }
  }
  if (result.inputText.isNotEmpty()) {
    Text(tr("model_input"), style = MaterialTheme.typography.labelLarge)
    DocumentText(result.inputText, result.modelName, stateKey = "model-input")
  }
  result.layers.forEachIndexed { index, layer ->
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
        drawRect(segmentationColor(index))
      }
      Text(layer.label, Modifier.weight(1f))
      layer.confidence?.let { Text("${(it*100).toInt()} %") }
    }
    if (layer.originalPolygonJson != null && layer.polygons.isEmpty()) {
      Text(tr("geometry_unreadable"))
      DocumentText(layer.originalPolygonJson, layer.label, stateKey = "layer-geometry:$index")
    }
  }
  if (result.text.isNotEmpty()) {
    Text(tr("output"), style = MaterialTheme.typography.labelLarge)
    DocumentText(result.text, result.modelName, stateKey = "model-output")
  }
}

@Composable
fun DocumentBlockBody(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  device: MapPoint?,
  refresh: Long,
  audio: DocumentAudioSession,
  permission: () -> Unit,
  editorPreview: Boolean = false,
) {
  var fullscreen by rememberViewerSession("image-open", node.id) { mutableStateOf(false) }
  val visual by
    produceState<Boolean?>(null, node, files, editorPreview) {
      value =
        if (editorPreview)
          kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            hasLeafVisual(node, files)
          }
        else true
    }
  if (editorPreview && visual == false) {
    Text(
      androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.next_empty_preview)
    )
    return
  }
  if (editorPreview && !node.visible)
    Text(androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.next_hidden))
  when (node.kind) {
    BlockKind.HEADING,
    BlockKind.TEXT,
    BlockKind.TEXT_FILE -> TextBlockContent(node, files)
    BlockKind.DOCUMENT ->
      if (node.body.isNotBlank()) NotebookDocumentView(node, files)
      else if (editorPreview)
        Text(
          androidx.compose.ui.res.stringResource(
            com.karandaev.fieldfolio.R.string.next_empty_preview
          )
        )
    BlockKind.IMAGE ->
      DocumentImage(files[node.asset], compact = !editorPreview, onClick = { fullscreen = true })
    BlockKind.AUDIO ->
      DocumentAudio(
        files[node.asset],
        audio,
        key = "${node.id}:${node.asset}",
        options = node.audio,
        resources = files,
        name = node.name,
      )
    BlockKind.MODEL_RESULT -> {
      node.result?.let { r ->
        val hasImage =
          files[r.inputAsset] != null ||
            r.boxes.isNotEmpty() ||
            r.maskAsset != null ||
            r.layers.any { it.maskAsset != null || it.polygons.isNotEmpty() }
        if (hasImage) {
          ResultImagePreview(r, files, compact = true)
          Text(r.modelName, style = MaterialTheme.typography.titleSmall)
          Text(
            com.karandaev.fieldfolio.feature.models.taskLabel(r),
            style = MaterialTheme.typography.labelSmall,
          )
        } else {
          ModelResultSummary(r)
          var details by rememberViewerSession("model-output-open", r.id) { mutableStateOf(false) }
          HintIconButton({ details = true }, tooltip = tr("view")) {
            Icon(Icons.Default.OpenInFull, tr("view"))
          }
          if (details) ModelOutputDialog(r, files) { details = false }
        }
      }
      if (node.body.isNotBlank()) DocumentText(node.body, node.displayLabel)
    }
    BlockKind.LOCATION,
    BlockKind.WEATHER,
    BlockKind.CLOCK -> {
      DocumentLiveWidget(node, device, refresh, permission)
      val p =
        if (node.deviceLocation) device
        else node.latitude?.let { lat -> node.longitude?.let { MapPoint(lat, it) } }
      if (node.kind == BlockKind.LOCATION && p != null)
        AdaptiveOsmMap(
          listOf(OsmMarkerSpec(node.id, p, OsmMarkerKind.Selected, node.name)),
          Modifier.fillMaxWidth()
            .height(
              if (editorPreview)
                (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * .65f)
                  .dp
                  .coerceAtLeast(280.dp)
              else 180.dp
            ),
          viewport =
            if (editorPreview)
              com.karandaev.fieldfolio.core.ui.components.map.viewport.OsmMapViewport.Fill
            else com.karandaev.fieldfolio.core.ui.components.map.viewport.OsmMapViewport.Card(),
          camera = OsmMapCameraConfig(p, initialZoom = 12.0),
          focusTarget = p,
          controls = OsmMapControls(),
        )
    }
    else -> Unit
  }
  if (fullscreen) {
    val result = node.result
    val bytes = files[if (node.kind == BlockKind.MODEL_RESULT) result?.inputAsset else node.asset]
    bytes?.let {
      ImageResultDialog(it, result, files[result?.maskAsset], files) { fullscreen = false }
    }
  }
}

@Composable
fun DocumentBreadcrumbs(tree: DocumentTree, folder: String?, navigate: (String?) -> Unit) {
  androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth()) {
    item { TextButton({ navigate(null) }) { Text(tr("root")) } }
    folder?.let { id ->
      (tree.ancestors(id) + tree.node(id)).forEach { n ->
        item(key = n.id) { TextButton({ navigate(n.id) }) { Text("› ${n.name}") } }
      }
    }
  }
}

/** Long resources are paged as lazy text chunks rather than one unbounded layout. */
@Composable
fun DocumentText(
  text: String,
  title: String,
  style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
  stateKey: String = title,
) {
  val limit by rememberTextPageSize()
  val role = "$stateKey:${text.hashCode()}:${text.length}"
  var full by rememberViewerSession("text-open:$role", text) { mutableStateOf(false) }
  val preview = remember(text, limit) { textPage(text, 0, limit) }
  SelectionContainer {
    Text(
      preview + if (preview.length < text.length) "…" else "",
      style = style,
      maxLines = 4,
      overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
      modifier = Modifier.testTag("collapsed-document-text"),
    )
  }
  if (text.length > limit || text.length > 180 || text.count { it == '\n' } >= 4)
    HintIconButton({ full = true }, tooltip = tr("view")) {
      Icon(Icons.Default.OpenInFull, tr("view"))
    }
  val encoded by
    produceState<ByteArray?>(null, text) {
      value =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
          text.toByteArray(Charsets.UTF_8)
        }
    }
  if (full)
    encoded?.let { data ->
      ViewerSession("${LocalViewerSession.current ?: "text"}:reader:$role") {
        PagedTextDialog(data, title) { full = false }
      }
    }
}

@Composable
private fun ScoreRow(score: ResultScore) {
  Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      score.color?.let { color ->
        androidx.compose.foundation.Canvas(Modifier.padding(end = 8.dp).size(14.dp)) {
          drawRect(androidx.compose.ui.graphics.Color(color.toInt()))
        }
      }
      Text(score.label, Modifier.weight(1f))
      Text("${"%.1f".format(score.score*100)} %")
    }
    if (score.score in 0.0..1.0)
      LinearProgressIndicator(
        progress = { score.score.toFloat() },
        modifier = Modifier.fillMaxWidth(),
        color =
          score.color?.let { androidx.compose.ui.graphics.Color(it.toInt()) }
            ?: MaterialTheme.colorScheme.primary,
      )
  }
}

@Composable
internal fun ResultImagePreview(
  result: ModelResultData,
  files: Map<String, ByteArray>,
  compact: Boolean = false,
  fillViewport: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val original = files[result.inputAsset]
  val geometry =
    result.boxes.isNotEmpty() ||
      result.maskAsset != null ||
      result.layers.any { it.maskAsset != null || it.polygons.isNotEmpty() }
  if (original == null && !geometry) return
  var bytes by
    rememberViewerSession("result-preview-background", result.id, original) {
      mutableStateOf<ByteArray?>(original)
    }
  LaunchedEffect(original, result, files) {
    if (bytes == null)
      bytes =
        original
          ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val sample =
              (listOfNotNull(result.maskAsset) + result.layers.mapNotNull { it.maskAsset })
                .firstNotNullOfOrNull { path ->
                  files[path]?.let {
                    runCatching { com.karandaev.fieldfolio.core.models.decodeModelImage(it, 1024) }
                      .getOrNull()
                  }
                }
            val bitmap =
              android.graphics.Bitmap.createBitmap(
                sample?.width ?: 640,
                sample?.height ?: 640,
                android.graphics.Bitmap.Config.ARGB_8888,
              )
            sample?.recycle()
            bitmap.eraseColor(android.graphics.Color.rgb(52, 52, 52))
            java.io
              .ByteArrayOutputStream()
              .also {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                bitmap.recycle()
              }
              .toByteArray()
          }
  }
  var open by rememberViewerSession("result-image-open", result.id) { mutableStateOf(false) }
  bytes?.let { data ->
    @Composable
    fun Picture(mod: Modifier) {
      DocumentImage(
        data,
        result,
        files[result.maskAsset],
        files,
        compact = compact,
        fillViewport = fillViewport,
        modifier = mod,
      ) {
        open = true
      }
    }
    if (fillViewport)
      Column(modifier.fillMaxSize()) {
        if (original == null)
          Text(tr("geometry_without_image"), style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f).fillMaxWidth()) { Picture(Modifier.fillMaxSize()) }
      }
    else {
      if (original == null)
        Text(tr("geometry_without_image"), style = MaterialTheme.typography.bodySmall)
      Picture(modifier)
    }
    if (open) ImageResultDialog(data, result, files[result.maskAsset], files) { open = false }
  }
}
