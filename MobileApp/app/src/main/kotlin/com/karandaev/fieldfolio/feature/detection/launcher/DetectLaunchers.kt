package com.karandaev.fieldfolio.feature.detection.launcher

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.karandaev.fieldfolio.core.image.picker.ImagePickerLauncher
import com.karandaev.fieldfolio.core.image.picker.createTempImageCaptureUri
import com.karandaev.fieldfolio.core.image.picker.rememberImagePickerLauncher
import com.karandaev.fieldfolio.core.ui.state.rememberSession

/**
 * Remembers Android launchers used by detection screen.
 *
 * @param onGalleryImageSelected Called when gallery picker returns image URI.
 * @param onCapturedPhotoSelected Called when camera successfully writes photo to URI.
 * @param onCameraCaptureCancelled Called when camera result is cancelled.
 * @param onCameraPermissionDenied Called when camera permission is denied.
 */
@Composable
internal fun rememberDetectLaunchers(
  onGalleryImageSelected: (Context, Uri?) -> Unit,
  onCapturedPhotoSelected: (Context, Uri?) -> Unit,
  onCameraCaptureCancelled: () -> Unit,
  onCameraPermissionDenied: () -> Unit,
): DetectLaunchers {
  val context = LocalContext.current
  val library = com.karandaev.fieldfolio.core.di.rememberAppServices().reportLibrary

  var pendingCameraUri by rememberSession { mutableStateOf<Uri?>(null) }

  val galleryLauncher =
    rememberImagePickerLauncher(
      onImageSelected = { selectedContext, uri ->
        library.externalPicker = false
        onGalleryImageSelected(selectedContext, uri)
      }
    )

  val cameraLauncher =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture()) { success ->
      library.externalPicker = false
      val uri = pendingCameraUri

      if (success && uri != null) {
        onCapturedPhotoSelected(context, uri)
      } else {
        onCameraCaptureCancelled()
      }

      pendingCameraUri = null
    }

  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
      granted ->
      if (granted) {
        val uri = createTempImageCaptureUri(context)

        pendingCameraUri = uri
        library.externalPicker = true
        cameraLauncher.launch(uri)
      } else {
        library.externalPicker = false
        onCameraPermissionDenied()
      }
    }

  return remember(galleryLauncher, cameraPermissionLauncher) {
    DetectLaunchers(
      galleryLauncher = galleryLauncher,
      cameraPermissionLauncher = cameraPermissionLauncher,
      beforePicker = { library.externalPicker = true },
    )
  }
}

/** Stable launcher wrapper used by detection route. */
@Stable
internal class DetectLaunchers(
  private val galleryLauncher: ImagePickerLauncher,
  private val cameraPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
  private val beforePicker: () -> Unit,
) {

  /** Opens system image picker. */
  fun launchGallery() {
    beforePicker()
    galleryLauncher.launchImagePicker()
  }

  /** Requests camera permission and launches camera when permission is granted. */
  fun launchCameraWithPermission() {
    beforePicker()
    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
  }
}
