package com.karandaev.fieldfolio.core.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll

/** Scrollable properties on the left, bounded preview on the right in landscape. */
@Composable
fun ElementWorkspace(
  modifier: Modifier = Modifier,
  fields: @Composable ColumnScope.() -> Unit,
  preview: @Composable (Modifier) -> Unit,
) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    if (maxWidth >= 600.dp && maxWidth > maxHeight)
      Row(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Column(
          Modifier.weight(1f)
            .fillMaxHeight()
            .testTag("element-fields")
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          content = fields,
        )
        preview(Modifier.weight(1f).fillMaxHeight().testTag("element-preview"))
      }
    else
      Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        fields()
        preview(Modifier.fillMaxWidth().height(300.dp).testTag("element-preview"))
      }
  }
}
