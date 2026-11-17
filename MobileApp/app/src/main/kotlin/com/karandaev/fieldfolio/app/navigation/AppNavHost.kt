package com.karandaev.fieldfolio.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.state.PersistedAppStateViewModel
import com.karandaev.fieldfolio.feature.auth.login.LoginRoute
import com.karandaev.fieldfolio.feature.auth.register.RegisterRoute
import com.karandaev.fieldfolio.feature.auth.reset.PasswordResetRoute
import com.karandaev.fieldfolio.feature.auth.state.AuthViewModel
import com.karandaev.fieldfolio.feature.detection.DetectRoute
import com.karandaev.fieldfolio.feature.home.HomeRoute
import com.karandaev.fieldfolio.feature.models.legacy.DetectViewModel
import com.karandaev.fieldfolio.feature.profile.ProfileRoute
import com.karandaev.fieldfolio.feature.reports.state.InspectionReportsViewModel
import com.karandaev.fieldfolio.feature.settings.SettingsRoute
import com.karandaev.fieldfolio.feature.settings.state.SettingsViewModel

/**
 * Main application NavHost.
 *
 * Owns screen route registration and connects navigation events from screens to the root
 * [NavHostController].
 */
@Composable
fun AppNavHost(
  navController: NavHostController,
  inspectionReportsViewModel: InspectionReportsViewModel,
  settingsViewModel: SettingsViewModel,
  persistedAppStateViewModel: PersistedAppStateViewModel,
  authViewModel: AuthViewModel,
  detectViewModel: DetectViewModel,
  inspectionReports: List<InspectionReport>,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    NavHost(navController = navController, startDestination = Routes.AUTH_LOGIN) {
      authRoutes(
        authViewModel = authViewModel,
        onLoginClick = {
          navController.navigateToAuthRoute(
            route = Routes.AUTH_LOGIN,
            authViewModel = authViewModel,
          )
        },
        onRegisterSuccess = {
          navController.navigateToAuthRoute(
            route = Routes.AUTH_LOGIN,
            authViewModel = authViewModel,
            clearMessage = false,
          )
        },
        onPasswordResetSuccess = {
          navController.navigateToAuthRoute(
            route = Routes.AUTH_LOGIN,
            authViewModel = authViewModel,
            clearMessage = false,
          )
        },
        onRegisterClick = {
          navController.navigateToAuthRoute(
            route = Routes.AUTH_REGISTER,
            authViewModel = authViewModel,
          )
        },
        onPasswordResetClick = {
          navController.navigateToAuthRoute(
            route = Routes.AUTH_PASSWORD_RESET,
            authViewModel = authViewModel,
          )
        },
      )

      homeRoute(
        inspectionReports = inspectionReports,
        inspectionReportsViewModel = inspectionReportsViewModel,
        onInspectionReportClick = { inspectionReportId ->
          navController.navigate(Routes.viewNote(inspectionReportId))
        },
        onInspectionReportEdit = { inspectionReportId ->
          navController.navigate(Routes.editNote(inspectionReportId))
        },
        onCreateInFolder = { parent -> navController.navigate(Routes.create(parentId = parent)) },
      )

      composable(Routes.MAP) {
        com.karandaev.fieldfolio.feature.document.viewer.DocumentMapRoute(
          inspectionReports,
          persistedAppStateViewModel,
          open = { id, folder ->
            navController.navigate(Routes.viewNote(id))
            if (folder != null) navController.navigate(Routes.viewFolder(id, folder))
          },
          create = { lat, lon -> navController.navigate(Routes.create(lat, lon)) },
        )
      }
      composable(Routes.MODELS) { com.karandaev.fieldfolio.feature.models.ModelsRoute() }

      detectRoute(
        detectViewModel = detectViewModel,
        onBackClick = navController::popBackStack,
        onDetectionComplete = { navController.popBackStack() },
      )

      profileRoute(authViewModel = authViewModel, onBackClick = navController::popBackStack)

      settingsRoute(
        settingsViewModel = settingsViewModel,
        authViewModel = authViewModel,
        inspectionReports = inspectionReports,
        onProfileClick = { navController.navigate(Routes.PROFILE) },
        onSignInClick = { authViewModel.clearGuestModeSilently() },
      )

      documentRoutes(navController, inspectionReportsViewModel)
    }
  }
}

