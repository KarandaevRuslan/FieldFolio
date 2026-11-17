package com.karandaev.fieldfolio.feature.shared.components.detectionpreview.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.defaults.DetectionPreviewContentPadding
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.defaults.DetectionPreviewContentSpacing
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.preview.DetectionPreviewImageBox
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.state.DetectionPreviewState

/**
 * Card that displays detection preview header, image area and status.
 *
 * @param state Detection preview state.
 * @param previewHeight Height of image preview area.
 * @param modifier Modifier applied to the card.
 */
@Composable
internal fun DetectionPreviewCard(
  state: DetectionPreviewState,
  previewHeight: Dp,
  modifier: Modifier = Modifier,
) {
  Card(modifier = modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(DetectionPreviewContentPadding),
      verticalArrangement = Arrangement.spacedBy(DetectionPreviewContentSpacing),
    ) {
      DetectionPreviewHeader(detectionsCount = state.detections.size)

      DetectionPreviewImageBox(state = state, previewHeight = previewHeight)

      DetectionPreviewStatusText(hasImage = state.hasImage, hasDetections = state.hasDetections)
    }
  }
}
