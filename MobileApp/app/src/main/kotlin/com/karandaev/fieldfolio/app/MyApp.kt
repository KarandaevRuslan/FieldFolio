package com.karandaev.fieldfolio.app

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.karandaev.fieldfolio.app.navigation.AppNavHost
import com.karandaev.fieldfolio.app.navigation.Routes
import com.karandaev.fieldfolio.app.navigation.isAuth
import com.karandaev.fieldfolio.app.navigation.rememberAppDestination
import com.karandaev.fieldfolio.app.scaffold.FieldfolioScaffold
import com.karandaev.fieldfolio.app.splash.AppSplashScreen
import com.karandaev.fieldfolio.app.splash.AppSplashViewModel
import com.karandaev.fieldfolio.core.state.PersistedAppStateViewModel
import com.karandaev.fieldfolio.core.ui.adaptive.rememberAdaptiveLayoutType
import com.karandaev.fieldfolio.core.ui.message.UiMessageDisplayMode
import com.karandaev.fieldfolio.core.ui.message.shouldShowAsToast
import com.karandaev.fieldfolio.core.util.other.toast
import com.karandaev.fieldfolio.feature.auth.state.AuthViewModel
import com.karandaev.fieldfolio.feature.auth.state.factory
import com.karandaev.fieldfolio.feature.auth.state.model.canAccessApp
import com.karandaev.fieldfolio.feature.models.legacy.DetectViewModel
import com.karandaev.fieldfolio.feature.reports.state.InspectionReportsViewModel
import com.karandaev.fieldfolio.feature.settings.state.SettingsViewModel

/**
 * Root composable of the application.
 *
 * This function wires navigation, screen-level ViewModels, adaptive layout, and the application
 * scaffold together.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MyApp(
  app: com.karandaev.fieldfolio.core.di.AppServices,
  settingsViewModel: SettingsViewModel,
  persistedAppStateViewModel: PersistedAppStateViewModel,
) {
  val inspectionReportsViewModel: InspectionReportsViewModel =
    viewModel(factory = InspectionReportsViewModel.factory(app.inspectionReportRepository))

  val authViewModel: AuthViewModel =
    viewModel(
      factory =
        AuthViewModel.factory(
          auth = app.firebaseAuth,
          firebaseGoogleAuthProvider = app.firebaseGoogleAuthProvider,
          persistedAppStateRepository = app.persistedAppStateRepository,
          inspectionProvider = app.inspectionProvider,
          imageSourceFileProvider = app.imageSourceFileProvider,
          imageCropper = app.imageCropper,
        )
    )

  val detectViewModel: DetectViewModel =
    viewModel(
      factory =
        DetectViewModel.factory(
          inspectionProvider = app.inspectionProvider,
          imageSourceFileProvider = app.imageSourceFileProvider,
          imageCropper = app.imageCropper,
        )
    )

  val splashViewModel: AppSplashViewModel = viewModel()

  val navController = rememberNavController()
  LibrarySecurityEffect(app.reportLibrary, navController, detectViewModel::resetForNewDetection)
  val navBackStackEntry by navController.currentBackStackEntryAsState()

  val inspectionReports by
    inspectionReportsViewModel.inspectionReports.collectAsStateWithLifecycle()
  val authState by authViewModel.state.collectAsStateWithLifecycle()
  val isSplashVisible by splashViewModel.isSplashVisible.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val authMessage = authState.message

  val currentRoute = navBackStackEntry?.destination?.route
  val isNavigationReady = currentRoute != null
  val isAuthRoute = currentRoute.isAuth()
  val canAccessApp = authState.canAccessApp

  val destination =
    rememberAppDestination(
      navBackStackEntry = navBackStackEntry,
      inspectionReports = inspectionReports,
    )

  LaunchedEffect(authMessage?.refreshId) {
    val message = authMessage ?: return@LaunchedEffect

    if (!message.shouldShowAsToast) {
      return@LaunchedEffect
    }

    context.toast(message.text)

    if (message.displayMode == UiMessageDisplayMode.Toast) {
      authViewModel.clearMessage()
    }
  }

  LaunchedEffect(canAccessApp, currentRoute) {
    if (!canAccessApp) app.reportLibrary.lockAll()
    if (!isNavigationReady) {
      return@LaunchedEffect
    }

    if (!canAccessApp && !isAuthRoute) {
      navController.navigate(Routes.AUTH_LOGIN) {
        popUpTo(navController.graph.id) { inclusive = true }
        launchSingleTop = true
      }
      return@LaunchedEffect
    }

    if (canAccessApp && isAuthRoute) {
      navController.navigate(Routes.HOME) {
        popUpTo(navController.graph.id) { inclusive = true }
        launchSingleTop = true
      }
    }
  }

  BoxWithConstraints {
    val layoutType = rememberAdaptiveLayoutType(maxWidth = maxWidth, maxHeight = maxHeight)

    Box {
      FieldfolioScaffold(
        destination = destination,
        layoutType = layoutType,
        navController = navController,
      ) { contentModifier ->
        AppNavHost(
          navController = navController,
          inspectionReportsViewModel = inspectionReportsViewModel,
          settingsViewModel = settingsViewModel,
          persistedAppStateViewModel = persistedAppStateViewModel,
          authViewModel = authViewModel,
          detectViewModel = detectViewModel,
          inspectionReports = inspectionReports,
          modifier = contentModifier,
        )
      }

      if (isSplashVisible) {
        AppSplashScreen()
      }
    }
  }
}
