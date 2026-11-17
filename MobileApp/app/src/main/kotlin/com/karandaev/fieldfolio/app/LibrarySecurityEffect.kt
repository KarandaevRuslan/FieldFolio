package com.karandaev.fieldfolio.app

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.karandaev.fieldfolio.app.navigation.Routes
import com.karandaev.fieldfolio.core.library.LibraryTree
import com.karandaev.fieldfolio.core.library.ReportLibrary
import kotlinx.coroutines.flow.catch

/** A background lock invalidates private screens and their memory-only form ViewModels. */
@Composable
internal fun LibrarySecurityEffect(
  library: ReportLibrary,
  navigation: NavHostController,
  clearDetection: () -> Unit,
) {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val context = LocalContext.current
  val activity = context as? Activity
  val entry by navigation.currentBackStackEntryAsState()
  val nodes by
    remember(library) { library.observeNodes().catch { emit(emptyList()) } }
      .collectAsStateWithLifecycle(emptyList())
  val session by library.session.collectAsStateWithLifecycle()
  val tree = remember(nodes) { runCatching { LibraryTree(nodes) }.getOrNull() }
  fun protected(id: Long?) =
    id != null && tree?.byId?.containsKey(id) == true && tree.locks(id).isNotEmpty()
  val currentId = entry?.arguments?.getLong(Routes.ARG_INSPECTION_REPORT_ID)?.takeIf { it > 0 }
  val currentParent = entry?.arguments?.getString(Routes.ARG_PARENT)?.toLongOrNull()
  val previous = navigation.previousBackStackEntry
  val detectionParentPrivate =
    entry?.destination?.route == Routes.DETECT &&
      (protected(
        previous?.arguments?.getLong(Routes.ARG_INSPECTION_REPORT_ID)?.takeIf { it > 0 }
      ) || protected(previous?.arguments?.getString(Routes.ARG_PARENT)?.toLongOrNull()))
  val privateRoute = protected(currentId) || protected(currentParent) || detectionParentPrivate
  val secure = remember(privateRoute, session) { privateRoute || library.keys.isNotEmpty() }
  LaunchedEffect(privateRoute, session, entry?.id) {
    if (privateRoute) {
      val scopeId =
        currentId
          ?: currentParent
          ?: previous?.arguments?.getLong(Routes.ARG_INSPECTION_REPORT_ID)?.takeIf { it > 0 }
          ?: previous?.arguments?.getString(Routes.ARG_PARENT)?.toLongOrNull()
      if (scopeId != null && !library.isUnlocked(scopeId)) {
        clearDetection()
        navigation.popBackStack(Routes.HOME, false)
      }
    }
  }
  val latestPrivate by rememberUpdatedState(privateRoute)
  val latestClear by rememberUpdatedState(clearDetection)
  DisposableEffect(activity, secure) {
    if (secure) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    onDispose { if (secure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
  }
  DisposableEffect(library, lifecycle, navigation) {
    val observer = LifecycleEventObserver { _, event ->
      if (
        event == Lifecycle.Event.ON_STOP &&
          !library.externalPicker &&
          activity?.isChangingConfigurations != true
      ) {
        library.lockAll()
        if (latestPrivate) {
          latestClear()
          if (!navigation.popBackStack(Routes.HOME, false))
            navigation.navigate(Routes.HOME) {
              popUpTo(navigation.graph.id) { inclusive = true }
              launchSingleTop = true
            }
        }
      }
    }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }
}
