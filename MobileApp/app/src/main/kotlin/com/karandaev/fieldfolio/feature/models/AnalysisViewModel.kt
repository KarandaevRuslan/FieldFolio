@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.models

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.*
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.image.crop.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.*
import com.karandaev.fieldfolio.feature.document.route.*
import com.karandaev.fieldfolio.feature.document.state.*
import com.karandaev.fieldfolio.feature.document.viewer.*
import kotlinx.coroutines.*

class AnalysisViewModel(private val runner: CatalogModelRunner) : ViewModel() {
  val frames = ImageFrameState()
  val image
    get() = frames.bytes

  var text by mutableStateOf("")
    private set

  var result by mutableStateOf<ModelExecution?>(null)
    private set

  private var canonical: ModelExecution? = null
  var runBounds by mutableStateOf(FullImageBounds)
    private set

  var busy by mutableStateOf(false)
    private set

  var error by mutableStateOf<Throwable?>(null)
  private var job: Job? = null

  fun image(bytes: ByteArray) {
    job?.cancel()
    clearResult()
    frames.installOwned(bytes)
    busy = false
  }

  fun text(value: String) {
    if (value.length > 1_000_000) {
      error = IllegalArgumentException("Text exceeds the model input limit")
      return
    }
    text = value
    clearResult()
  }

  private fun clearResult() {
    // Projected executions can share immutable resource arrays with the canonical result.
    (result?.files.orEmpty().values + canonical?.files.orEmpty().values)
      .distinctBy { System.identityHashCode(it) }
      .forEach { it.fill(0) }
    result = null
    canonical = null
    runBounds = FullImageBounds
  }

  private fun view(bounds: NormalizedCropBox) {
    if (busy || image == null) return
    busy = true
    job =
      viewModelScope.launch {
        try {
          val frame = frames.prepare(bounds)
          val projected = canonical?.let { projectExecution(it, runBounds, frame) }
          frames.commit(frame)
          result = projected
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          error = e
        } finally {
          busy = false
        }
      }
  }

  fun crop(bounds: NormalizedCropBox) = view(bounds)

  fun restoreRun() = view(if (canonical == null) FullImageBounds else runBounds)

  fun restoreSource() = view(FullImageBounds)

  fun run(choice: ModelChoice) {
    if (busy) return
    busy = true
    val bounds = frames.bounds
    job =
      viewModelScope.launch {
        try {
          val next = runner.run(choice.local, image, text, choice.server?.id ?: "road-server")
          clearResult()
          canonical = next
          runBounds = bounds
          result = next
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          error = e
        } finally {
          busy = false
        }
      }
  }

  class ExportSnapshot(val execution: ModelExecution, val image: ByteArray?) {
    fun release() {
      execution.files.values.forEach { it.fill(0) }
      image?.fill(0)
    }
  }

  private var pendingExport: ExportSnapshot? = null
  private var exportPreparation: Job? = null
  var exportPreparing by mutableStateOf(false)
    private set

  var exportWriting by mutableStateOf(false)
    private set

  var exportReady by mutableStateOf(false)
    private set

  fun prepareExport() {
    val current = result ?: return
    val input = image
    if (busy || exportPreparing || exportWriting) return
    exportPreparing = true
    exportPreparation =
      viewModelScope.launch {
        try {
          val copy =
            withContext(Dispatchers.Default) {
              ExportSnapshot(
                ModelExecution(current.result, current.files.mapValues { it.value.copyOf() }),
                input?.copyOf(),
              )
            }
          pendingExport?.release()
          pendingExport = copy
          exportReady = true
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          error = e
        } finally {
          exportPreparing = false
        }
      }
  }

  fun consumeExportRequest() {
    exportReady = false
  }

  fun takeExportSnapshot(): ExportSnapshot? {
    exportReady = false
    return pendingExport.also { pendingExport = null }
  }

  fun exportResult(
    context: android.content.Context,
    uri: android.net.Uri?,
    moshi: com.squareup.moshi.Moshi,
  ) {
    val snapshot = takeExportSnapshot() ?: return
    if (uri == null) {
      snapshot.release()
      return
    }
    exportWriting = true
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) {
          context.applicationContext.contentResolver.openOutputStream(uri)?.use {
            ModelResultArchive.write(snapshot.execution, snapshot.image, moshi, it)
          } ?: error("Cannot open export destination")
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        error = e
      } finally {
        snapshot.release()
        exportWriting = false
      }
    }
  }

  fun cancel() {
    job?.cancel()
  }

  fun clear() {
    if (exportPreparing) {
      exportPreparation?.invokeOnCompletion { clear() }
      return
    }
    pendingExport?.release()
    pendingExport = null
    exportReady = false
    cancel()
    frames.clear()
    text = ""
    clearResult()
    error = null
  }

  override fun onCleared() {
    clear()
  }
}
