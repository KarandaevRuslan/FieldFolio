package com.karandaev.fieldfolio.core.ui.components.actions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.ui.components.dialog.LocalDialogWidth

/** Reserve title/back space and scroll actions inside the real inset-safe window width. */
@Composable
fun EditorActionRow(content: @Composable RowScope.() -> Unit) {
  Row(
    Modifier.widthIn(max = (LocalDialogWidth.current - 212.dp).coerceAtLeast(48.dp))
      .horizontalScroll(rememberScrollState())
      .testTag("editor-actions"),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}
