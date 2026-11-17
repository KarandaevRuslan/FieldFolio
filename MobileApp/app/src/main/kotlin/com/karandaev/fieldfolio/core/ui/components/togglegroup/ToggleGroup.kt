package com.karandaev.fieldfolio.core.ui.components.togglegroup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Universal multi-option toggle group.
 *
 * Prefer stable keys for options:
 *
 * ToggleGroup( options = mapOf( ThemeMode.System to "System", ThemeMode.Light to
 * stringResource(R.string.ui_light), ThemeMode.Dark to stringResource(R.string.ui_dark) ),
 * selectedOption = selectedTheme, onSelect = { selectedTheme = it } )
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ToggleGroup(
  options: Map<T, String>,
  selectedOption: T?,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  itemEnabled: (T) -> Boolean = { true },
  colors: ToggleGroupColors = ToggleGroupDefaults.colors(),
  shape: Shape = ToggleGroupDefaults.Shape,
  itemShape: Shape = ToggleGroupDefaults.ItemShape,
  textStyle: TextStyle = MaterialTheme.typography.labelLarge,
  horizontalPadding: Dp = ToggleGroupDefaults.HorizontalPadding,
  verticalPadding: Dp = ToggleGroupDefaults.VerticalPadding,
  itemModifier: RowScope.(option: T, isSelected: Boolean) -> Modifier = { _, _ -> Modifier },
) {
  FlowRow(modifier = modifier.clip(shape).background(colors.containerColor).selectableGroup()) {
    options.forEach { (option, label) ->
      val isSelected = option == selectedOption
      val isItemEnabled = enabled && itemEnabled(option)

      Text(
        text = label,
        style = textStyle,
        color = colors.contentColor(selected = isSelected, enabled = isItemEnabled),
        modifier =
          Modifier.clip(itemShape)
            .then(
              if (isSelected) {
                Modifier.background(colors.selectedContainerColor)
              } else {
                Modifier
              }
            )
            .selectable(selected = isSelected, enabled = isItemEnabled, role = Role.RadioButton) {
              onSelect(option)
            }
            .heightIn(min = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .then(itemModifier(option, isSelected)),
      )
    }
  }
}
