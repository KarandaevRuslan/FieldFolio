package com.karandaev.fieldfolio.feature.settings.components.sections

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.ui.text.tr

@Composable
internal fun SettingsDocumentSection() {
  SettingsTextSection()
  SettingsAudioSection()
}

@Composable
internal fun SettingsTextSection() {
  val context = LocalContext.current
  val size by rememberTextPageSize()
  SettingsSection(
    androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.final_text_settings)
  ) {
    CompactChoice(
      tr("text_page_size"),
      "text-page-size",
      size,
      DocumentTextPreferences.options.associateWith { it.toString() },
    ) {
      DocumentTextPreferences.set(context, it)
    }
    val editor = remember { com.karandaev.fieldfolio.acode.settings.EditorPreferences(context) }
    CompactChoice(
      androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.final_font),
      "acode-font-size",
      editor.fontSize,
      (11..24).associateWith { "$it" },
    ) {
      editor.updateFontSize(it)
    }
    androidx.compose.material3.ListItem(
      headlineContent = {
        androidx.compose.material3.Text(
          androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.final_wrap)
        )
      },
      trailingContent = {
        androidx.compose.material3.Switch(editor.wordWrap, editor::updateWordWrap)
      },
    )
  }
}

@Composable
internal fun SettingsAudioSection() {
  val context = LocalContext.current
  val seconds by rememberAudioSeekSeconds()
  SettingsSection(tr("audio")) {
    CompactChoice(
      tr("audio_seek_step"),
      "audio-seek-step",
      seconds,
      AudioPreferences.options.associateWith {
        androidx.compose.ui.res.stringResource(
          com.karandaev.fieldfolio.R.string.doc_seconds_value,
          it,
        )
      },
    ) {
      AudioPreferences.set(context, it)
    }
  }
}
