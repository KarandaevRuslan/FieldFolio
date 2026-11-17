package com.karandaev.fieldfolio.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Describes a top-level app destination shown in the bottom navigation bar.
 *
 * Top-level destinations do not show the back button and are restored when switching between bottom
 * navigation tabs.
 */
data class TopLevelDestination(val route: String, val labelRes: Int, val icon: ImageVector)

/** Top-level destinations available from the bottom navigation bar. */
val TopLevelDestinations =
  listOf(
    TopLevelDestination(
      route = Routes.HOME,
      labelRes = com.karandaev.fieldfolio.R.string.ui_reports,
      icon = Icons.Default.Description,
    ),
    TopLevelDestination(
      route = Routes.MAP,
      labelRes = com.karandaev.fieldfolio.R.string.destination_map,
      icon = Icons.Default.Map,
    ),
    TopLevelDestination(
      route = Routes.MODELS,
      labelRes = com.karandaev.fieldfolio.R.string.doc_models,
      icon = Icons.Default.Memory,
    ),
    TopLevelDestination(
      route = Routes.SETTINGS,
      labelRes = com.karandaev.fieldfolio.R.string.destination_settings,
      icon = Icons.Default.Settings,
    ),
  )

/** Checks whether the route belongs to a top-level destination. */
fun String?.isTopLevelDestination(): Boolean {
  return TopLevelDestinations.any { destination -> destination.route == this }
}
