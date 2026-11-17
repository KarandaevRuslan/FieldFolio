package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model.OsmFocusMenuItem
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.source.OsmFocusPointAction
import com.karandaev.fieldfolio.core.ui.scroll.AppLazyColumn as LazyColumn

/** Displays selectable focus menu destination rows. */
@Composable
internal fun FocusMenuItemList(
  items: List<OsmFocusMenuItem>,
  actions: List<OsmFocusPointAction>,
  showSearch: Boolean,
  onItemClick: (OsmFocusMenuItem) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxWidth().padding(top = if (showSearch) 0.dp else 8.dp),
    contentPadding = PaddingValues(vertical = 4.dp),
  ) {
    items(items = actions, key = { action -> "action-${action.id}" }) { action ->
      TextButton(
        onClick = action.onClick,
        enabled = action.enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
      ) {
        Text(action.title)
      }
    }
    items(items = items, key = { item -> item.id }) { item ->
      FocusMenuRow(item = item, onClick = { onItemClick(item) })
    }
  }
}
