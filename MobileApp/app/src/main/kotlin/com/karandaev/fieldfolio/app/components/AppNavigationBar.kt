package com.karandaev.fieldfolio.app.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.app.navigation.TopLevelDestination
import com.karandaev.fieldfolio.app.navigation.TopLevelDestinations

/** Bottom navigation bar for compact layouts. */
@Composable
fun AppNavigationBar(
  currentRoute: String?,
  onNavigate: (String) -> Unit,
  destinations: List<TopLevelDestination> = TopLevelDestinations,
) {
  NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
    destinations.forEach { destination ->
      NavigationBarItem(
        icon = {
          Icon(
            imageVector = destination.icon,
            contentDescription = stringResource(destination.labelRes),
          )
        },
        label = { Text(stringResource(destination.labelRes)) },
        selected = currentRoute == destination.route,
        onClick = {
          if (currentRoute != destination.route) {
            onNavigate(destination.route)
          }
        },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
      )
    }
  }
}
