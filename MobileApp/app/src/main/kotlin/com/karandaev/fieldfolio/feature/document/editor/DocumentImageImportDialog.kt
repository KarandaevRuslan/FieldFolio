package com.karandaev.fieldfolio.feature.document.editor

import androidx.compose.runtime.*
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.names.ResourceIdentity
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel

/**
 * Imports either an owned VFS image or report artwork through the shared image acquisition flow.
 */
@Composable
internal fun DocumentImageImportDialog(
  vm: DocumentViewModel,
  reportId: Long,
  coverImport: Boolean,
  identity: ResourceIdentity,
  onIdentity: (ResourceIdentity) -> Unit,
  dismiss: () -> Unit,
) {
  val imageIdentity by rememberUpdatedState(identity)
  com.karandaev.fieldfolio.feature.media.ReportImageInputDialog(
    "$reportId-$coverImport",
    tr(if (coverImport) "add_cover" else "choose_image"),
    dismiss,
    namedResource = !coverImport,
    initialBytes = if (coverImport) vm.files[vm.document.cover] else null,
    document = vm.document.takeUnless { coverImport && reportId == 0L },
    files = vm.files,
    onIdentity = if (coverImport) null else onIdentity,
  ) { bytes, name ->
    vm.addFile(
      bytes,
      name,
      android.graphics.BitmapFactory.Options().let { info ->
        info.inJustDecodeBounds = true
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, info)
        info.outMimeType ?: "image/jpeg"
      },
      BlockKind.IMAGE,
      coverImport,
      identity = if (coverImport) null else imageIdentity,
    )
    if (vm.error == null) dismiss() else bytes.fill(0)
    vm.error == null
  }
}
