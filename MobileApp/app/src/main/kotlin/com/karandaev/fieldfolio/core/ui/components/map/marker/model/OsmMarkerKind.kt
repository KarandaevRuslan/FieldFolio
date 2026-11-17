package com.karandaev.fieldfolio.core.ui.components.map.marker.model

import androidx.compose.runtime.Immutable

/** Identifies the semantic role of a marker on the map. */
@Immutable
enum class OsmMarkerKind {
  Note,
  Selected,
  Current,
}
