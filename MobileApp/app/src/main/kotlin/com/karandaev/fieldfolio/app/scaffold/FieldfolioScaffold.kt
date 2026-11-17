package com.karandaev.fieldfolio.app.scaffold

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.karandaev.fieldfolio.app.navigation.AppDestination
import com.karandaev.fieldfolio.app.navigation.Routes
import com.karandaev.fieldfolio.core.ui.adaptive.AdaptiveLayoutType

/**
 * Adaptive application scaffold coordinator.
 *
 * Chooses the appropriate root chrome for the current window size:
 * - bottom navigation for compact layouts;
 * - navigation rail for wide or landscape layouts.
 */
@Composable
fun FieldfolioScaffold(
  destination: AppDestination,
  layoutType: AdaptiveLayoutType,
  navController: NavHostController,
  content: @Composable (Modifier) -> Unit,
) {
  val onNavigateToTopLevelDestination: (String) -> Unit = { route ->
    navController.navigateToTopLevelDestination(route)
  }

  val onCreateNoteClick = { navController.navigate(Routes.create()) }

  val latestContent by rememberUpdatedState(content)
  val stableContent = remember {
    movableContentOf<Modifier> { modifier -> latestContent(modifier) }
  }
  when (layoutType) {
    AdaptiveLayoutType.SinglePane -> {
      MapNotesSinglePaneScaffold(
        destination = destination,
        onBackClick = navController::popBackStack,
        onNavigateToTopLevelDestination = onNavigateToTopLevelDestination,
        onCreateNoteClick = onCreateNoteClick,
        content = stableContent,
      )
    }

    AdaptiveLayoutType.TwoPane -> {
      MapNotesTwoPaneScaffold(
        destination = destination,
        onNavigateToTopLevelDestination = onNavigateToTopLevelDestination,
        onCreateNoteClick = onCreateNoteClick,
        content = stableContent,
      )
    }
  }
}

/** Navigates to a top-level destination while preserving and restoring destination state. */
private fun NavHostController.navigateToTopLevelDestination(route: String) {
  navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }

    launchSingleTop = true
    restoreState = true
  }
}
