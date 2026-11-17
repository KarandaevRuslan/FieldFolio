package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.ui.filter

import com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model.OsmFocusMenuItem

/** Returns items whose title contains the search query. */
internal fun List<OsmFocusMenuItem>.filterByTitle(query: String): List<OsmFocusMenuItem> {
  val normalizedQuery = query.trim()

  if (normalizedQuery.isEmpty()) {
    return this
  }

  return filter { item -> item.title.contains(other = normalizedQuery, ignoreCase = true) }
}
