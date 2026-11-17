package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.source.OsmFocusPointAction
import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.source.OsmFocusPointActionIcon

/** Title and compact resource actions; keep refresh outside the destination list. */
@Composable
internal fun FocusMenuHeader(actions: List<OsmFocusPointAction> = emptyList()) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(R.string.ui_focus_destination),
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    actions.forEach { action ->
      HintIconButton(
        onClick = action.onClick,
        modifier = Modifier.size(40.dp),
        enabled = action.enabled,
        tooltip = action.title,
      ) {
        if (action.busy) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          Icon(
            imageVector = when (action.icon) {
              OsmFocusPointActionIcon.RefreshLocation -> Icons.Default.Refresh
              OsmFocusPointActionIcon.GrantLocation -> Icons.Default.LocationOn
            },
            contentDescription = action.title,
          )
        }
      }
    }
  }
}