private fun NavGraphBuilder.authRoutes(
  authViewModel: AuthViewModel,
  onLoginClick: () -> Unit,
  onRegisterSuccess: () -> Unit,
  onPasswordResetSuccess: () -> Unit,
  onRegisterClick: () -> Unit,
  onPasswordResetClick: () -> Unit,
) {
  composable(Routes.AUTH_LOGIN) {
    LoginRoute(
      authViewModel = authViewModel,
      onRegisterClick = onRegisterClick,
      onPasswordResetClick = onPasswordResetClick,
    )
  }

  composable(Routes.AUTH_REGISTER) {
    RegisterRoute(
      authViewModel = authViewModel,
      onLoginClick = onLoginClick,
      onRegisterSuccess = onRegisterSuccess,
    )
  }

  composable(Routes.AUTH_PASSWORD_RESET) {
    PasswordResetRoute(
      authViewModel = authViewModel,
      onLoginClick = onLoginClick,
      onPasswordResetSuccess = onPasswordResetSuccess,
    )
  }
}

private fun NavGraphBuilder.detectRoute(
  detectViewModel: DetectViewModel,
  onBackClick: () -> Unit,
  onDetectionComplete: () -> Unit,
) {
  composable(Routes.DETECT) {
    DetectRoute(
      detectViewModel = detectViewModel,
      onBackClick = onBackClick,
      onDetectionComplete = onDetectionComplete,
    )
  }
}

private fun NavGraphBuilder.profileRoute(authViewModel: AuthViewModel, onBackClick: () -> Unit) {
  composable(Routes.PROFILE) {
    ProfileRoute(authViewModel = authViewModel, onBackClick = onBackClick)
  }
}

private fun NavGraphBuilder.homeRoute(
  inspectionReports: List<InspectionReport>,
  inspectionReportsViewModel: InspectionReportsViewModel,
  onInspectionReportClick: (Long) -> Unit,
  onInspectionReportEdit: (Long) -> Unit,
  onCreateInFolder: (Long?) -> Unit,
) {
  composable(Routes.HOME) {
    HomeRoute(
      inspectionReports = inspectionReports,
      onInspectionReportClick = onInspectionReportClick,
      onInspectionReportEdit = onInspectionReportEdit,
      onCreateInFolder = onCreateInFolder,
      onInspectionReportDelete = { inspectionReportId ->
        inspectionReportsViewModel.deleteInspectionReport(inspectionReportId)
      },
    )
  }
}

private fun NavGraphBuilder.settingsRoute(
  settingsViewModel: SettingsViewModel,
  authViewModel: AuthViewModel,
  inspectionReports: List<InspectionReport>,
  onProfileClick: () -> Unit,
  onSignInClick: () -> Unit,
) {
  composable(Routes.SETTINGS) {
    SettingsRoute(
      settingsViewModel = settingsViewModel,
      authViewModel = authViewModel,
      inspectionReports = inspectionReports,
      onProfileClick = onProfileClick,
      onSignInClick = onSignInClick,
    )
  }
}

private fun NavHostController.navigateToAuthRoute(
  route: String,
  authViewModel: AuthViewModel,
  clearMessage: Boolean = true,
) {
  authViewModel.clearUserName()
  authViewModel.clearPassword()

  if (clearMessage) {
    authViewModel.clearMessage()
  }

  authViewModel.clearProfileAvatarImage()
  authViewModel.cancelSensitiveOperation()

  navigate(route) { launchSingleTop = true }
}
