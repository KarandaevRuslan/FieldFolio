package com.karandaev.fieldfolio.core.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll

/** The landscape image has a bounded viewport and never participates in vertical text scrolling. */
@Composable
fun MediaWorkspace(
  controls: @Composable () -> Unit,
  details: @Composable () -> Unit,
  image: @Composable BoxScope.() -> Unit,
  compactControls: Boolean = false,
) {
  val currentControls = rememberUpdatedState(controls)
  val stickyControls = remember { movableContentOf { currentControls.value() } }
  val currentDetails = rememberUpdatedState(details)
  val stickyDetails = remember { movableContentOf { currentDetails.value() } }
  val currentImage = rememberUpdatedState(image)
  val stickyImage = remember { movableContentOf<BoxScope> { scope -> currentImage.value(scope) } }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth >= 600.dp && maxWidth > maxHeight
    val controlsWidth = (maxWidth * .42f).coerceIn(340.dp, 440.dp)
    val imageHeight = (maxHeight * .55f).coerceIn(180.dp, 480.dp)
    if (wide && compactControls) {
      Column(Modifier.fillMaxSize()) {
        Row(
          Modifier.fillMaxWidth(),
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
          Box(Modifier.weight(1f)) { stickyControls() }
          Box(Modifier.padding(end = 12.dp)) { stickyDetails() }
        }
        Box(Modifier.weight(1f).fillMaxWidth().testTag("media-image-pane")) { stickyImage(this) }
      }
    } else if (wide)
      Row(Modifier.fillMaxSize().testTag("media-landscape")) {
        Column(Modifier.width(controlsWidth).fillMaxHeight().testTag("media-controls-pane")) {
          Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) { stickyControls() }
          Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            stickyDetails()
          }
        }
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight().testTag("media-image-pane")) { stickyImage(this) }
      }
    else
      Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) { stickyControls() }
        Column(
          Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(Modifier.fillMaxWidth().height(imageHeight).testTag("media-image-pane")) {
            stickyImage(this)
          }
          Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            stickyDetails()
          }
        }
      }
  }
}
