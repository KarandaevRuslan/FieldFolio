@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.settings.components.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.domain.model.ThemeMode
import com.karandaev.fieldfolio.core.localization.AppLanguage
import com.karandaev.fieldfolio.core.ui.state.rememberSession

@Composable
internal fun SettingsAppearanceSection(
  themeMode: ThemeMode,
  languageCode: String,
  onLanguageCodeSelect: (String) -> Unit,
  onThemeModeSelect: (ThemeMode) -> Unit,
) {
  SettingsSection(stringResource(R.string.ui_appearance)) {
    CompactChoice(
      stringResource(R.string.ui_theme),
      "theme",
      themeMode,
      linkedMapOf(
        ThemeMode.LIGHT to stringResource(R.string.ui_light),
        ThemeMode.DARK to stringResource(R.string.ui_dark),
      ),
      onThemeModeSelect,
    )
    HorizontalDivider(
      Modifier.padding(horizontal = 16.dp),
      color = MaterialTheme.colorScheme.outlineVariant,
    )
    CompactChoice(
      stringResource(R.string.ui_language),
      "language",
      AppLanguage.normalize(languageCode),
      linkedMapOf(
        "system" to stringResource(R.string.ui_language_system),
        "en" to stringResource(R.string.ui_language_en),
        "ru" to stringResource(R.string.ui_language_ru),
      ),
      onLanguageCodeSelect,
    )
  }
}

@Composable
internal fun <T> CompactChoice(
  label: String,
  tag: String,
  selected: T,
  options: Map<T, String>,
  select: (T) -> Unit,
) {
  var expanded by rememberSession { mutableStateOf(false) }
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      label,
      Modifier.weight(1f).testTag("$tag-label"),
      style = MaterialTheme.typography.bodyLarge,
    )
    ExposedDropdownMenuBox(
      expanded,
      { expanded = it },
      Modifier.width(172.dp).testTag("$tag-selector"),
    ) {
      Surface(
        Modifier.menuAnchor().fillMaxWidth().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Row(
          Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            options.getValue(selected),
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
          )
          Icon(Icons.Default.ExpandMore, null, Modifier.size(20.dp))
        }
      }
      ExposedDropdownMenu(expanded, { expanded = false }) {
        options.forEach { (value, text) ->
          DropdownMenuItem(
            text = { Text(text) },
            onClick = {
              expanded = false
              select(value)
            },
            leadingIcon = { if (value == selected) Icon(Icons.Default.Check, null) },
            modifier = Modifier.testTag("$tag-$value"),
          )
        }
      }
    }
  }
}
