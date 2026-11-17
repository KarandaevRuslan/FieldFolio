package com.karandaev.fieldfolio.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R

/** Resolves the same localized metadata labels for the editor and the audio viewer. */
@Composable
internal fun audioTagLabel(key: String): String =
  stringResource(
    when (key) {
      "title" -> R.string.final_tag_title
      "artist" -> R.string.final_tag_artist
      "album" -> R.string.final_tag_album
      "genre" -> R.string.final_tag_genre
      "year" -> R.string.final_tag_year
      "track" -> R.string.final_tag_track
      else -> R.string.final_tag_composer
    }
  )
