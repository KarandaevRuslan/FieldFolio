package com.karandaev.fieldfolio.feature.settings.state

import com.karandaev.fieldfolio.core.domain.model.AppSettings
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.ThemeMode
import com.karandaev.fieldfolio.core.domain.repository.InspectionReportRepository
import com.karandaev.fieldfolio.core.domain.repository.SettingsRepository
import com.karandaev.fieldfolio.core.inspectionapi.InspectionProvider
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsResetActionTest {
  private class Reports : InspectionReportRepository {
    var resetCalls = 0
    var resetBarrier: CompletableDeferred<Unit>? = null
    var resetError: Exception? = null

    override suspend fun resetToDefaults() {
      resetCalls++
      resetBarrier?.await()
      resetError?.let { throw it }
    }

    override suspend fun saveWithImage(
      report: InspectionReport,
      parentId: Long?,
      imagePath: String?,
      removeImage: Boolean,
    ): Long = error("unused")

    override fun observeInspectionReports(): Flow<List<InspectionReport>> = flowOf(emptyList())

    override fun observeInspectionReportById(id: Long): Flow<InspectionReport?> = flowOf(null)

    override suspend fun getInspectionReportById(id: Long): InspectionReport? = null

    override suspend fun insert(inspectionReport: InspectionReport): Long = error("unused")

    override suspend fun insertAll(inspectionReports: List<InspectionReport>): List<Long> =
      error("unused")

    override suspend fun update(inspectionReport: InspectionReport) = error("unused")

    override suspend fun deleteById(id: Long) = error("unused")
  }

  private val settings =
    object : SettingsRepository {
      override fun observeSettings(): Flow<AppSettings> = flowOf(AppSettings())

      override suspend fun ensureDefaults() = Unit

      override suspend fun setThemeMode(themeMode: ThemeMode) = Unit

      override suspend fun setLanguageCode(languageCode: String) = Unit

      override suspend fun setServerBaseUrl(serverBaseUrl: String) = Unit
    }

  private fun viewModel(reports: Reports): SettingsViewModel {
    val provider =
      Proxy.newProxyInstance(
        InspectionProvider::class.java.classLoader,
        arrayOf(InspectionProvider::class.java),
      ) { _, method, _ ->
        error("Unexpected provider call: ${method.name}")
      } as InspectionProvider
    return SettingsViewModel(settings, reports, provider)
  }

  @Test
  fun resetWaitsForRepositoryBeforeClosingDialog() = runBlocking {
    val reports = Reports()
    val barrier = CompletableDeferred<Unit>()
    reports.resetBarrier = barrier
    val vm = viewModel(reports)
    vm.showResetDataDialog()
    vm.setResetDataConfirmationText(RESET_DATA_CONFIRMATION_WORD)

    val reset = async(start = CoroutineStart.UNDISPATCHED) { vm.resetInspectionReportsToDefaults() }
    assertEquals(1, reports.resetCalls)
    assertFalse(reset.isCompleted)
    assertTrue(vm.runtimeUiState.value.dialogState is SettingsDialogState.ResetData)

    barrier.complete(Unit)
    reset.await()
    assertEquals(SettingsDialogState.None, vm.runtimeUiState.value.dialogState)
  }

  @Test
  fun repositoryFailureIsPropagatedAndDialogStaysOpen() = runBlocking {
    val reports = Reports().apply { resetError = IOException("storage failed") }
    val vm = viewModel(reports)
    vm.showResetDataDialog()
    vm.setResetDataConfirmationText(RESET_DATA_CONFIRMATION_WORD)

    try {
      vm.resetInspectionReportsToDefaults()
      fail("Reset must report repository failure")
    } catch (expected: IOException) {
      assertEquals("storage failed", expected.message)
    }

    assertEquals(1, reports.resetCalls)
    assertTrue(vm.runtimeUiState.value.dialogState is SettingsDialogState.ResetData)
  }

  @Test
  fun invalidConfirmationCannotResetRepository() = runBlocking {
    val reports = Reports()
    val vm = viewModel(reports)
    vm.showResetDataDialog()
    vm.setResetDataConfirmationText("wrong")

    try {
      vm.resetInspectionReportsToDefaults()
      fail("Reset must require confirmation")
    } catch (expected: IllegalStateException) {
      assertEquals(0, reports.resetCalls)
    }
    assertTrue(vm.runtimeUiState.value.dialogState is SettingsDialogState.ResetData)
  }
}
