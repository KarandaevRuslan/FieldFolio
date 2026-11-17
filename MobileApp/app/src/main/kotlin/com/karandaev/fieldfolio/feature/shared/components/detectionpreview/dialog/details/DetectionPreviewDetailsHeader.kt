package com.karandaev.fieldfolio.feature.shared.components.detectionpreview.dialog.details

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton

/** Header for landscape details panel. */
@Composable
internal fun DetectionPreviewDetailsHeader(detectionsCount: Int, onDismissRequest: () -> Unit) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
      text =
        stringResource(id = R.string.detection_preview_details_title_with_count, detectionsCount),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )

    HintIconButton(
      onClick = onDismissRequest,
      tooltip = stringResource(R.string.detection_preview_details_close_content_description),
    ) {
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription =
          stringResource(R.string.detection_preview_details_close_content_description),
      )
    }
  }
}
