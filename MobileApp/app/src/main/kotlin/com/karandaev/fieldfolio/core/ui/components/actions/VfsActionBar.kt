@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.core.ui.components.actions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll

enum class VfsSelectionAction(val label: Int) {
  SELECT_ALL(R.string.library_select_all),
  CLEAR_SELECTION(R.string.library_clear_selection),
  RENAME(R.string.library_rename),
  COPY(R.string.library_copy),
  CUT(R.string.library_cut),
  MOVE(R.string.library_move),
  PASTE(R.string.library_paste),
  CLEAR_CLIPBOARD(R.string.library_clear_clipboard),
  DELETE(R.string.library_delete),
}

fun vfsSelectionActions(
  hasItems: Boolean,
  selected: Int,
  clipboard: Boolean,
): List<VfsSelectionAction> = buildList {
  if (hasItems) add(VfsSelectionAction.SELECT_ALL)
  if (selected > 0) add(VfsSelectionAction.CLEAR_SELECTION)
  if (selected == 1) add(VfsSelectionAction.RENAME)
  if (selected > 0)
    addAll(listOf(VfsSelectionAction.COPY, VfsSelectionAction.CUT, VfsSelectionAction.MOVE))
  if (clipboard) addAll(listOf(VfsSelectionAction.PASTE, VfsSelectionAction.CLEAR_CLIPBOARD))
  if (selected > 0) add(VfsSelectionAction.DELETE)
}

fun VfsSelectionAction.icon(): ImageVector =
  when (this) {
    VfsSelectionAction.SELECT_ALL -> Icons.Default.SelectAll
    VfsSelectionAction.CLEAR_SELECTION -> Icons.Default.Deselect
    VfsSelectionAction.RENAME -> Icons.Default.DriveFileRenameOutline
    VfsSelectionAction.COPY -> Icons.Default.ContentCopy
    VfsSelectionAction.CUT -> Icons.Default.ContentCut
    VfsSelectionAction.MOVE -> Icons.Default.DriveFileMove
    VfsSelectionAction.PASTE -> Icons.Default.ContentPaste
    VfsSelectionAction.CLEAR_CLIPBOARD -> Icons.Default.ClearAll
    VfsSelectionAction.DELETE -> Icons.Default.Delete
  }

@Composable
fun VfsToolIcon(
  icon: ImageVector,
  label: String,
  tag: String,
  enabled: Boolean = true,
  selected: Boolean? = null,
  click: () -> Unit,
) {
  TooltipBox(
    TooltipDefaults.rememberPlainTooltipPositionProvider(),
    { PlainTooltip { Text(label) } },
    rememberTooltipState(),
  ) {
    if (selected == null)
      IconButton(click, Modifier.testTag(tag), enabled = enabled) { Icon(icon, label) }
    else
      IconToggleButton(selected, { click() }, Modifier.testTag(tag), enabled = enabled) {
        Icon(icon, label)
      }
  }
}

@Composable
fun VfsSelectionButton(action: VfsSelectionAction, enabled: Boolean = true, click: () -> Unit) =
  VfsToolIcon(
    action.icon(),
    stringResource(action.label),
    "vfs-${action.name.lowercase()}",
    enabled,
    click = click,
  )

@Composable
fun VfsActionBar(vertical: Boolean, tag: String, content: @Composable () -> Unit) {
  if (vertical)
    Column(
      Modifier.width(56.dp)
        .fillMaxHeight()
        .verticalScroll(rememberScrollState(), reserveScrollbarGutter = false)
        .testTag(tag)
    ) {
      content()
    }
  else
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(tag)) { content() }
}
