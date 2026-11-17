package com.karandaev.fieldfolio.core.library

import android.net.Uri
import com.karandaev.fieldfolio.core.data.local.report.mapper.toEntityWithResults
import com.karandaev.fieldfolio.core.json.importjson.reports.parseInspectionReportsImportJson
import com.squareup.moshi.JsonClass
import java.nio.charset.CodingErrorAction
import java.util.Base64

@JsonClass(generateAdapter = true)
internal data class LibraryManifest(
  val format: String = "geoinspect-library",
  val version: Int = 1,
  val nodes: List<ArchiveNode>,
)

@JsonClass(generateAdapter = true)
internal data class ArchiveNode(
  val id: Long,
  val parentId: Long?,
  val name: String,
  val folder: Boolean,
  val objectPath: String?,
  val updatedAt: Long,
  val lockId: String?,
  val salt: String?,
  val verifier: String?,
  val iterations: Int,
  val displayName: String? = null,
)

class LibraryArchivePasswordRequired : IllegalStateException("Archive password required")

suspend fun ReportLibrary.exportArchive(ids: List<Long>, uri: Uri, password: CharArray? = null) {
  val file = prepareArchiveFile(ids, password)
  try {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
        file.inputStream().use { it.copyTo(output) }
      } ?: error("Cannot write archive")
    }
  } finally {
    file.delete()
    password?.fill('\u0000')
  }
}

/** Streams opaque report objects to a private staging file before opening the system picker. */
internal suspend fun ReportLibrary.prepareArchiveFile(
  ids: List<Long>,
  password: CharArray? = null,
): java.io.File = access {
  var result: java.io.File? = null
  try {
    password?.let(LibraryPasswordPolicy::requireNew)
    val tree = LibraryTree(dao.all())
    val roots = tree.roots(ids)
    val members = tree.subtree(roots)
    val inherited =
      roots.flatMap { id ->
        tree.ancestors(id).takeIf { parents -> parents.any { it.lockId != null } }.orEmpty()
      }
    val selected =
      (members + inherited).distinctBy { it.id }.sortedBy { tree.ancestors(it.id).size }
    val selectedIds = selected.map { it.id }.toSet()
    val used = mutableSetOf<Pair<Long?, String>>()
    val manifestNodes =
      selected.map { node ->
        val parent = node.parentId.takeIf { it in selectedIds }
        var name = node.name
        var suffix = 2
        while (!used.add(parent to com.karandaev.fieldfolio.core.names.ItemNames.key(name))) name =
          numbered(node.name, suffix++)
        ArchiveNode(
          node.id,
          parent,
          name,
          node.folder,
          if (node.folder) null else "objects/${node.id}.bin",
          node.updatedAt,
          node.lockId,
          node.salt?.b64(),
          node.verifier?.b64(),
          node.iterations,
          node.displayName,
        )
      }
    val json =
      moshi
        .adapter(LibraryManifest::class.java)
        .toJson(LibraryManifest(nodes = manifestNodes))
        .toByteArray()
    require(json.size <= 8 * 1024 * 1024)
    require(
      selected.sumOf { it.payloadRef?.let(objects::size) ?: 0 } + json.size <=
        LibraryZip.MAX_TOTAL - 1024 * 1024
    )
    val file = java.io.File.createTempFile("geoinspect-export-", ".tmp", context.cacheDir)
    result = file
    file.outputStream().use { output ->
      val stream = if (password == null) output else LibraryCrypto.archiveOutput(output, password)
      java.util.zip.ZipOutputStream(stream).use { zip ->
        zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
        zip.write(json)
        zip.closeEntry()
        selected
          .filter { !it.folder }
          .forEach { node ->
            zip.putNextEntry(java.util.zip.ZipEntry("objects/${node.id}.bin"))
            objects.input(requireNotNull(node.payloadRef)).use { it.copyTo(zip, 8192) }
            zip.closeEntry()
          }
      }
    }
    require(file.length() <= LibraryZip.MAX_TOTAL)
    file
  } catch (e: Exception) {
    result?.delete()
    throw e
  } finally {
    password?.fill('\u0000')
  }
}

internal suspend fun ReportLibrary.exportBytes(
  ids: List<Long>,
  password: CharArray? = null,
): ByteArray {
  val file = prepareArchiveFile(ids, password)
  return try {
    file.readBytes()
  } finally {
    file.delete()
  }
}

suspend fun ReportLibrary.importArchive(
  uri: Uri,
  parent: Long?,
  password: CharArray? = null,
): List<Long> {
  try {
    val bytes =
      kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use {
          LibraryZip.read(it, LibraryZip.MAX_TOTAL)
        } ?: error("Cannot read archive")
      }
    return importBytes(bytes, parent, password)
  } finally {
    password?.fill('\u0000')
  }
}

