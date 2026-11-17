package com.karandaev.fieldfolio.feature.library

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.ui.clipboard.copyPathToClipboard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class LibraryClipboard(val ids: List<Long>, val cut: Boolean)

internal class LibraryViewModel(val library: ReportLibrary) : ViewModel() {
  val covers = LibraryCoverCache()
  var contextItem by mutableStateOf<Long?>(null)
  var nodes by mutableStateOf<List<LibraryNode>>(emptyList())
    private set

  var folder by mutableStateOf<Long?>(null)
    private set

  var loading by mutableStateOf(true)
    private set

  var busy by mutableStateOf(false)
    private set

  var error by mutableStateOf<Exception?>(null)
  var selected by mutableStateOf<Set<Long>>(emptySet())
  var query by mutableStateOf("")
  var sortByDate by mutableStateOf(false)
  var clipboard by mutableStateOf<LibraryClipboard?>(null)
  var unlock by mutableStateOf<Long?>(null)
  var password by mutableStateOf<Long?>(null)
  var removePassword by mutableStateOf<Long?>(null)
  var rename by mutableStateOf<Long?>(null)
  var createFolder by mutableStateOf(false)
  var deleting by mutableStateOf<List<Long>?>(null)
  var moving by mutableStateOf<LibraryClipboard?>(null)
  var confirmUnprotect by mutableStateOf<Pair<LibraryClipboard, Long?>?>(null)
  var goPath by mutableStateOf(false)
  var pendingPath: String? = null
  var exportIds by mutableStateOf<List<Long>?>(null)
  private var collectionJob: kotlinx.coroutines.Job? = null
  private var lockGeneration = library.generation.get()
  private val previous = mutableStateListOf<Long?>()
  private val next = mutableStateListOf<Long?>()
  var session by mutableLongStateOf(0)
    private set

  val canBack
    get() = previous.isNotEmpty()

  val canForward
    get() = next.isNotEmpty()

  private var cachedNodes: List<LibraryNode>? = null
  private var cachedTree = LibraryTree(emptyList())
  val tree: LibraryTree
    get() {
      val current = nodes
      if (cachedNodes !== current) {
        cachedTree = LibraryTree(current)
        cachedNodes = current
      }
      return cachedTree
    }

  val entries
    get() =
      nodes
        .filter {
          it.parentId == folder &&
            (it.name.contains(query, true) || it.displayLabel.contains(query, true))
        }
        .sortedWith(
          compareByDescending<LibraryNode> { it.folder }
            .let {
              if (sortByDate) it.thenByDescending { n -> n.updatedAt }
              else it.thenBy { n -> n.displayLabel.lowercase() }
            }
        )

  val path
    get() = folder?.let { tree.ancestors(it) + tree.node(it) }.orEmpty()

  init {
    reload()
  }

  fun locked(id: Long): Boolean {
    session
    if (id !in tree.byId) return true
    return tree.locks(id).any { !library.keys.containsKey(it.id) }
  }

  fun copyPath(id: Long) {
    copyPathToClipboard(
      library.context,
      "/" + (tree.ancestors(id) + tree.node(id)).joinToString("/") { it.name },
    )
  }

  fun refresh() {
    if (!busy) reload()
  }

  fun reload() {
    collectionJob?.cancel()
    loading = true
    collectionJob =
      viewModelScope.launch {
        try {
          combine(library.observeNodes(), library.session) { nodes, epoch -> nodes to epoch }
            .collect { (value, epoch) ->
              val changed = lockGeneration != library.generation.get()
              lockGeneration = library.generation.get()
              nodes = value
              session = epoch
              loading = false
              if (folder != null && (folder !in tree.byId || !library.isUnlocked(folder!!)))
                navigate(null, false)
              selected = selected.intersect(nodes.map { it.id }.toSet())
              if (changed) {
                covers.clear()
                clipboard = null
                selected = emptySet()
                query = ""
              }
            }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          error = e
          loading = false
        }
      }
  }

  fun perform(action: suspend () -> Unit, success: () -> Unit = {}) {
    if (busy) return
    busy = true
    viewModelScope.launch {
      try {
        action()
        success()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        error = e
      } finally {
        busy = false
      }
    }
  }

  fun open(node: LibraryNode, onReport: (Long) -> Unit) =
    perform({
      if (!library.isUnlocked(node.id)) unlock = node.id
      else if (node.folder) navigate(node.id)
      else {
        library.getInspectionReportById(node.id)
        onReport(node.id)
      }
    })

  fun navigate(id: Long?, history: Boolean = true) {
    if (id == folder) return
    if (history) {
      previous += folder
      if (previous.size > 100) previous.removeAt(0)
      next.clear()
    }
    folder = id
    library.currentFolder.value = id
    selected = emptySet()
    query = ""
  }

  fun back() {
    if (canBack) {
      next += folder
      val id = previous.removeAt(previous.lastIndex)
      visitHistory(id)
    }
  }

  fun forward() {
    if (canForward) {
      previous += folder
      val id = next.removeAt(next.lastIndex)
      visitHistory(id)
    }
  }

  private fun visitHistory(id: Long?) =
    perform({
      if (id != null && (id !in tree.byId || !library.isUnlocked(id))) {
        navigate(null, false)
        error = LibraryLockedException()
      } else navigate(id, false)
    })

  fun up() = navigate(folder?.let { tree.node(it).parentId })

  fun select(id: Long) {
    selected = if (id in selected) selected - id else selected + id
  }

  fun copy(ids: List<Long>, cut: Boolean) {
    clipboard = LibraryClipboard(tree.roots(ids), cut)
    selected = emptySet()
  }

  fun paste(
    source: LibraryClipboard? = clipboard,
    target: Long? = folder,
    confirmed: Boolean = false,
  ) {
    val request = source ?: return
    perform({
      try {
        library.transfer(request.ids, target, request.cut, confirmed)
      } catch (_: LibraryUnprotectRequired) {
        confirmUnprotect = request to target
        return@perform
      }
      if (request.cut && clipboard == request) clipboard = null
      selected = emptySet()
      moving = null
      confirmUnprotect = null
    })
  }

  fun visitPath(value: String, onReport: (Long) -> Unit) =
    perform({
      val parts = value.trim().trim('/').split('/').filter { it.isNotEmpty() }
      var parent: Long? = if (value.startsWith('/')) null else folder
      for ((i, encoded) in parts.withIndex()) {
        if (encoded == ".") continue
        if (encoded == "..") {
          parent = parent?.let { tree.node(it).parentId }
          continue
        }
        val part = android.net.Uri.decode(encoded)
        LibraryTree.name(part)
        val node =
          nodes.singleOrNull { it.parentId == parent && it.name == part }
            ?: kotlin.error("Path not found")
        if (!library.isUnlocked(node.id)) {
          pendingPath = value
          unlock = node.id
          return@perform
        }
        if (i < parts.lastIndex) require(node.folder)
        if (node.folder) parent = node.id
        else {
          library.getInspectionReportById(node.id)
          goPath = false
          onReport(node.id)
          return@perform
        }
      }
      navigate(parent)
      goPath = false
    })

  fun resumePendingPath(onReport: (Long) -> Unit) {
    val path = pendingPath ?: return
    pendingPath = null
    viewModelScope.launch {
      kotlinx.coroutines.yield()
      visitPath(path, onReport)
    }
  }

  fun lock() {
    library.lockAll()
    clipboard = null
    selected = emptySet()
    unlock = null
    password = null
    rename = null
    moving = null
    deleting = null
    goPath = false
  }
}
