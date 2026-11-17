package com.karandaev.fieldfolio.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.app.navigation.TopLevelDestination
import com.karandaev.fieldfolio.app.navigation.TopLevelDestinations
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll

private val AppNavigationRailTopPadding = 8.dp
private val AppNavigationRailBottomPadding = 8.dp
private val AppNavigationRailFabReservedBottomPadding = 96.dp
private val AppNavigationRailFabBottomPadding = 16.dp
private val AppNavigationRailItemSpacing = 6.dp

/**
 * Side navigation rail for wide and landscape layouts.
 *
 * The route list is scrollable so landscape phones can fit all navigation items. The create-note
 * action is anchored at the bottom and does not shift route items.
 */
@Composable
fun AppNavigationRail(
  currentRoute: String?,
  showCreateNoteButton: Boolean,
  onCreateNoteClick: () -> Unit,
  onNavigate: (String) -> Unit,
  modifier: Modifier = Modifier,
  destinations: List<TopLevelDestination> = TopLevelDestinations,
) {
  NavigationRail(
    modifier =
      modifier
        .testTag("app-navigation-rail")
        .fillMaxHeight()
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
    windowInsets = WindowInsets.statusBars.only(WindowInsetsSides.Top),
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Box(modifier = Modifier.fillMaxHeight()) {
      Column(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .verticalScroll(rememberScrollState(), reserveScrollbarGutter = false)
            .padding(
              PaddingValues(
                top = AppNavigationRailTopPadding,
                bottom =
                  if (showCreateNoteButton) {
                    AppNavigationRailFabReservedBottomPadding
                  } else {
                    AppNavigationRailBottomPadding
                  },
              )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppNavigationRailItemSpacing),
      ) {
        destinations.forEach { destination ->
          NavigationRailItem(
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
              NavigationRailItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
              ),
          )
        }
      }

      if (showCreateNoteButton) {
        CreateNoteFab(
          onClick = onCreateNoteClick,
          modifier =
            Modifier.align(Alignment.BottomCenter)
              .padding(bottom = AppNavigationRailFabBottomPadding),
        )
      }
    }
  }
}
