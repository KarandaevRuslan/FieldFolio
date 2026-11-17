@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.document.viewer

import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.acode.web.evaluateJs
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.datarefresh.rememberDeviceFix
import com.karandaev.fieldfolio.core.document.runtime.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintFilledTonalIconButton
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog
import com.karandaev.fieldfolio.core.ui.state.rememberViewerSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.tr
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
fun NotebookDocumentView(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  active: Boolean = true,
) {
  val parent = LocalDocumentAccess.current
  val fallback =
    rememberViewerSession("document-fallback", node, files) {
      DocumentAccessContext(
        "standalone-${node.id}",
        ReportDocument(nodes = listOf(node.copy(parentId = null, order = 0))),
        files,
      )
    }
  val access = parent ?: fallback
  val windows = LocalNotebookWindows.current
  var localExpanded by rememberViewerSession("notebook-expanded", node.id) { mutableStateOf(false) }
  val expanded = windows?.expanded == node.id || localExpanded
  Column(Modifier.fillMaxWidth().testTag("document-mini-${node.id}")) {
    Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
      Text("Observable · Nexus", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
      HintIconButton(
        { if (windows != null) windows.expanded = node.id else localExpanded = true },
        tooltip = stringResource(R.string.v20_expand_document),
      ) {
        Icon(Icons.Default.OpenInFull, stringResource(R.string.v20_expand_document))
      }
    }
    if (!expanded) DocumentRuntimeView(node, access, Modifier.fillMaxWidth().height(164.dp), active)
    else Box(Modifier.fillMaxWidth().height(164.dp))
  }
  if (localExpanded) FullNotebookDialog(node, access, active) { localExpanded = false }
}

@Composable
internal fun NotebookWindowHost(windows: NotebookWindows, access: DocumentAccessContext) {
  windows.expanded?.let { id ->
    val node = access.document.nodes.firstOrNull { it.id == id && it.kind == BlockKind.DOCUMENT }
    if (node != null) FullNotebookDialog(node, access, true) { windows.clear() }
    else LaunchedEffect(id) { windows.clear() }
  }
}

@Composable
internal fun FullNotebookDialog(
  node: DocumentNode,
  access: DocumentAccessContext,
  active: Boolean,
  dismiss: () -> Unit,
) {
  var searchRequest by
    rememberViewerSession("document-find-action", node.id) { mutableStateOf<String?>(null) }
  SafeAppDialog(dismiss) {
    Surface(Modifier.fillMaxSize().testTag("document-fullscreen")) {
      Column {
        TopAppBar(
          title = { Text(node.displayLabel, maxLines = 1) },
          windowInsets = WindowInsets(0, 0, 0, 0),
          navigationIcon = {
            HintIconButton(dismiss, tooltip = tr("close")) {
              Icon(Icons.Default.Close, tr("close"))
            }
          },
          actions = {
            HintIconButton(
              { searchRequest = java.util.UUID.randomUUID().toString() },
              tooltip = stringResource(R.string.v17_search_text),
            ) {
              Icon(Icons.Default.Search, stringResource(R.string.v17_search_text))
            }
          },
        )
        DocumentRuntimeView(
          node,
          access,
          Modifier.fillMaxWidth().weight(1f),
          active,
          expanded = true,
          searchRequest = searchRequest,
        )
      }
    }
  }
}

