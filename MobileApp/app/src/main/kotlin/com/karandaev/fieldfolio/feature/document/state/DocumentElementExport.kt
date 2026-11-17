package com.karandaev.fieldfolio.feature.document.state

import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import kotlinx.coroutines.*

internal fun DocumentViewModel.performClearElementExport() {
  elementExportGeneration++
  elementExportJob?.cancel()
  elementExportJob = null
  elementExport = null
  elementExportReady = false
  elementExportBusy = false
}

internal fun DocumentViewModel.performPrepareElementExport(
  ids: List<String>,
  weather: com.karandaev.fieldfolio.core.weather.WeatherProvider,
) {
  if (elementExportBusy) return
  clearElementExport()
  val generation = elementExportGeneration
  elementExportBusy = true
  val access = documentAccess(weather)
  elementExportName = ids.singleOrNull()?.let { tree.node(it).name + ".zip" } ?: "elements.zip"
  elementExportJob =
    viewModelScope.launch {
      try {
        withContext(Dispatchers.Default) {
          require(ids.isNotEmpty())
          val snapshotTree = DocumentTree(access.document)
          ids.forEach { snapshotTree.node(it) }
        }
        ensureActive()
        if (generation != elementExportGeneration) return@launch
        elementExport = access to ids.distinct()
        elementExportReady = true
      } catch (cancel: CancellationException) {
        throw cancel
      } catch (failure: Exception) {
        if (generation == elementExportGeneration) error = failure
      } finally {
        if (generation == elementExportGeneration) elementExportBusy = false
      }
    }
}

internal fun DocumentViewModel.performWriteElementExport(
  context: android.content.Context,
  uri: android.net.Uri?,
) {
  elementExportReady = false
  val archive = elementExport ?: return
  if (uri == null) {
    clearElementExport()
    return
  }
  val generation = elementExportGeneration
  elementExportBusy = true
  elementExportJob =
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use {
            DocumentElementArchive.write(archive.first, archive.second, it)
          } ?: error("Cannot open export destination")
        }
      } catch (cancel: CancellationException) {
        withContext(NonCancellable + Dispatchers.IO) {
          runCatching {
            if (android.provider.DocumentsContract.isDocumentUri(context, uri))
              android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
            else context.contentResolver.delete(uri, null, null)
          }
        }
        throw cancel
      } catch (failure: Exception) {
        withContext(NonCancellable + Dispatchers.IO) {
          runCatching {
            if (android.provider.DocumentsContract.isDocumentUri(context, uri))
              android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
            else context.contentResolver.delete(uri, null, null)
          }
        }
        if (generation == elementExportGeneration) error = failure
      } finally {
        if (generation == elementExportGeneration) {
          elementExport = null
          elementExportBusy = false
        }
      }
    }
}
