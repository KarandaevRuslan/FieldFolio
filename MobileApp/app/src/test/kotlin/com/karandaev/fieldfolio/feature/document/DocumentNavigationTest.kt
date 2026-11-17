package com.karandaev.fieldfolio.feature.document

import androidx.activity.ComponentActivity
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.*
import com.google.firebase.*
import com.karandaev.fieldfolio.app.MyApplication
import com.karandaev.fieldfolio.app.navigation.Routes
import com.karandaev.fieldfolio.app.navigation.documentRoutes
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.library.saveDocument
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.reports.state.InspectionReportsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MyApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentNavigationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun repeatedClicksOnCurrentBreadcrumbDoNotRequireRepeatedSystemBack() {
    val app = compose.activity.application as MyApplication
    if (FirebaseApp.getApps(app).isEmpty())
      FirebaseApp.initializeApp(
        app,
        FirebaseOptions.Builder()
          .setApplicationId("1:123:android:test")
          .setApiKey("test-key")
          .setProjectId("local-test")
          .build(),
      )
    val id = runBlocking {
      app.reportLibrary.saveDocument(
        0,
        null,
        "Report",
        "",
        ReportDocument(
          nodes =
            listOf(
              DocumentNode(id = "group", name = "Group", kind = BlockKind.FOLDER),
              DocumentNode(
                id = "text",
                parentId = "group",
                name = "Notes",
                kind = BlockKind.TEXT,
                body = "Inside group",
              ),
            )
        ),
        emptyMap(),
      )
    }
    lateinit var nav: androidx.navigation.NavHostController
    val reports = InspectionReportsViewModel(app.reportLibrary)
    val store = androidx.lifecycle.ViewModelStore().apply { put("reports", reports) }
    try {
      compose.setContent {
        MaterialTheme {
          nav = rememberNavController()
          NavHost(nav, Routes.viewNote(id)) { documentRoutes(nav, reports) }
        }
      }
      compose.waitUntil(15000) {
        compose.onAllNodesWithText("Group").fetchSemanticsNodes().isNotEmpty()
      }
      compose
        .onNode(
          hasContentDescription("Open folder") and hasAnyAncestor(hasTestTag("report-cell-group"))
        )
        .performClick()
      compose.waitUntil(10000) {
        compose.onAllNodesWithText("Inside group").fetchSemanticsNodes().isNotEmpty()
      }
      val count = nav.currentBackStack.value.size
      repeat(12) { compose.onNodeWithText("› Group").performClick() }
      assertEquals(count, nav.currentBackStack.value.size)
      compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
      assertEquals(Routes.VIEW_PATTERN, nav.currentBackStackEntry?.destination?.route)
    } finally {
      store.clear()
    }
  }
}
