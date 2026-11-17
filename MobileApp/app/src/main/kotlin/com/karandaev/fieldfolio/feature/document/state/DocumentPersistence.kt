package com.karandaev.fieldfolio.feature.document.state

import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import kotlinx.coroutines.*

internal fun DocumentViewModel.performRefresh() {
  if (busy || dirty) return
  job?.cancel()
  job =
    viewModelScope.launch {
      loading = true
      try {
        val b = library.loadDocument(reportId)
        clearFiles()
        files = b.files
        document = b.document
        title = b.report.title
        logicalName = b.name
        displayName = b.displayName
        description = b.report.content
        stamp = b.report.lastUpdatedAtMillis
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        error = e
      } finally {
        loading = false
      }
    }
}

internal fun DocumentViewModel.performRestoreProperties(
  previousTitle: String,
  previousDescription: String,
  previousCover: String?,
  wasDirty: Boolean,
) {
  title = previousTitle
  description = previousDescription
  document = document.copy(cover = previousCover)
  dirty = wasDirty
}

internal fun DocumentViewModel.performSave() {
  if (
    !com.karandaev.fieldfolio.core.names.ItemNames.valid(title) ||
      !com.karandaev.fieldfolio.core.names.ResourceIdentity(logicalName ?: title, displayName).valid
  ) {
    error = IllegalArgumentException("Invalid report name")
    return
  }
  if (busy || title.isBlank()) return
  busy = true
  job =
    viewModelScope.launch {
      try {
        val id =
          library.saveDocument(
            reportId,
            parent,
            displayName?.takeIf { it.isNotBlank() } ?: title,
            description,
            document,
            files,
            stamp,
            identity =
              com.karandaev.fieldfolio.core.names.ResourceIdentity(
                logicalName ?: title,
                displayName,
              ),
          )
        dirty = false
        savedId = id
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        error = e
      } finally {
        busy = false
      }
    }
}

internal fun DocumentViewModel.performDiscardForLock() {
  notebookWindows.clear()
  runtimeContext = null
  runtimeLocation = null
  savedId = null
  job?.cancel()
  clearFiles()
  document = ReportDocument()
  title = ""
  logicalName = null
  displayName = null
  description = ""
  clipboard = null
  dirty = false
  loading = true
}

internal fun DocumentViewModel.action(block: () -> Unit) {
  error = null
  try {
    com.karandaev.fieldfolio.core.names.NameResolutionEvents.capture { block() }
  } catch (e: Throwable) {
    error = e
  }
}
