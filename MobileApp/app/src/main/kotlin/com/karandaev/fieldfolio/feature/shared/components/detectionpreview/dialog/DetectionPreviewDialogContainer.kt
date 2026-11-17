package com.karandaev.fieldfolio.feature.shared.components.detectionpreview.dialog

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog

/** Preview dimensions and centering both use the current dialog's safe working area. */
@Composable
internal fun DetectionPreviewDialogContainer(
  onDismissRequest: () -> Unit,
  content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
  SafeAppDialog(
    onDismissRequest,
    DialogProperties(dismissOnClickOutside = true),
    DetectionPreviewDialogScreenPadding,
    content,
  )
}
