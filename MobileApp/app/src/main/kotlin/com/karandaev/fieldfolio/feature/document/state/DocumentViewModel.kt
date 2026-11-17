package com.karandaev.fieldfolio.feature.document.state

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import java.util.UUID
import kotlinx.coroutines.*

/**
 * Owns one report editing session and its private in-memory draft.
 *
 * Public methods preserve the screen contract; package-internal operations separate tree mutations,
 * attachments, model staging, persistence and exports. Private bytes never enter Android saved
 * state.
 */
class DocumentViewModel(
  val library: ReportLibrary,
  val runner: CatalogModelRunner,
  val reportId: Long,
  val parent: Long?,
  point: MapPoint?,
) : ViewModel() {
  val runtimeScope = java.util.UUID.randomUUID().toString()
  val notebookWindows = com.karandaev.fieldfolio.core.document.runtime.NotebookWindows()
  var runtimeLocation: MapPoint? = null
  internal var runtimeContext:
    com.karandaev.fieldfolio.core.document.runtime.DocumentAccessContext? =
    null

  var elementExportReady by mutableStateOf(false)
  var elementExportBusy by mutableStateOf(false)
  var elementExportName = "elements.zip"
  internal var elementExport:
    Pair<com.karandaev.fieldfolio.core.document.runtime.DocumentAccessContext, List<String>>? =
    null
  internal var elementExportJob: Job? = null
  internal var elementExportGeneration = 0L

  internal val thumbnails = DocumentThumbnailCache()
  val viewerImages = ViewerImageWindows()
  var title by mutableStateOf("")
  var logicalName by mutableStateOf<String?>(null)
  var displayName by mutableStateOf<String?>(null)
  var description by mutableStateOf("")
  var document by mutableStateOf(ReportDocument())
    internal set

  var files by mutableStateOf<Map<String, ByteArray>>(emptyMap())
    internal set

  var loading by mutableStateOf(true)
    internal set

  var busy by mutableStateOf(false)
    internal set

  var dirty by mutableStateOf(false)
    internal set

  var error by mutableStateOf<Throwable?>(null)
  var folder by mutableStateOf<String?>(null)
  var reanalysisImage: ByteArray? = null
  var reanalysisText: String = ""
  var clipboard by mutableStateOf<Pair<List<String>, Boolean>?>(null)
  var stamp: Long? = null
    internal set

  var modelDraft by mutableStateOf<DocumentNode?>(null)
    internal set

  var modelFiles by mutableStateOf<Map<String, ByteArray>>(emptyMap())
    internal set

  var viewerPresentation: DocumentPresentation? = null

  internal var job: Job? = null
  val tree
    get() = DocumentTree(document)

  init {
    if (reportId == 0L) {
      loading = false
      if (point != null)
        document =
          ReportDocument(
            nodes =
              listOf(
                DocumentNode(
                  name = "Location",
                  kind = BlockKind.LOCATION,
                  latitude = point.latitude,
                  longitude = point.longitude,
                )
              )
          )
    } else refresh()
  }

  internal var history by mutableStateOf(listOf<String?>(null))
  internal var historyIndex by mutableIntStateOf(0)
  val canGoBack
    get() = historyIndex > 0

  val canGoForward
    get() = historyIndex < history.lastIndex

  fun changed() {
    dirty = true
  }

  fun setCover(path: String?) {
    document = document.copy(cover = path)
    dirty = true
  }

  var savedId by mutableStateOf<Long?>(null)
    internal set

  fun consumeSaved() {
    savedId = null
  }

  internal fun clearFiles() {
    clearElementExport()
    viewerImages.clear()
    viewerPresentation = null
    discardModelDraft()
    thumbnails.clear()
    reanalysisImage = null
    reanalysisText = ""
    files.values.forEach { it.fill(0) }
    files = emptyMap()
  }

  override fun onCleared() {
    clearFiles()
  }

  /** Returns report-scoped, read-only runtime capabilities. */
  fun documentAccess(weather: com.karandaev.fieldfolio.core.weather.WeatherProvider) =
    performDocumentAccess(weather)

  /** Cancels pending export work and releases its report-scoped capabilities. */
  fun clearElementExport() = performClearElementExport()

  /** Snapshots selected subtrees before asking the platform for a ZIP destination. */
  fun prepareElementExport(
    ids: List<String>,
    weather: com.karandaev.fieldfolio.core.weather.WeatherProvider,
  ) = performPrepareElementExport(ids, weather)

  /** Writes the prepared unencrypted ZIP; a cancelled picker only clears pending state. */
  fun writeElementExport(context: android.content.Context, uri: android.net.Uri?) =
    performWriteElementExport(context, uri)

  /** Keeps element identity/presentation separate from the reusable analysis workspace. */
  fun stageModelProperties(node: DocumentNode) = performStageModelProperties(node)

  /** Zeroes staged private result bytes without modifying the saved report. */
  fun discardModelDraft() = performDiscardModelDraft()

  /** Copies result resources off the UI thread and enforces the report attachment budget. */
  suspend fun stageAnalysisResult(execution: ModelExecution, image: ByteArray?) =
    performStageAnalysisResult(execution, image)

  /** Validates the staged node and commits only resources referenced by the resulting tree. */
  fun commitModelDraft(node: DocumentNode) = performCommitModelDraft(node)

  /** Reloads a clean idle report; dirty or busy sessions are never overwritten. */
  fun refresh() = performRefresh()

  /** Enters a folder and records history only when the destination actually changes. */
  fun navigate(target: String?) = performNavigate(target)

  /** Restores the previous valid folder in this session history. */
  fun goBack() = performGoBack()

  /** Restores the next valid folder after a history-back operation. */
  fun goForward() = performGoForward()

  /** Repositions one sibling using the same validated tree transfer as VFS moves. */
  fun moveToIndex(id: String, index: Int) = performMoveToIndex(id, index)

  /** Applies node metadata through central name/structure validation and marks the draft dirty. */
  fun update(node: DocumentNode) = performUpdate(node)

  /** Inserts a typed node with defaults; returns its conflict-resolved logical identity. */
  fun add(kind: BlockKind, name: String) = performAdd(kind, name)

  /** Deletes a subtree from the draft; final resource retention follows the document snapshot. */
  fun remove(id: String) = performRemove(id)

  /** Moves a sibling by a bounded relative offset without changing its identity. */
  fun reorder(id: String, delta: Int) = performReorder(id, delta)

  /** Copies or moves complete subtrees through the central ordered-tree invariant boundary. */
  fun transfer(ids: List<String>, target: String?, copy: Boolean) =
    performTransfer(ids, target, copy)

  /** Pastes into the current folder; a successful cut consumes the clipboard. */
  fun paste() = performPaste()

  /** Takes ownership of imported bytes under a private resource path within the 48 MB budget. */
  fun addFile(
    bytes: ByteArray,
    name: String,
    mime: String,
    kind: BlockKind,
    asCover: Boolean = false,
    identity: com.karandaev.fieldfolio.core.names.ResourceIdentity? = null,
  ) = performAddFile(bytes, name, mime, kind, asCover, identity)

  /** Stores edited text as a new owned resource and retains only referenced attachments. */
  fun updateTextResource(node: DocumentNode, bytes: ByteArray) =
    performUpdateTextResource(node, bytes)

  /** Encodes a text edit off the UI thread without writing back to its original device URI. */
  fun editTextFile(node: DocumentNode, text: String) = performEditTextFile(node, text)

  /** Commits replacement image bytes and identity together after size/name validation. */
  fun updateImage(
    node: DocumentNode,
    bytes: ByteArray,
    identity: com.karandaev.fieldfolio.core.names.ResourceIdentity,
  ) = performUpdateImage(node, bytes, identity)

  /** Applies audio presentation and optional owned cover bytes to the draft. */
  fun updateAudio(node: DocumentNode, cover: ByteArray?) = performUpdateAudio(node, cover)

  /** Restores the pre-dialog report properties, cover reference and previous dirty flag. */
  fun restoreProperties(
    previousTitle: String,
    previousDescription: String,
    previousCover: String?,
    wasDirty: Boolean,
  ) = performRestoreProperties(previousTitle, previousDescription, previousCover, wasDirty)

  /**
   * Validates report identity, persists the owned snapshot and publishes its saved ID on success.
   */
  fun save() = performSave()

  /** Cancels report work, revokes runtime access and clears private in-memory content. */
  fun discardForLock() = performDiscardForLock()
}
