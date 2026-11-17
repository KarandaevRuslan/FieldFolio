@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.document.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog
import com.karandaev.fieldfolio.core.ui.components.dialog.rememberUnsavedClose
import com.karandaev.fieldfolio.core.ui.components.fields.PresentationActions
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.viewer.DocumentImage
import com.karandaev.fieldfolio.feature.document.viewer.ImageResultDialog

@Composable
fun AudioPropertiesDialog(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  dismiss: () -> Unit,
  save: (DocumentNode, ByteArray?) -> Unit,
) = AudioPropertiesDialog(node, files, dismiss, null, save)

@Composable
fun AudioPropertiesDialog(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  dismiss: () -> Unit,
  document: ReportDocument? = null,
  save: (DocumentNode, ByteArray?) -> Unit,
) {
  var identity by rememberNodeIdentity(node)
  val name = identity.name
  var options by rememberSession(node.id) { mutableStateOf(node.audio) }
  var replacement by rememberSession(node.id) { mutableStateOf<ByteArray?>(null) }
  var preview by rememberSession(node.id) { mutableStateOf(false) }
  val previewAudio = rememberSession(node.id) { DocumentAudioSession() }
  DisposableEffect(previewAudio) { onDispose { previewAudio.close() } }
  var picking by rememberSession { mutableStateOf(false) }
  val meta by
    produceState(AudioMetadata(), node.asset, files) {
      value = files[node.asset]?.let { readAudioMetadata(it) } ?: AudioMetadata()
    }
  val close =
    rememberUnsavedClose(
      identity != node.identity || options != node.audio || replacement != null
    ) {
      replacement?.fill(0)
      dismiss()
    }
  var expanded by rememberSession(node.id) { mutableStateOf(false) }
  var viewing by rememberSession { mutableStateOf(false) }
  val cover = replacement ?: files[options.coverAsset] ?: meta.cover
  fun resetCover() {
    replacement?.fill(0)
    replacement = null
    options = options.copy(coverAsset = null)
  }
  @Composable
  fun fields() {
    NodeIdentityFields(identity, { identity = it }, node.kind)
    ListItem(
      headlineContent = { Text(stringResource(R.string.final_show_cover)) },
      trailingContent = {
        Switch(
          options.showCover,
          { options = options.copy(showCover = it) },
          Modifier.testTag("audio-show-cover"),
        )
      },
    )
    OutlinedCard(Modifier.fillMaxWidth().testTag("audio-metadata-group")) {
      ListItem(
        headlineContent = { Text(stringResource(R.string.final_show_tags)) },
        leadingContent = {
          HintIconButton(
            { expanded = !expanded },
            tooltip = stringResource(R.string.media_metadata_fields),
          ) {
            Icon(
              if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              stringResource(R.string.media_metadata_fields),
            )
          }
        },
        trailingContent = {
          Switch(
            options.showTags,
            { options = options.copy(showTags = it) },
            Modifier.testTag("audio-show-tags"),
          )
        },
      )
      if (expanded)
        Column(
          Modifier.padding(12.dp).alpha(if (options.showTags) 1f else .5f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AudioMetadata.TAGS.forEach { tag ->
            val visible = tag !in options.hiddenTags
            OutlinedTextField(
              options.overrides[tag] ?: meta.tags[tag].orEmpty(),
              { value ->
                options = options.copy(overrides = options.overrides + (tag to value.take(500)))
              },
              Modifier.fillMaxWidth().testTag("audio-tag-$tag"),
              enabled = options.showTags && visible,
              label = { Text(audioTagLabel(tag)) },
              singleLine = true,
              leadingIcon = {
                Checkbox(
                  visible,
                  { show ->
                    options =
                      options.copy(
                        hiddenTags =
                          if (show) options.hiddenTags - tag else options.hiddenTags + tag
                      )
                  },
                  enabled = options.showTags,
                )
              },
              trailingIcon = {
                if (tag in options.overrides)
                  HintIconButton(
                    { options = options.copy(overrides = options.overrides - tag) },
                    enabled = options.showTags,
                    tooltip = stringResource(R.string.final_reset_tag),
                  ) {
                    Icon(Icons.Default.Restore, stringResource(R.string.final_reset_tag))
                  }
              },
            )
          }
        }
    }
  }
  @Composable
  fun picture(modifier: Modifier) {
    val disabledPaint = remember {
      Paint().apply {
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
      }
    }
    Box(
      modifier
        .alpha(if (options.showCover) 1f else .38f)
        .drawWithContent {
          if (options.showCover) drawContent()
          else {
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), disabledPaint)
            drawContent()
            drawContext.canvas.restore()
          }
        }
        .testTag("audio-cover-pane"),
      contentAlignment = Alignment.Center,
    ) {
      cover?.let {
        DocumentImage(
          it,
          fillViewport = true,
          modifier = Modifier.fillMaxSize(),
          onClick = { viewing = true },
        )
      }
        ?: Icon(
          Icons.Default.Album,
          null,
          Modifier.size(96.dp),
          tint = MaterialTheme.colorScheme.outline,
        )
    }
  }
  SafeAppDialog(close, androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false)) {
    Surface(Modifier.fillMaxSize()) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val toolbarWidth = (maxWidth - 212.dp).coerceAtLeast(48.dp)
        Scaffold(
          contentWindowInsets = WindowInsets(0, 0, 0, 0),
          topBar = {
            TopAppBar(
              title = { Text(tr("audio")) },
              windowInsets = WindowInsets(0, 0, 0, 0),
              navigationIcon = {
                HintIconButton(close, tooltip = tr("close")) {
                  Icon(Icons.Default.Close, tr("close"))
                }
              },
              actions = {
                Row(
                  Modifier.widthIn(max = toolbarWidth)
                    .horizontalScroll(rememberScrollState())
                    .testTag("audio-action-toolbar"),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  PresentationActions(identity, { identity = it })
                  HintIconButton({ preview = true }, tooltip = tr("view")) {
                    Icon(Icons.Default.Preview, tr("view"))
                  }
                  HintIconButton(
                    { picking = true },
                    Modifier.testTag("audio-change-cover"),
                    tooltip = stringResource(R.string.media_change_cover),
                  ) {
                    Icon(
                      Icons.Default.AddPhotoAlternate,
                      stringResource(R.string.media_change_cover),
                    )
                  }
                  HintIconButton(
                    ::resetCover,
                    enabled = replacement != null || options.coverAsset != null,
                    tooltip = stringResource(R.string.final_reset_cover),
                  ) {
                    Icon(Icons.Default.Restore, stringResource(R.string.final_reset_cover))
                  }
                  HintIconButton(
                    {
                      resetCover()
                      options = options.copy(overrides = emptyMap(), hiddenTags = emptyList())
                    },
                    enabled =
                      replacement != null ||
                        options.coverAsset != null ||
                        options.overrides.isNotEmpty() ||
                        options.hiddenTags.isNotEmpty(),
                    tooltip = stringResource(R.string.final_reset_tags),
                  ) {
                    Icon(
                      Icons.Default.SettingsBackupRestore,
                      stringResource(R.string.final_reset_tags),
                    )
                  }
                }
                HintIconButton(
                  {
                    save(
                      node.copy(
                        name = name.trim(),
                        displayName =
                          com.karandaev.fieldfolio.core.names.ItemNames.display(
                            identity.displayName
                          ),
                        hideTitle = identity.hideTitle,
                        visible = identity.visible,
                        audio = options,
                      ),
                      replacement,
                    )
                  },
                  enabled = identity.valid,
                  tooltip = tr("save"),
                ) {
                  Icon(Icons.Default.Save, tr("save"))
                }
              },
            )
          },
        ) { pad ->
          BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
            if (maxWidth > maxHeight && maxWidth >= 600.dp)
              Row(
                Modifier.fillMaxSize().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
              ) {
                Column(
                  Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  fields()
                }
                picture(Modifier.weight(1f).fillMaxHeight())
              }
            else
              Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                fields()
                picture(Modifier.fillMaxWidth().height(280.dp))
              }
          }
        }
      }
    }
  }

  if (preview) {
    val path = "resources/doc/audio-preview-cover.img"
    val draft =
      identity
        .asDraft(node)
        .copy(
          audio = options.copy(coverAsset = if (replacement != null) path else options.coverAsset)
        )
    DocumentElementPreviewDialog(
      draft,
      if (replacement != null) files + (path to replacement!!) else files,
      null,
      0,
      previewAudio,
      {},
      { preview = false },
    )
  }
  if (viewing && cover != null) ImageResultDialog(cover, null, null) { viewing = false }
  if (picking)
    com.karandaev.fieldfolio.feature.media.ReportImageInputDialog(
      "audio-cover-${node.id}",
      stringResource(R.string.media_change_cover),
      { picking = false },
      initialBytes = cover,
      initialName = "cover.png",
      document = document,
      files = files,
      namedResource = false,
      trimBorders = true,
      embeddedCover = meta.cover,
      restoreSavedState = false,
    ) { bytes, _ ->
      replacement?.fill(0)
      replacement = bytes
      picking = false
      true
    }
}
