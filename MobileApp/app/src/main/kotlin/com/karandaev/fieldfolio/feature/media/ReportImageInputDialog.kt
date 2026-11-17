@file:OptIn(
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
  androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.karandaev.fieldfolio.feature.media

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.image.crop.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.components.fields.PresentationActions
import com.karandaev.fieldfolio.core.ui.lifecycle.findActivity
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.*
import com.karandaev.fieldfolio.feature.document.route.*
import com.karandaev.fieldfolio.feature.document.state.*
import com.karandaev.fieldfolio.feature.document.viewer.*
import com.karandaev.fieldfolio.feature.models.*
import kotlinx.coroutines.*

/** One acquisition/crop/preview toolbar for model input, report images and covers. */
@Composable
fun ReportImageInputDialog(
  key: String,
  title: String,
  dismiss: () -> Unit,
  save: (ByteArray, String) -> Boolean,
) =
  ReportImageInputDialog(
    key = key,
    title = title,
    dismiss = dismiss,
    initialBytes = null,
    save = save,
  )

/** Shared adaptive workspace; acquisition always resets the original, crop never destroys it. */
@Composable
fun ReportImageInputDialog(
  key: String,
  title: String,
  dismiss: () -> Unit,
  initialBytes: ByteArray? = null,
  initialName: String = "image.jpg",
  document: ReportDocument? = null,
  files: Map<String, ByteArray> = emptyMap(),
  trimBorders: Boolean = false,
  embeddedCover: ByteArray? = null,
  restoreSavedState: Boolean = true,
  preserveNameOnAcquire: Boolean = false,
  namedResource: Boolean = true,
  initialIdentity: com.karandaev.fieldfolio.core.names.ResourceIdentity? = null,
  onIdentity: ((com.karandaev.fieldfolio.core.names.ResourceIdentity) -> Unit)? = null,
  save: (ByteArray, String) -> Boolean,
) {
  val draft: ImageInputDraft = viewModel(key = "image-input-$key")
  var identity by
    com.karandaev.fieldfolio.core.ui.state.rememberSession(key) {
      mutableStateOf(
        initialIdentity ?: com.karandaev.fieldfolio.core.names.ResourceIdentity(initialName)
      )
    }
  val activity = LocalContext.current.findActivity()
  val scope = rememberCoroutineScope()
  var saving by remember { mutableStateOf(false) }
  var inputs by rememberSaveable { mutableStateOf(false) }
  var preview by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(draft, initialBytes) { draft.initialize(initialBytes, initialName) }
  DisposableEffect(draft, activity) {
    onDispose { if (activity?.isChangingConfigurations != true) draft.clear() }
  }
  val close =
    rememberUnsavedClose(
      draft.changed ||
        draft.name != initialName ||
        identity !=
          (initialIdentity ?: com.karandaev.fieldfolio.core.names.ResourceIdentity(initialName))
    ) {
      draft.clear()
      dismiss()
    }
  val image = draft.bytes
  val enabled = !draft.busy && !saving
  SafeAppDialog(close, androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false)) {
    Surface(Modifier.fillMaxSize().testTag("image-input-workspace")) {
      Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
          TopAppBar(
            title = { Text(title, maxLines = 1) },
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
              HintIconButton(close, tooltip = tr("close")) {
                Icon(Icons.Default.Close, tr("close"))
              }
            },
            actions = {
              if (onIdentity != null) PresentationActions(identity, { identity = it })
              if (embeddedCover != null)
                HintIconButton(
                  {
                    scope.launch {
                      draft.set(
                        withContext(Dispatchers.Default) { embeddedCover.copyOf() },
                        initialName,
                      )
                    }
                  },
                  modifier = Modifier.testTag("image-restore-embedded"),
                  enabled = enabled,
                  tooltip = stringResource(R.string.final_reset_cover),
                ) {
                  Icon(Icons.Default.Restore, stringResource(R.string.final_reset_cover))
                }
              HintIconButton(
                {
                  image?.let { input ->
                    scope.launch {
                      saving = true
                      try {
                        val copy = withContext(Dispatchers.Default) { input.copyOf() }
                        onIdentity?.invoke(identity.copy(name = draft.name))
                        if (save(copy, draft.name)) draft.clear()
                      } finally {
                        saving = false
                      }
                    }
                  }
                },
                enabled =
                  enabled &&
                    image != null &&
                    (!namedResource ||
                      com.karandaev.fieldfolio.core.names.ItemNames.valid(draft.name)),
                tooltip = tr("save"),
              ) {
                Icon(Icons.Default.Check, tr("save"))
              }
            },
          )
        },
      ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
          com.karandaev.fieldfolio.core.ui.layout.MediaWorkspace(
            controls = {
              ImageInputToolbar(
                image,
                enabled = enabled,
                onImage = { b, n -> draft.set(b, if (preserveNameOnAcquire) draft.name else n) },
                afterCamera = {
                  if (document != null)
                    HintIconButton(
                      { inputs = true },
                      enabled = enabled,
                      tooltip = tr("choose_image_block"),
                    ) {
                      Icon(Icons.Default.FolderOpen, tr("choose_image_block"))
                    }
                },
                cropSource = draft.frames.baseBytes,
                onCropSelected = draft::crop,
                onResetCrop = draft::reset,
                onRestoreSource =
                  if (restoreSavedState) ({ draft.restoreSaved(!preserveNameOnAcquire) })
                  else draft::restore,
                restoreSourceEnabled =
                  if (restoreSavedState) draft.hasSavedImage else image != null,
                restoreSourceLabel =
                  if (restoreSavedState) stringResource(R.string.v19_restore_saved_image) else null,
                onTrimBorder = if (trimBorders) draft::trim else null,
              )
            },
            details = {
              if (namedResource && onIdentity != null)
                com.karandaev.fieldfolio.core.ui.components.fields.ResourceIdentityFields(
                  identity.copy(name = draft.name),
                  {
                    identity = it
                    draft.name = it.name
                  },
                  file = true,
                  presentation = false,
                )
              else if (namedResource)
                com.karandaev.fieldfolio.core.ui.components.fields.ResourceNameField(
                  draft.name,
                  { draft.name = it },
                  file = true,
                )
              if (!enabled) LinearProgressIndicator(Modifier.fillMaxWidth())
              if (trimBorders)
                Text(
                  stringResource(R.string.media_border_hint),
                  style = MaterialTheme.typography.bodySmall,
                )
            },
            image = {
              image?.let {
                DocumentImage(
                  it,
                  fillViewport = true,
                  modifier = Modifier.fillMaxSize(),
                  onClick = { preview = true },
                )
              }
                ?: Box(
                  Modifier.fillMaxSize(),
                  contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                  Text(tr("choose_image"))
                }
            },
          )
        }
      }
    }
  }
  if (preview && image != null) ImageResultDialog(image, null, null) { preview = false }
  if (inputs && document != null)
    ModelInputPicker(document, false, { inputs = false }, files) { node ->
      inputs = false
      files[node.asset]?.let { source ->
        scope.launch {
          draft.set(
            withContext(Dispatchers.Default) { source.copyOf() },
            if (preserveNameOnAcquire) draft.name else node.name,
          )
        }
      }
    }
  draft.error?.let { error ->
    SafeAlertDialog(
      { draft.error = null },
      title = { Text(tr("error")) },
      text = {
        Text(
          if (error is UniformBorderNotFound) stringResource(R.string.media_no_border)
          else error.message.orEmpty()
        )
      },
      confirmButton = { TextButton({ draft.error = null }) { Text(tr("close")) } },
    )
  }
}
