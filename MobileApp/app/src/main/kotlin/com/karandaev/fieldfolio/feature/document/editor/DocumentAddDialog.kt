@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.document.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.karandaev.fieldfolio.core.document.BlockKind
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr

@Composable
fun DocumentAddDialog(dismiss: () -> Unit, choose: (BlockKind, String) -> Unit) {
  com.karandaev.fieldfolio.core.ui.components.actions.AdaptiveActionMenu(
    true,
    dismiss,
    tr("add"),
    tag = "add-block-grid",
  ) {
    BlockKind.entries
      .filter { it !in setOf(BlockKind.HEADING, BlockKind.TEXT_FILE) }
      .forEach { kind ->
        val label = kind.label()
        com.karandaev.fieldfolio.core.ui.components.actions.AdaptiveActionItem(
          { Text(label) },
          { choose(kind, label) },
        )
      }
  }
}
