package com.karandaev.fieldfolio.core.ui.components.fields

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Same 48dp leading slot for icons, selection and thumbnails; baseline and height never depend on
 * decoding.
 */
@Composable
fun VfsItemRow(
  modifier: Modifier = Modifier,
  headlineContent: @Composable () -> Unit,
  supportingContent: @Composable () -> Unit,
  leadingContent: @Composable () -> Unit,
  trailingContent: @Composable () -> Unit,
) {
  Row(
    modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { leadingContent() }
    Column(Modifier.weight(1f)) {
      Box(Modifier.testTag("vfs-item-title")) {
        ProvideTextStyle(MaterialTheme.typography.bodyLarge) { headlineContent() }
      }
      ProvideTextStyle(MaterialTheme.typography.bodySmall) { supportingContent() }
    }
    Box(contentAlignment = Alignment.Center) { trailingContent() }
  }
}
