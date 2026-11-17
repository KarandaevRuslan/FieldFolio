package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model.OsmFocusMenuItem
import com.karandaev.fieldfolio.core.ui.scroll.AppLazyColumn as LazyColumn

/** Displays selectable focus menu destination rows. */
@Composable
internal fun FocusMenuItemList(
  items: List<OsmFocusMenuItem>,
  showSearch: Boolean,
  onItemClick: (OsmFocusMenuItem) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxWidth().padding(top = if (showSearch) 0.dp else 8.dp),
    contentPadding = PaddingValues(vertical = 4.dp),
  ) {
    items(items = items, key = { item -> item.id }) { item ->
      FocusMenuRow(item = item, onClick = { onItemClick(item) })
    }
  }
}
