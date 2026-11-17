package com.karandaev.fieldfolio.feature.shared.components.detectionpreview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.karandaev.fieldfolio.core.domain.model.detection.Detection
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.card.DetectionPreviewCard
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.defaults.DetectionPreviewHeight
import com.karandaev.fieldfolio.feature.shared.components.detectionpreview.state.rememberDetectionPreviewState

/**
 * Displays detection preview.
 *
 * This component is UI-only:
 * - it does not know about inspection reports;
 * - it does not resolve image paths by report id;
 * - it does not extract detections from domain objects.
 *
 * @param detections Detections to display.
 * @param imagePath Optional local image path.
 * @param modifier Modifier applied to the card.
 * @param previewHeight Height of the image preview area.
 */
@Composable
fun InspectionReportDetectionPreview(
  detections: List<Detection>,
  imagePath: String?,
  modifier: Modifier = Modifier,
  previewHeight: Dp = DetectionPreviewHeight,
) {
  val previewState = rememberDetectionPreviewState(detections = detections, imagePath = imagePath)

  DetectionPreviewCard(state = previewState, previewHeight = previewHeight, modifier = modifier)
}
