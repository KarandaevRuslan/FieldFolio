@file:OptIn(
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
  androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.karandaev.fieldfolio.feature.media

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.image.crop.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.lifecycle.findActivity
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.detection.launcher.rememberDetectLaunchers
import com.karandaev.fieldfolio.feature.document.editor.*
import com.karandaev.fieldfolio.feature.document.route.*
import com.karandaev.fieldfolio.feature.document.state.*
import com.karandaev.fieldfolio.feature.document.viewer.*
import com.karandaev.fieldfolio.feature.models.*
import kotlinx.coroutines.*

/** One acquisition/crop/preview toolbar for model input, report images and covers. */
@Composable
fun ImageInputToolbar(
  bytes: ByteArray?,
  enabled: Boolean = true,
  onImage: (ByteArray, String) -> Unit,
  onPreview: (() -> Unit)? = null,
  afterCamera: @Composable () -> Unit = {},
  actionsVisible: Boolean = false,
  cropSource: ByteArray? = null,
  onCropSelected: ((NormalizedCropBox) -> Unit)? = null,
  onResetCrop: (() -> Unit)? = null,
  resetLabel: String? = null,
  onRestoreSource: (() -> Unit)? = null,
  restoreSourceEnabled: Boolean = bytes != null,
  restoreSourceLabel: String? = null,
  onTrimBorder: (() -> Unit)? = null,
  content: @Composable RowScope.() -> Unit = {},
) {
  val context = LocalContext.current
  val app = rememberAppServices()
  val scope = rememberCoroutineScope()
  var original by rememberSession { mutableStateOf<ByteArray?>(null) }
  val activity = LocalContext.current.findActivity()
  DisposableEffect(activity) {
    onDispose {
      if (activity?.isChangingConfigurations != true) {
        original?.fill(0)
        original = null
      }
    }
  }
  var crop by rememberSession { mutableStateOf(false) }
  var preview by rememberSession { mutableStateOf(false) }
  var issue by rememberSession { mutableStateOf<String?>(null) }
  val currentImage by rememberUpdatedState(onImage)
  suspend fun acquired(bytes: ByteArray, name: String) {
    if (onCropSelected == null) {
      val copy = withContext(Dispatchers.Default) { bytes.copyOf() }
      original?.fill(0)
      original = copy
    }
    currentImage(bytes, name)
  }
  val launchers =
    rememberDetectLaunchers(
      onGalleryImageSelected = { ctx, uri ->
        if (uri != null)
          scope.launch {
            try {
              acquired(readPickedBytes(ctx, uri, 48 * 1024 * 1024), pickedName(ctx, uri))
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              issue = e.message
            }
          }
      },
      onCapturedPhotoSelected = { ctx, uri ->
        if (uri != null)
          scope.launch {
            try {
              acquired(readPickedBytes(ctx, uri, 48 * 1024 * 1024), "photo.jpg")
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              issue = e.message
            } finally {
              runCatching { ctx.contentResolver.delete(uri, null, null) }
            }
          }
      },
      onCameraCaptureCancelled = {},
      onCameraPermissionDenied = {
        issue = context.getString(com.karandaev.fieldfolio.R.string.models_camera_permission)
      },
    )
  Surface(
    Modifier.fillMaxWidth().testTag("image-input-toolbar"),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
      Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
        HintIconButton(launchers::launchGallery, enabled = enabled, tooltip = tr("choose_image")) {
          Icon(Icons.Default.PhotoLibrary, tr("choose_image"))
        }
        HintIconButton(
          launchers::launchCameraWithPermission,
          enabled = enabled,
          tooltip = tr("take_photo"),
        ) {
          Icon(Icons.Default.PhotoCamera, tr("take_photo"))
        }
        afterCamera()
        HintIconButton(
          {
            scope.launch {
              if (onCropSelected == null && original == null)
                original = withContext(Dispatchers.Default) { bytes?.copyOf() }
              crop = true
            }
          },
          enabled = enabled && bytes != null,
          tooltip = tr("crop_image"),
        ) {
          Icon(Icons.Default.Crop, tr("crop_image"))
        }
        HintIconButton(
          { if (onPreview != null) onPreview() else preview = true },
          enabled = bytes != null,
          tooltip = tr("view"),
        ) {
          Icon(Icons.Default.ZoomIn, tr("view"))
        }
        HintIconButton(
          {
            if (onResetCrop != null) onResetCrop()
            else
              scope.launch {
                original?.let { source ->
                  val copy = withContext(Dispatchers.Default) { source.copyOf() }
                  currentImage(copy, "image.jpg")
                }
              }
          },
          enabled = enabled && (onResetCrop != null && bytes != null || original != null),
          tooltip =
            resetLabel
              ?: stringResource(
                if (onResetCrop != null) R.string.media_reset_crop
                else R.string.polish_restore_image
              ),
        ) {
          Icon(
            Icons.Default.Restore,
            resetLabel
              ?: stringResource(
                if (onResetCrop != null) R.string.media_reset_crop
                else R.string.polish_restore_image
              ),
          )
        }
        if (onRestoreSource != null)
          HintIconButton(
            onRestoreSource,
            enabled = enabled && restoreSourceEnabled,
            tooltip = restoreSourceLabel ?: stringResource(R.string.media_restore_source),
          ) {
            Icon(
              Icons.Default.SettingsBackupRestore,
              restoreSourceLabel ?: stringResource(R.string.media_restore_source),
            )
          }
        if (onTrimBorder != null)
          HintIconButton(
            onTrimBorder,
            enabled = enabled && bytes != null,
            tooltip = stringResource(R.string.media_trim_border),
          ) {
            Icon(Icons.Default.AutoFixHigh, stringResource(R.string.media_trim_border))
          }
      }
      if (actionsVisible) {
        VerticalDivider(Modifier.height(32.dp))
        Row(
          Modifier.height(48.dp).padding(horizontal = 4.dp),
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
          content()
        }
      }
    }
  }
  if (crop && bytes != null)
    AnalysisCropDialog(
      cropSource ?: original ?: bytes,
      { crop = false },
      onCropSelected?.let { select ->
        { box ->
          select(box)
          crop = false
        }
      },
    ) {
      currentImage(it, "image.jpg")
      crop = false
    }
  if (preview && bytes != null) ImageResultDialog(bytes, null, null) { preview = false }
  issue?.let {
    com.karandaev.fieldfolio.core.ui.components.dialog.SafeAlertDialog(
      { issue = null },
      title = { Text(tr("error")) },
      text = { Text(it) },
      confirmButton = { TextButton({ issue = null }) { Text(tr("close")) } },
    )
  }
}
