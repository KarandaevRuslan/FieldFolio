package com.karandaev.fieldfolio.feature.home

import androidx.compose.runtime.Composable
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.domain.model.InspectionReport

/** Reports library. Weather and clock components remain available for document widgets. */
@Composable
fun HomeRoute(
  inspectionReports: List<InspectionReport>,
  onInspectionReportClick: (Long) -> Unit,
  onInspectionReportEdit: (Long) -> Unit,
  onInspectionReportDelete: (Long) -> Unit,
  onCreateInFolder: (Long?) -> Unit,
) {
  val app = rememberAppServices()

  val libraryViewModel: com.karandaev.fieldfolio.feature.library.LibraryViewModel =
    androidx.lifecycle.viewmodel.compose.viewModel(
      factory =
        object : androidx.lifecycle.ViewModelProvider.Factory {
          override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return com.karandaev.fieldfolio.feature.library.LibraryViewModel(app.reportLibrary) as T
          }
        }
    )

  com.karandaev.fieldfolio.feature.library.LibraryBrowser(
    vm = libraryViewModel,
    onOpen = onInspectionReportClick,
    onEdit = onInspectionReportEdit,
    onCreate = onCreateInFolder,
  )
}
