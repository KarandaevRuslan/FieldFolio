package com.karandaev.fieldfolio.app.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.*
import androidx.navigation.compose.composable
import com.karandaev.fieldfolio.app.navigation.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.route.DocumentRoute
import com.karandaev.fieldfolio.feature.reports.state.InspectionReportsViewModel

fun NavGraphBuilder.documentRoutes(nav: NavHostController, reports: InspectionReportsViewModel) {
  val idArg = listOf(navArgument(Routes.ARG_INSPECTION_REPORT_ID) { type = NavType.LongType })
  fun openFolder(id: Long, folder: String?) {
    val route = if (folder == null) Routes.viewNote(id) else Routes.viewFolder(id, folder)
    if (
      nav.currentBackStackEntry?.destination?.route == Routes.VIEW_FOLDER_PATTERN &&
        nav.currentBackStackEntry?.arguments?.getString("folderId") == folder
    )
      return
    if (nav.currentBackStackEntry?.destination?.route == Routes.VIEW_PATTERN && folder == null)
      return
    if (!nav.popBackStack(route, false)) nav.navigate(route) { launchSingleTop = true }
  }
  composable(
    Routes.CREATE_PATTERN,
    arguments =
      listOf(Routes.ARG_LAT, Routes.ARG_LON, Routes.ARG_PARENT).map { key ->
        navArgument(key) {
          type = NavType.StringType
          nullable = true
          defaultValue = null
        }
      },
  ) { entry ->
    val lat = entry.arguments?.getString(Routes.ARG_LAT)?.toDoubleOrNull()
    val lon = entry.arguments?.getString(Routes.ARG_LON)?.toDoubleOrNull()
    DocumentRoute(
      parentId = entry.arguments?.getString(Routes.ARG_PARENT)?.toLongOrNull(),
      initialPoint = if (lat != null && lon != null) MapPoint(lat, lon) else null,
      editing = true,
      owner = entry,
      onBack = { nav.popBackStack() },
      onSaved = { id ->
        nav.popBackStack()
        nav.navigate(Routes.viewNote(id))
      },
      onEdit = {},
      onFolder = {},
      onDelete = {},
    )
  }
  composable(Routes.EDIT_PATTERN, arguments = idArg) { entry ->
    val id = entry.arguments!!.getLong(Routes.ARG_INSPECTION_REPORT_ID)
    DocumentRoute(
      reportId = id,
      editing = true,
      owner = entry,
      onBack = { nav.popBackStack() },
      onSaved = {
        nav.popBackStack()
        if (nav.currentBackStackEntry?.destination?.route?.startsWith("${Routes.VIEW}/") != true)
          nav.navigate(Routes.viewNote(id))
      },
      onEdit = {},
      onFolder = {},
      onDelete = {},
    )
  }
  composable(Routes.VIEW_PATTERN, arguments = idArg) { entry ->
    val id = entry.arguments!!.getLong(Routes.ARG_INSPECTION_REPORT_ID)
    val items by reports.inspectionReports.collectAsStateWithLifecycle()
    DocumentRoute(
      reportId = id,
      editing = false,
      owner = entry,
      revision = items.firstOrNull { it.id == id }?.lastUpdatedAtMillis,
      onBack = { nav.popBackStack() },
      onSaved = {},
      onEdit = { nav.navigate(Routes.editNote(id)) },
      onFolder = { openFolder(id, it) },
      onDelete = {
        reports.deleteInspectionReport(id)
        nav.popBackStack()
      },
    )
  }
  composable(
    Routes.VIEW_FOLDER_PATTERN,
    arguments = idArg + navArgument("folderId") { type = NavType.StringType },
  ) { entry ->
    val id = entry.arguments!!.getLong(Routes.ARG_INSPECTION_REPORT_ID)
    val folder = entry.arguments!!.getString("folderId")
    val owner =
      remember(entry) {
        runCatching { nav.getBackStackEntry(Routes.viewNote(id)) }.getOrDefault(entry)
      }
    val items by reports.inspectionReports.collectAsStateWithLifecycle()
    DocumentRoute(
      reportId = id,
      editing = false,
      owner = owner,
      folderScope = folder,
      revision = items.firstOrNull { it.id == id }?.lastUpdatedAtMillis,
      onBack = { nav.popBackStack() },
      onSaved = {},
      onEdit = { nav.navigate(Routes.editNote(id)) },
      onFolder = { openFolder(id, it) },
      onDelete = {
        reports.deleteInspectionReport(id)
        nav.popBackStack(Routes.HOME, false)
      },
    )
  }
}
