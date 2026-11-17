@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.karandaev.fieldfolio.core.library

import android.content.Context
import androidx.room.withTransaction
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.data.local.report.mapper.*
import com.karandaev.fieldfolio.core.data.local.report.seed.DefaultInspectionReportsSeed
import com.karandaev.fieldfolio.core.document.locations
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType
import com.karandaev.fieldfolio.core.domain.repository.InspectionReportRepository
import com.karandaev.fieldfolio.core.image.report.storeandresolve.InspectionReportImageStorage
import com.squareup.moshi.Moshi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Canonical tree and immutable report packets. Database v7 is migrated once, with original IDs. */
class ReportLibrary(
  internal val context: Context,
  internal val db: AppDatabase,
  internal val moshi: Moshi,
) : InspectionReportRepository {
  internal val dao = db.libraryDao()
  internal val objects = LibraryObjects(context)
  internal val packets = ReportPackets(context, moshi)
  private val mutex = Mutex()
  internal val keys = ConcurrentHashMap<Long, ByteArray>()

  private data class CachedReport(val ref: String?, val report: InspectionReport)

  private val reportsCache = ConcurrentHashMap<Long, CachedReport>()
  internal val generation = AtomicLong(0)
  internal val sessionMonitor = Any()
  private val _session = MutableStateFlow(0L)
  val session: StateFlow<Long> = _session.asStateFlow()
  private var initialized = false
  @Volatile var externalPicker = false
  val currentFolder = MutableStateFlow<Long?>(null)

  internal suspend fun <T> access(block: suspend () -> T): T =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        com.karandaev.fieldfolio.core.names.NameResolutionEvents.transaction {
          initialize()
          block()
        }
      }
    }

  private suspend fun initialize() {
    if (initialized) return
    if (dao.state("initialized") == null) {
      val original = db.inspectionReportDao().getAllForLibraryMigration()
      val reports =
        if (original.isEmpty() && dao.state("seed") == "true") DefaultInspectionReportsSeed.create()
        else original
      val staged = mutableListOf<String>()
      try {
        db.withTransaction {
          var next = (reports.maxOfOrNull { it.report.id } ?: 0L).coerceAtLeast(0) + 1
          val names = mutableSetOf<String>()
          reports.forEach { report ->
            val id = report.report.id.takeIf { it > 0 } ?: next++
            val base = safeName(report.report.title)
            var name = base
            var suffix = 2
            while (!names.add(name)) name = numbered(base, suffix++)
            val packet =
              packets.legacy(
                report.copy(report = report.report.copy(id = id)),
                includeDetectionImage = original.isNotEmpty(),
              )
            val ref = objects.write(packets.encode(packet))
            staged += ref
            dao.insert(
              LibraryNode(
                id = id,
                name = name,
                folder = false,
                payloadRef = ref,
                updatedAt = report.report.lastUpdatedAtMillis,
              )
            )
          }
          db.inspectionReportDao().clearMigratedReports()
          dao.state(LibraryState("nextId", next.toString()))
          dao.state(LibraryState("initialized", "true"))
        }
      } catch (e: Exception) {
        staged.forEach(objects::delete)
        throw e
      }
      original.forEach { report ->
        InspectionReportImageStorage.deleteDetectionImageFiles(context, report.report.id)
      }
      val owned =
        listOf(
          java.io.File(context.filesDir, "reports").canonicalFile.toPath(),
          java.io.File(context.filesDir, "masks").canonicalFile.toPath(),
        )
      original
        .flatMap { it.results }
        .flatMap { it.segmentations }
        .mapNotNull { it.maskPath }
        .forEach { path ->
          val raw = java.io.File(path)
          val file = if (raw.isAbsolute) raw else java.io.File(context.filesDir, path)
          if (owned.any { file.canonicalFile.toPath().startsWith(it) }) file.delete()
        }
      runCatching {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use {
          it.moveToFirst()
        }
      }
    }
    objects.cleanup(dao.all().mapNotNull { it.payloadRef }.toSet())
    LibraryImages.managed = true
    // Upgrade previously valid case/Unicode collisions without losing IDs, locks or payloads.
    val names = mutableMapOf<Long?, MutableList<String>>()
    db.withTransaction {
      dao.all().forEach { node ->
        val used = names.getOrPut(node.parentId) { mutableListOf() }
        val unique =
          com.karandaev.fieldfolio.core.names.ItemNames.unique(node.name, used, notify = true)
        used += unique
        if (unique != node.name) dao.update(node.copy(name = unique))
      }
    }
    initialized = true
  }

  internal inner class Revision(val nodes: MutableList<LibraryNode>, var next: Long) {
    val staged = mutableListOf<String>()

    fun id() = next++

    fun tree() = LibraryTree(nodes)

    fun set(node: LibraryNode) {
      nodes.removeAll { it.id == node.id }
      nodes.add(node)
    }

    fun payload(node: LibraryNode, bytes: ByteArray): LibraryNode {
      val ref = objects.write(bytes)
      staged += ref
      return node.copy(payloadRef = ref).also(::set)
    }
  }

  internal suspend fun <T> mutate(
    expectedGeneration: Long? = null,
    block: suspend (Revision) -> T,
  ): T {
    val before = dao.all()
    val tx =
      Revision(
        before.toMutableList(),
        maxOf(dao.state("nextId")?.toLongOrNull() ?: 1, (before.maxOfOrNull { it.id } ?: 0) + 1),
      )
    val epoch = generation.get()
    if (expectedGeneration != null && expectedGeneration != epoch) throw LibraryLockedException()
    val result: T
    try {
      result =
        db.withTransaction {
          val value = block(tx)
          check(epoch == generation.get()) { "Session locked during operation" }
          LibraryTree(tx.nodes)
          val old = before.associateBy { it.id }
          val remaining = tx.nodes.map { it.id }.toSet()
          dao.delete(before.filter { it.id !in remaining }.map { it.id })
          tx.nodes.forEach { node ->
            if (node.id !in old) dao.insert(node) else if (node !== old[node.id]) dao.update(node)
          }
          dao.state(LibraryState("nextId", tx.next.toString()))
          value
        }
    } catch (e: Exception) {
      tx.staged.forEach(objects::delete)
      throw e
    }
    val used = tx.nodes.mapNotNull { it.payloadRef }.toSet()
    before.mapNotNull { it.payloadRef }.filter { it !in used }.forEach(objects::delete)
    val after = tx.nodes.associateBy { it.id }
    before
      .filter { it.payloadRef != after[it.id]?.payloadRef }
      .forEach {
        LibraryImages.remove(it.id)
        reportsCache.remove(it.id)
      }
    return result
  }

  fun lockAll() =
    synchronized(sessionMonitor) {
      generation.incrementAndGet()
      keys.values.forEach { it.fill(0) }
      keys.clear()
      LibraryImages.clear()
      reportsCache.clear()
      currentFolder.value = null
      _session.value = generation.get()
    }

  internal fun unlocked(tree: LibraryTree, id: Long) =
    tree.locks(id).all { keys.containsKey(it.id) }

  internal fun requireUnlocked(tree: LibraryTree, id: Long) {
    if (!unlocked(tree, id)) throw LibraryLockedException()
  }

  internal fun decrypt(tree: LibraryTree, node: LibraryNode): ByteArray {
    requireUnlocked(tree, node.id)
    var bytes = objects.read(requireNotNull(node.payloadRef))
    tree.locks(node.id).forEach { n ->
      bytes =
        LibraryCrypto.open(
          bytes,
          keys[n.id] ?: throw LibraryLockedException(),
          "payload:${n.lockId}",
        )
    }
    return bytes
  }

  internal fun encrypt(tree: LibraryTree, node: LibraryNode, plain: ByteArray): ByteArray {
    var bytes = plain
    tree.locks(node.id).asReversed().forEach { n ->
      bytes =
        LibraryCrypto.seal(
          bytes,
          keys[n.id] ?: throw LibraryLockedException(),
          "payload:${n.lockId}",
        )
    }
    return bytes
  }

  internal fun readPacket(tree: LibraryTree, id: Long): ReportPacket {
    val node = tree.node(id)
    require(!node.folder)
    val packet = packets.decode(decrypt(tree, node))
    return packet.copy(
      snapshot =
        packet.snapshot.copy(
          report =
            packet.snapshot.report.copy(
              report = packet.snapshot.report.report.copy(id = id),
              results =
                packet.snapshot.report.results.map {
                  it.copy(result = it.result.copy(inspectionReportId = id))
                },
            )
        )
    )
  }

  private fun domain(
    tree: LibraryTree,
    node: LibraryNode,
    publishImage: Boolean = false,
  ): InspectionReport {
    if (!publishImage)
      reportsCache[node.id]
        ?.takeIf { it.ref == node.payloadRef }
        ?.let {
          return it.report
        }
    val epoch = generation.get()
    val packet = readPacket(tree, node.id)
    return synchronized(sessionMonitor) {
      if (epoch != generation.get()) throw LibraryLockedException()
      if (publishImage) {
        val cover =
          if (packet.snapshot.document != null) packet.snapshot.document.cover
          else packet.snapshot.detectionImage
        val image = cover?.let { packet.files[it] }
        LibraryImages.put(node.id, image, cover)
      }
      val report =
        packet.snapshot.report
          .toDomain()
          .copy(id = node.id, documentLocations = packet.snapshot.document?.locations())
      reportsCache[node.id] = CachedReport(node.payloadRef, report)
      report
    }
  }

  fun observeNodes(): Flow<List<LibraryNode>> = flow {
    access { Unit }
    emitAll(dao.observe())
  }

  override fun observeInspectionReports(): Flow<List<InspectionReport>> = flow {
    access { Unit }
    emitAll(
      combine(dao.observe(), session) { _, _ -> Unit }
        .mapLatest {
          access {
            val tree = LibraryTree(dao.all())
            tree.nodes
              .filter { !it.folder && unlocked(tree, it.id) }
              .mapNotNull { runCatching { domain(tree, it) }.getOrNull() }
              .sortedByDescending { it.lastUpdatedAtMillis }
          }
        }
    )
  }

  override fun observeInspectionReportById(id: Long): Flow<InspectionReport?> =
    observeInspectionReports().map { reports -> reports.firstOrNull { it.id == id } }

  override suspend fun getInspectionReportById(id: Long): InspectionReport? = access {
    val tree = LibraryTree(dao.all())
    tree.byId[id]?.takeIf { !it.folder }?.let { domain(tree, it, true) }
  }

  suspend fun isProtected(id: Long): Boolean = access {
    LibraryTree(dao.all()).locks(id).isNotEmpty()
  }

  suspend fun isUnlocked(id: Long): Boolean = access {
    val tree = LibraryTree(dao.all())
    unlocked(tree, id)
  }

  override suspend fun insert(inspectionReport: InspectionReport): Long =
    saveWithImage(inspectionReport.copy(id = 0), null, null, false)

  override suspend fun insertAll(inspectionReports: List<InspectionReport>): List<Long> = access {
    inspectionReports.forEach {
      com.karandaev.fieldfolio.core.names.ItemNames.requireName(it.title)
    }
    mutate { tx ->
      inspectionReports.map { report ->
        createReport(
          tx,
          ReportPacket(ReportSnapshot(report = report.copy(id = 0).toEntityWithResults())),
          null,
        )
      }
    }
  }

  override suspend fun update(inspectionReport: InspectionReport) {
    saveWithImage(inspectionReport, null, null, false)
  }

  override suspend fun saveWithImage(
    report: InspectionReport,
    parentId: Long?,
    imagePath: String?,
    removeImage: Boolean,
  ): Long = access {
    com.karandaev.fieldfolio.core.names.ItemNames.requireName(report.title)
    mutate { tx ->
      if (report.id == 0L) {
        createReport(
          tx,
          packets.withImage(
            ReportPacket(ReportSnapshot(report = report.toEntityWithResults())),
            imagePath,
            removeImage,
          ),
          parentId,
        )
      } else {
        val tree = tx.tree()
        val node = tree.node(report.id)
        val old = readPacket(tree, node.id)
        val nextName = node.name
        val updated = updatePacket(old, report)
        val packet = packets.withImage(updated, imagePath, removeImage)
        val renamed = node.copy(name = nextName, updatedAt = System.currentTimeMillis())
        tx.payload(renamed, encrypt(tree, node, packets.encode(packet)))
        node.id
      }
    }
  }

  internal fun createReport(
    tx: Revision,
    packet: ReportPacket,
    parent: Long?,
    requestedName: String? = null,
  ): Long {
    val tree = tx.tree()
    tree.requireDestination(parent)
    if (parent != null) requireUnlocked(tree, parent)
    val base = safeName(requestedName ?: packet.snapshot.report.report.title)
    val name =
      com.karandaev.fieldfolio.core.names.ItemNames.unique(
        base,
        tx.nodes.filter { it.parentId == parent }.map { it.name },
        notify = true,
      )
    val id = tx.id()
    val node =
      LibraryNode(
        id = id,
        parentId = parent,
        name = name,
        folder = false,
        updatedAt = packet.snapshot.report.report.lastUpdatedAtMillis,
      )
    tx.set(node)
    tx.payload(node, encrypt(tx.tree(), node, packets.encode(packet)))
    return id
  }

  internal fun updatePacket(old: ReportPacket, report: InspectionReport): ReportPacket {
    val first =
      old.snapshot.report.results
        .filter { it.result.type == InspectionResultType.DETECTION }
        .minWithOrNull(compareBy({ it.result.sortOrder }, { it.result.id }))
    val nextDetection = report.toEntityWithResults().results.firstOrNull()
    val results =
      old.snapshot.report.results.map {
        if (it === first)
          it.copy(detections = report.detectResponse.toDetectionEntities(it.result.id))
        else it
      } + if (first == null) listOfNotNull(nextDetection) else emptyList()
    return old.copy(
      snapshot =
        old.snapshot.copy(
          report =
            old.snapshot.report.copy(
              report = report.toEntity().copy(lastUpdatedAtMillis = System.currentTimeMillis()),
              results = results,
            )
        )
    )
  }

  override suspend fun deleteById(id: Long) {
    delete(listOf(id))
  }

  suspend fun delete(ids: List<Long>) = access {
    mutate { tx ->
      val removed = tx.tree().subtree(ids).map { it.id }.toSet()
      tx.nodes.removeAll { it.id in removed }
    }
  }

  suspend fun createFolder(name: String, parent: Long?, displayName: String? = null): Long =
    access {
      LibraryTree.name(name)
      val label = com.karandaev.fieldfolio.core.names.ItemNames.display(displayName)
      mutate { tx ->
        val tree = tx.tree()
        tree.requireDestination(parent)
        if (parent != null) requireUnlocked(tree, parent)
        val unique =
          com.karandaev.fieldfolio.core.names.ItemNames.unique(
            name,
            tree.nodes.filter { it.parentId == parent }.map { it.name },
            notify = true,
          )
        tx.id().also {
          tx.set(
            LibraryNode(
              id = it,
              name = unique,
              folder = true,
              parentId = parent,
              displayName = label,
            )
          )
        }
      }
    }

  suspend fun rename(id: Long, name: String, displayName: String? = null) = access {
    LibraryTree.name(name)
    mutate { tx ->
      val tree = tx.tree()
      val node = tree.node(id)
      if (node.parentId != null) requireUnlocked(tree, node.parentId)
      val unique =
        com.karandaev.fieldfolio.core.names.ItemNames.unique(
          name,
          tree.nodes.filter { it.parentId == node.parentId && it.id != id }.map { it.name },
          notify = true,
        )
      tx.set(
        node.copy(
          name = unique,
          displayName = com.karandaev.fieldfolio.core.names.ItemNames.display(displayName),
          updatedAt = System.currentTimeMillis(),
        )
      )
    }
  }

  override suspend fun resetToDefaults() = access {
    // Reset only the report tree. mutate also removes its stored payloads and cached reports.
    // Keep initialization and nextId so reopening cannot reseed reports or reuse deleted IDs.
    mutate { tx -> tx.nodes.clear() }
    lockAll()
  }

  internal fun notifyKeys() {
    _session.value = _session.value + 1
  }

  internal fun safeName(value: String): String =
    value
      .trim()
      .map { if (it.isISOControl() || it in "/\\:") '_' else it }
      .joinToString("")
      .take(100)
      .ifBlank { "Report" }
      .let { if (it in setOf(".", "..")) "Report" else it }

  internal fun numbered(value: String, index: Int) = "${value.take(108)} ($index)"
}
