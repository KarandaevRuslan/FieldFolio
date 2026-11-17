package com.karandaev.fieldfolio.core.library

import android.content.Context
import com.karandaev.fieldfolio.core.data.local.report.relation.InspectionReportWithResults
import com.karandaev.fieldfolio.core.document.resourcePaths
import com.karandaev.fieldfolio.core.image.report.storeandresolve.InspectionReportImageStorage
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.File

@JsonClass(generateAdapter = true)
internal data class ReportSnapshot(
  val format: String = "geoinspect-report",
  val version: Int = 1,
  val report: InspectionReportWithResults,
  val detectionImage: String? = null,
  val missingResources: List<String> = emptyList(),
  val document: com.karandaev.fieldfolio.core.document.ReportDocument? = null,
)

internal data class ReportPacket(
  val snapshot: ReportSnapshot,
  val files: Map<String, ByteArray> = emptyMap(),
)

internal class ReportPackets(private val context: Context, moshi: Moshi) {
  private val adapter = moshi.adapter(ReportSnapshot::class.java)

  fun encode(packet: ReportPacket): ByteArray {
    validate(packet)
    val json = adapter.toJson(packet.snapshot).toByteArray()
    require(json.size <= 8 * 1024 * 1024) { "Report metadata exceeds size limit" }
    return LibraryZip.pack(mapOf("report.json" to json) + packet.files).also {
      require(it.size <= LibraryZip.MAX_OBJECT)
    }
  }

  fun decode(bytes: ByteArray): ReportPacket {
    val files = LibraryZip.unpack(bytes)
    val json = requireNotNull(files["report.json"]) { "Missing report.json" }
    require(json.size <= 8 * 1024 * 1024)
    return ReportPacket(
        requireNotNull(adapter.fromJson(json.toString(Charsets.UTF_8))),
        files - "report.json",
      )
      .also(::validate)
  }

  private fun validate(packet: ReportPacket) {
    val s = packet.snapshot
    val report = s.report.report
    require(s.format == "geoinspect-report" && s.version in 1..2)
    require(report.title.length <= 1_000_000 && report.content.length <= 4_000_000)
    require(
      report.latitude.isFinite() &&
        report.latitude in -90.0..90.0 &&
        report.longitude.isFinite() &&
        report.longitude in -180.0..180.0
    )
    require(
      s.report.results.size <= 1000 &&
        s.report.results.sumOf { it.detections.size + it.segmentations.size } <= 100_000
    )
    require(
      packet.files.size <= 500 &&
        packet.files.values.sumOf { it.size.toLong() } <= 48L * 1024 * 1024
    )
    (packet.files.keys + s.missingResources).forEach {
      LibraryZip.path(it)
      require(it.startsWith("resources/"))
    }
    fun reference(path: String?) {
      if (path != null)
        require(path in packet.files || path in s.missingResources) { "Missing report resource" }
    }
    s.document?.let { doc ->
      com.karandaev.fieldfolio.core.document.DocumentTree(doc)
      doc.resourcePaths().forEach(::reference)
    }
    reference(s.detectionImage)
    s.report.results.forEach { result ->
      result.detections.forEach {
        require(
          it.confidence.isFinite() &&
            listOf(it.xMin, it.yMin, it.xMax, it.yMax).all(Double::isFinite)
        )
      }
      result.segmentations.forEach {
        reference(it.maskPath)
        require(it.confidence == null || it.confidence.isFinite())
      }
    }
  }

  fun legacy(
    report: InspectionReportWithResults,
    includeDetectionImage: Boolean = true,
  ): ReportPacket {
    val files = linkedMapOf<String, ByteArray>()
    val missing = mutableListOf<String>()
    fun capture(file: File?, alias: String): String? {
      if (file == null) return null
      if (file.isFile)
        files[alias] = file.inputStream().use { LibraryZip.read(it, LibraryZip.MAX_FILE) }
      else missing += alias
      return alias
    }
    val image =
      if (includeDetectionImage)
        InspectionReportImageStorage.findLegacyDetectionImageFile(context, report.report.id)
      else null
    val imageName = capture(image, "resources/detection.${extension(image?.name)}")
    val results =
      report.results.mapIndexed { ri, result ->
        result.copy(
          segmentations =
            result.segmentations.mapIndexed { si, segment ->
              val original = segment.maskPath
              if (original == null) segment
              else {
                val raw = File(original)
                val file = if (raw.isAbsolute) raw else File(context.filesDir, original)
                val allowed =
                  file.canonicalFile.toPath().let { p ->
                    p.startsWith(context.filesDir.canonicalFile.toPath()) ||
                      p.startsWith(context.cacheDir.canonicalFile.toPath())
                  }
                val alias = "resources/masks/$ri-$si.${extension(file.name)}"
                if (allowed) capture(file, alias) else missing.add(alias)
                segment.copy(maskPath = alias)
              }
            }
        )
      }
    return ReportPacket(
      ReportSnapshot(
        report = report.copy(results = results),
        detectionImage = imageName,
        missingResources = missing,
      ),
      files,
    )
  }

  fun withImage(packet: ReportPacket, path: String?, remove: Boolean): ReportPacket {
    if (!remove && path.isNullOrBlank()) return packet
    val previous = packet.snapshot.detectionImage
    val remaining = packet.files - listOfNotNull(previous).toSet()
    if (remove)
      return packet.copy(
        snapshot =
          packet.snapshot.copy(
            detectionImage = null,
            missingResources = packet.snapshot.missingResources - listOfNotNull(previous).toSet(),
          ),
        files = remaining,
      )
    if (LibraryImages.isModel(path)) {
      require(packet.snapshot.detectionImage?.let { it in packet.files } == true) {
        "Stored image no longer exists"
      }
      return packet
    }
    val source = File(requireNotNull(path))
    require(source.isFile) { "Image no longer exists" }
    val name = "resources/detection.${extension(source.name)}"
    val bytes = source.inputStream().use { LibraryZip.read(it, LibraryZip.MAX_FILE) }
    return packet.copy(
      snapshot =
        packet.snapshot.copy(
          detectionImage = name,
          missingResources = packet.snapshot.missingResources - listOfNotNull(previous).toSet(),
        ),
      files = remaining + (name to bytes),
    )
  }

  private fun extension(name: String?) =
    name?.substringAfterLast('.', "bin")?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
      ?: "bin"
}
