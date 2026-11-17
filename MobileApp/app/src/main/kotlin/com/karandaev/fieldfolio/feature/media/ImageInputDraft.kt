@file:OptIn(
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
  androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.karandaev.fieldfolio.feature.media

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.*
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.image.crop.*
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.*
import com.karandaev.fieldfolio.feature.document.route.*
import com.karandaev.fieldfolio.feature.document.state.*
import com.karandaev.fieldfolio.feature.document.viewer.*
import com.karandaev.fieldfolio.feature.models.*
import kotlinx.coroutines.*

/** One acquisition/crop/preview toolbar for model input, report images and covers. */
class ImageInputDraft : ViewModel() {
  val frames = ImageFrameState()
  val bytes
    get() = frames.bytes

  var name by mutableStateOf("image.jpg")
  var busy by mutableStateOf(false)
    private set

  var error by mutableStateOf<Throwable?>(null)
  var changed by mutableStateOf(false)
    private set

  private var job: Job? = null
  private var saved: ByteArray? = null
  private var savedName = "image.jpg"
  private var savedRevision = -1L
  private var initialized = false
  var hasSavedImage by mutableStateOf(false)
    private set

  suspend fun initialize(value: ByteArray?, label: String) {
    if (initialized) return
    busy = true
    try {
      val copies = withContext(Dispatchers.Default) { value?.let { it.copyOf() to it.copyOf() } }
      saved = copies?.first
      savedName = label
      hasSavedImage = saved != null
      if (frames.bytes == null) {
        copies?.second?.let(frames::installOwned)
        name = label
        changed = false
        savedRevision = frames.revision
      } else copies?.second?.fill(0)
      initialized = true
    } finally {
      busy = false
    }
  }

  fun restoreSaved(restoreName: Boolean = true) {
    if (busy) return
    val initial = saved ?: return
    busy = true
    job =
      viewModelScope.launch {
        try {
          val copy = withContext(Dispatchers.Default) { initial.copyOf() }
          frames.installOwned(copy)
          savedRevision = frames.revision
          if (restoreName) name = savedName
          changed = false
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          error = e
        } finally {
          busy = false
        }
      }
  }

  fun set(value: ByteArray, label: String) {
    job?.cancel()
    frames.installOwned(value)
    name = label
    changed = true
    busy = false
  }

  private fun edit(bounds: NormalizedCropBox, base: Boolean = false) {
    if (busy || frames.source == null) return
    busy = true
    job =
      viewModelScope.launch {
        try {
          val frame = frames.prepare(bounds)
          frames.commit(frame, if (base) frame.bounds else null)
          changed = frames.revision != savedRevision || frames.bounds != FullImageBounds
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          error = e
        } finally {
          busy = false
        }
      }
  }

  fun crop(box: NormalizedCropBox) = edit(box.inside(frames.base))

  fun reset() = edit(frames.base)

  fun restore() = edit(FullImageBounds, true)

  fun trim() {
    if (busy || frames.source == null) return
    busy = true
    job =
      viewModelScope.launch {
        try {
          val box = uniformBorderBounds(frames.source!!) ?: throw UniformBorderNotFound()
          val frame = frames.prepare(box)
          frames.commit(frame, frame.bounds)
          changed = frames.revision != savedRevision || frames.bounds != FullImageBounds
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          error = e
        } finally {
          busy = false
        }
      }
  }

  fun clear() {
    job?.cancel()
    frames.clear()
    saved?.fill(0)
    saved = null
    hasSavedImage = false
    initialized = false
    savedRevision = -1L
    name = "image.jpg"
    busy = false
    changed = false
    error = null
  }

  override fun onCleared() {
    clear()
  }
}