@Composable
internal fun DocumentRuntimeView(
  node: DocumentNode,
  access: DocumentAccessContext,
  modifier: Modifier = Modifier,
  active: Boolean = true,
  expanded: Boolean = false,
  searchRequest: String? = null,
) {
  val inherited = LocalDocumentWebPool.current
  val default: DocumentWebPool = viewModel(key = "document-web-pool")
  val pool = inherited ?: default
  // The read-only VFS may reference a hidden device Location/Weather node. Unlike a plain
  // text report, an ACTIVE notebook therefore needs an up-to-date location snapshot.
  // It shares the app-wide GPS request with every visible widget and map; VFS get() itself
  // still does not trigger a network request or create a permanent subscription.
  val vfsUsesDevice = remember(access) {
    access.weather != null && access.document.nodes.any {
      it.deviceLocation && it.kind in setOf(BlockKind.LOCATION, BlockKind.WEATHER)
    }
  }
  if (vfsUsesDevice) rememberDeviceFix(active = active)
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current
  val dark = MaterialTheme.colorScheme.background.luminance() < .5f
  val locale = LocalConfiguration.current.locales[0].toLanguageTag()
  val language =
    LocalConfiguration.current.locales[0].language.let { if (it == "ru") "ru" else "en" }
  var retry by remember { mutableIntStateOf(0) }
  val entry =
    remember(pool, access.key, node.id, retry) { pool.acquire(context, access.key, node.id) }
  DisposableEffect(entry, lifecycle) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> pool.pause()
        Lifecycle.Event.ON_START -> pool.resume()
        else -> Unit
      }
    }
    lifecycle.lifecycle.addObserver(observer)
    onDispose {
      lifecycle.lifecycle.removeObserver(observer)
      pool.release(entry)
    }
  }
  Box(modifier.clipToBounds().testTag("document-runtime-${node.id}")) {
    AndroidView(
      factory = { FrameLayout(it).apply { isSaveEnabled = false } },
      update = { container ->
        if (!entry.dead) {
          entry.context.baseContext = context
          entry.view.setBackgroundColor(if (dark) 0xff10131a.toInt() else 0xfff7f8fc.toInt())
          (entry.view as? DocumentRuntimeWebView)?.documentExpanded = expanded
          pool.attach(entry, container, active)
        }
      },
      onRelease = { container ->
        if (entry.host === container) {
          container.removeAllViews()
          entry.host = null
        }
      },
      modifier = Modifier.fillMaxSize(),
    )
    if (!entry.dead && !entry.failed && entry.source == null)
      LinearProgressIndicator(Modifier.fillMaxWidth())
    if (expanded && !entry.dead && !entry.failed)
      HintFilledTonalIconButton(
        { entry.view.evaluateJavascript("window.PreviewApi?.scrollToTop?.()", null) },
        anchorModifier =
          Modifier.align(androidx.compose.ui.Alignment.BottomEnd)
            .padding(end = 40.dp, bottom = 16.dp),
        tooltip = stringResource(R.string.v22_to_top),
      ) {
        Icon(Icons.Default.VerticalAlignTop, stringResource(R.string.v22_to_top))
      }
    if (entry.failed)
      Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.v20_document_failed))
          HintIconButton(
            {
              pool.clearReport(access.key)
              retry++
            },
            tooltip = tr("refresh"),
          ) {
            Icon(Icons.Default.Refresh, tr("refresh"))
          }
        }
      }
  }
  LaunchedEffect(entry, entry.finished, expanded, searchRequest) {
    if (entry.finished && !entry.dead) {
      entry.view.evaluateJavascript("window.PreviewApi?.setViewportMode?.($expanded)", null)
      if (searchRequest != null && searchRequest != entry.lastSearchRequest && expanded) {
        entry.lastSearchRequest = searchRequest
        entry.view.evaluateJavascript("window.PreviewApi?.search?.()", null)
      }
    }
  }
  LaunchedEffect(entry, entry.finished, entry.failed, node.body, access, dark, language) {
    if (!entry.finished || entry.failed || entry.dead) return@LaunchedEffect
    val resources =
      try {
        access.resources()
      } catch (cancel: CancellationException) {
        throw cancel
      } catch (_: Exception) {
        entry.failed = true
        return@LaunchedEffect
      }
    entry.client.resources = resources
    val environment =
      JSONObject()
        .put("dark", dark)
        .put("language", language)
        .put("locale", locale)
        .put("timeZone", java.util.TimeZone.getDefault().id)
        .put(
          "hourCycle",
          if (android.text.format.DateFormat.is24HourFormat(context)) "h23" else "h12",
        )
        .put("epochMillis", System.currentTimeMillis())
        .put("elapsedRealtimeMillis", android.os.SystemClock.elapsedRealtime())
        .put(
          "bootCount",
          android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.BOOT_COUNT,
            -1,
          ),
        )
    val changed =
      entry.source != node.body ||
        entry.access !== access ||
        entry.dark != dark ||
        entry.language != language
    val script =
      withContext(Dispatchers.Default) {
        val prepare =
          entry.snapshot
            ?.let { "window.PreviewApi.importState(${JSONObject.quote(it)});" }
            .orEmpty()
        val render =
          if (changed) {
            val manifest = resources.manifest(entry.client.host, entry.client.scope, node.id)
            "$prepare window.PreviewApi.render(${JSONObject.quote(node.body)},${JSONObject.quote(node.id)},$manifest);"
          } else ""
        "(function(){if(!window.PreviewApi?.isReady?.())return false;window.PreviewApi.setPreferences($environment);window.PreviewApi.setViewportMode($expanded);$render return true;})()"
      }
    val succeeded =
      withTimeoutOrNull(10_000) {
        while (isActive) {
          if (entry.view.evaluateJs(script) == "true") return@withTimeoutOrNull true
          delay(100)
        }
        false
      } == true
    if (succeeded && !entry.dead) {
      entry.source = node.body
      entry.access = access
      entry.dark = dark
      entry.language = language
      entry.snapshot = null
      // The iframe retains its own viewport; scrolling the outer WebView would reset it.
    } else if (!entry.dead) entry.failed = true
  }
}
