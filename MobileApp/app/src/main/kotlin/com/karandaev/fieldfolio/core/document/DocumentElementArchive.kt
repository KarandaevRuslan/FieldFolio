package com.karandaev.fieldfolio.core.document

import com.karandaev.fieldfolio.core.document.runtime.DocumentAccessContext
import com.karandaev.fieldfolio.core.json.MoshiFactory
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import okio.buffer
import okio.sink
import org.json.JSONArray
import org.json.JSONObject

/**
 * Explicit plaintext export of selected VFS subtrees, with portable assets and widget snapshots.
 */
object DocumentElementArchive {
  suspend fun bytes(access: DocumentAccessContext, ids: List<String>): ByteArray {
    val output = ByteArrayOutputStream()
    write(access, ids, output)
    return output.toByteArray()
  }

  /**
   * Streams a plain ZIP snapshot with metadata and selected subtree resources; does not refresh
   * widgets.
   */
  suspend fun write(
    access: DocumentAccessContext,
    ids: List<String>,
    output: java.io.OutputStream,
  ): Unit =
    withContext(Dispatchers.IO) {
      require(ids.isNotEmpty())
      val tree = DocumentTree(access.document)
      val selected = tree.subtree(ids).map { it.id }.toSet()
      val ancestors = ids.flatMap { tree.ancestors(it) }.map { it.id }.toSet() - selected
      val included = tree.document.nodes.filter { it.id in selected || it.id in ancestors }
      val assets = linkedMapOf<String, ByteArray>()
      val replacements = mutableMapOf<String, String>()
      fun attach(path: String?, mime: String? = null): String? {
        if (path == null) return null
        return replacements.getOrPut(path) {
          val bytes = requireNotNull(access.files[path]) { "Missing resource: $path" }
          val fallback =
            path.substringAfterLast('.', "bin").take(12).takeIf {
              it.matches(Regex("[a-zA-Z0-9]+"))
            } ?: "bin"
          val suffix = extension(bytes, mime, fallback)
          "resources/asset-${assets.size}.$suffix".also { assets[it] = bytes }
        }
      }
      val audioCache = mutableMapOf<String, AudioMetadata>()
      val embeddedArt = mutableMapOf<String, String>()
      val audioInfo = JSONObject()
      val widgets = JSONObject()
      val point = access.device()
      val captured = System.currentTimeMillis()
      val nodes =
        included.map { node ->
          ensureActive()
          if (node.kind == BlockKind.AUDIO && node.id in selected) {
            val meta =
              node.asset?.let { path ->
                audioCache[path]
                  ?: access.files[path]
                    ?.let { readAudioMetadata(it) }
                    ?.also { audioCache[path] = it }
              } ?: AudioMetadata()
            var art = attach(node.audio.coverAsset)
            if (art == null && meta.cover != null) {
              art =
                embeddedArt.getOrPut(node.asset ?: node.id) {
                  "resources/audio-${assets.size}.${extension(meta.cover!!,"image/jpeg","jpg")}"
                    .also { assets[it] = meta.cover!! }
                }
            }
            audioInfo.put(
              node.id,
              JSONObject()
                .put("tags", JSONObject(meta.tags))
                .put("displayedTags", JSONObject(meta.displayed(node.audio)))
                .put("cover", art ?: JSONObject.NULL),
            )
          }
          if (
            node.kind in setOf(BlockKind.LOCATION, BlockKind.WEATHER, BlockKind.CLOCK) &&
              node.id in selected
          ) {
            val location =
              if (node.deviceLocation && node.kind == BlockKind.CLOCK) null
              else if (node.deviceLocation) point
              else
                node.latitude?.let { lat ->
                  node.longitude?.let { lon ->
                    com.karandaev.fieldfolio.core.domain.model.location.MapPoint(lat, lon)
                  }
                }
            val snapshot =
              JSONObject()
                .put("capturedAt", captured)
                .put("deviceLocation", node.deviceLocation)
                .put("latitude", location?.latitude ?: JSONObject.NULL)
                .put("longitude", location?.longitude ?: JSONObject.NULL)
            if (node.kind == BlockKind.CLOCK)
              snapshot.put(
                "zoneId",
                if (node.deviceLocation) java.time.ZoneId.systemDefault().id
                else node.zoneId ?: JSONObject.NULL,
              )
            if (node.kind == BlockKind.WEATHER) {
              val cached =
                location?.let { access.weather?.cachedWeather(it.latitude, it.longitude) }
              snapshot.put(
                "weather",
                cached?.let {
                  JSONObject()
                    .put("temperature", it.temperature)
                    .put("weatherCode", it.weatherCode)
                    .put("zoneId", it.zoneId ?: JSONObject.NULL)
                } ?: JSONObject.NULL,
              )
            }
            widgets.put(node.id, snapshot)
          }
          node.copy(
            asset = attach(node.asset, node.mime),
            audio = node.audio.copy(coverAsset = attach(node.audio.coverAsset)),
            result =
              node.result?.let { result ->
                result.copy(
                  inputAsset = attach(result.inputAsset, "image/jpeg"),
                  maskAsset = attach(result.maskAsset, "image/png"),
                  layers =
                    result.layers.map { it.copy(maskAsset = attach(it.maskAsset, "image/png")) },
                )
              },
          )
        }
      require(assets.values.sumOf { it.size.toLong() } <= 128L * 1024 * 1024)
      val manifest =
        JSONObject()
          .put("format", "Fieldfolio.VfsElements")
          .put("version", 1)
          .put("encrypted", false)
          .put("createdAt", captured)
          .put("selectedRoots", JSONArray(ids.distinct()))
          .put("ancestorOnly", JSONArray(ancestors.toList()))
          .put("originalPaths", JSONObject(included.associate { it.id to tree.path(it.id) }))
          .put("widgets", widgets)
          .put("audio", audioInfo)
      ZipOutputStream(output).use { zip ->
        fun entry(name: String, bytes: ByteArray) {
          zip.putNextEntry(ZipEntry(name))
          var offset = 0
          while (offset < bytes.size) {
            ensureActive()
            val count = minOf(65536, bytes.size - offset)
            zip.write(bytes, offset, count)
            offset += count
          }
          zip.closeEntry()
        }
        entry("manifest.json", manifest.toString(2).toByteArray())
        zip.putNextEntry(ZipEntry("document.json"))
        val jsonSink = zip.sink().buffer()
        MoshiFactory.create()
          .adapter(ReportDocument::class.java)
          .toJson(jsonSink, ReportDocument(nodes = nodes))
        jsonSink.flush()
        zip.closeEntry()
        entry(
          "README.txt",
          "Fieldfolio VFS export. Plain ZIP, even when the report is protected. document.json preserves node settings, order, logical/display names and typed model results. manifest.json records original paths, selected roots, structural ancestors, audio metadata/artwork and cached widget snapshots at export time. Only selected subtrees and their assets are included. Other VFS items read dynamically by Document JavaScript are not guessed or exported; export their common folder when required. Device location/weather can be null when unavailable; no extra location permission or network request is made. No passwords, keys, accounts or model binaries are exported."
            .toByteArray(),
        )
        assets.forEach { (name, bytes) ->
          ensureActive()
          entry(name, bytes)
        }
      }
    }

  private fun extension(bytes: ByteArray, mime: String?, fallback: String): String {
    fun starts(vararg values: Int) =
      values.indices.all { it < bytes.size && (bytes[it].toInt() and 255) == values[it] }
    return when {
      starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> "png"
      starts(0xff, 0xd8, 0xff) -> "jpg"
      bytes.size >= 12 &&
        bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
        bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> "webp"
      starts(0x47, 0x49, 0x46, 0x38) -> "gif"
      mime == "image/png" -> "png"
      mime == "image/jpeg" -> "jpg"
      mime == "image/webp" -> "webp"
      mime == "image/svg+xml" -> "svg"
      else -> fallback
    }
  }
}