internal suspend fun ReportLibrary.importBytes(
  input: ByteArray,
  parent: Long?,
  password: CharArray? = null,
): List<Long> = access {
  try {
    val bytes =
      if (LibraryCrypto.protected(input)) {
        if (password == null) throw LibraryArchivePasswordRequired()
        LibraryCrypto.decryptArchive(input, password)
      } else input
    if (bytes.firstOrNull() != 'P'.code.toByte()) {
      require(bytes.size <= 8 * 1024 * 1024)
      val json =
        Charsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .decode(java.nio.ByteBuffer.wrap(bytes))
          .toString()
          .removePrefix("\uFEFF")
      val reports = parseInspectionReportsImportJson(json, moshi)
      return@access mutate { tx ->
        reports.map {
          createReport(tx, ReportPacket(ReportSnapshot(report = it.toEntityWithResults())), parent)
        }
      }
    }
    val files = LibraryZip.unpack(bytes, LibraryZip.MAX_OBJECT)
    val json = requireNotNull(files["manifest.json"])
    require(json.size <= 8 * 1024 * 1024)
    val manifest =
      requireNotNull(
        moshi.adapter(LibraryManifest::class.java).fromJson(json.toString(Charsets.UTF_8))
      )
    require(
      manifest.format == "geoinspect-library" &&
        manifest.version == 1 &&
        manifest.nodes.size in 1..5000
    )
    val seenNames = mutableMapOf<Long?, MutableList<String>>()
    val nodes =
      manifest.nodes.map { n ->
        require(n.iterations in 100_000..1_200_000)
        require(
          (n.lockId == null) == (n.salt == null) && (n.lockId == null) == (n.verifier == null)
        )
        if (n.lockId != null)
          require(
            n.lockId.length in 1..100 &&
              n.salt!!.unb64().size == 16 &&
              n.verifier!!.unb64().size in 28..256
          )
        require(n.folder == (n.objectPath == null))
        n.objectPath?.let {
          LibraryZip.path(it)
          require(it.startsWith("objects/") && files.containsKey(it))
        }
        LibraryNode(
          n.id,
          n.parentId,
          com.karandaev.fieldfolio.core.names.ItemNames.unique(
              n.name,
              seenNames.getOrPut(n.parentId) { mutableListOf() },
              notify = true,
            )
            .also { seenNames.getValue(n.parentId).add(it) },
          n.folder,
          n.objectPath,
          n.updatedAt,
          n.lockId,
          n.salt?.unb64(),
          n.verifier?.unb64(),
          n.iterations,
          com.karandaev.fieldfolio.core.names.ItemNames.display(n.displayName),
        )
      }
    require(nodes.mapNotNull { it.payloadRef }.distinct().size == nodes.count { !it.folder })
    require(files.keys == nodes.mapNotNull { it.payloadRef }.toSet() + "manifest.json")
    val imported = LibraryTree(nodes)
    var expandedSize = 0L
    nodes
      .filter { !it.folder }
      .forEach { n ->
        val data = files.getValue(requireNotNull(n.payloadRef))
        if (imported.locks(n.id).isEmpty()) {
          val decoded = packets.decode(data)
          expandedSize += decoded.files.values.sumOf { it.size.toLong() } + data.size
          require(expandedSize <= LibraryZip.MAX_TOTAL) { "Expanded archive exceeds size limit" }
        } else require(data.size >= 28 * imported.locks(n.id).size)
      }
    mutate { tx ->
      val tree = tx.tree()
      tree.requireDestination(parent)
      if (parent != null) requireUnlocked(tree, parent)
      val incomingRoots = nodes.filter { it.parentId == null }.map { it.id }
      val mapping = nodes.associate { it.id to tx.id() }
      imported.subtree(incomingRoots).forEach { node ->
        val targetParent = node.parentId?.let(mapping::getValue) ?: parent
        var name = node.name
        var suffix = 2
        while (!tx.tree().availableName(targetParent, name)) name = numbered(node.name, suffix++)
        val target =
          node.copy(
            id = mapping.getValue(node.id),
            parentId = targetParent,
            name = name,
            payloadRef = null,
          )
        tx.set(target)
        if (!node.folder) {
          var data = files.getValue(requireNotNull(node.payloadRef))
          if (parent != null)
            tree.locks(parent).asReversed().forEach { layer ->
              data =
                LibraryCrypto.seal(
                  data,
                  keys[layer.id] ?: throw LibraryLockedException(),
                  "payload:${layer.lockId}",
                )
            }
          tx.payload(target, data)
        }
      }
      incomingRoots.map(mapping::getValue)
    }
  } finally {
    password?.fill('\u0000')
  }
}

private fun ByteArray.b64() = Base64.getEncoder().encodeToString(this)

private fun String.unb64(): ByteArray {
  require(length <= 1024)
  return Base64.getDecoder().decode(this)
}

suspend fun ReportLibrary.rootIds(): List<Long> = access {
  dao.all().filter { it.parentId == null }.map { it.id }
}

suspend fun ReportLibrary.importLegacyDirectory(uri: Uri, parent: Long?): List<Long> = access {
  val imported =
    com.karandaev.fieldfolio.core.json.importjson.reports.importInspectionReportsDirectory(
      context,
      uri,
      moshi,
    )
  val prepared =
    imported.items.map { item ->
      val file = item.imageFile
      val name =
        file?.let {
          "resources/detection." +
            (it.name?.substringAfterLast('.', "jpg")?.lowercase()?.takeIf { ext ->
              ext in setOf("jpg", "jpeg", "png", "webp")
            } ?: "jpg")
        }
      val data =
        if (file == null) null
        else
          context.contentResolver.openInputStream(file.uri)?.use {
            LibraryZip.read(it, LibraryZip.MAX_FILE)
          } ?: error("Missing legacy image")
      ReportPacket(
        ReportSnapshot(report = item.inspectionReport.toEntityWithResults(), detectionImage = name),
        if (name != null && data != null) mapOf(name to data) else emptyMap(),
      )
    }
  mutate { tx -> prepared.map { createReport(tx, it, parent) } }
}
