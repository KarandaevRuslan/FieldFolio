package com.karandaev.fieldfolio.core.document.runtime

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.WebResourceResponse
import androidx.compose.runtime.staticCompositionLocalOf
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.json.MoshiFactory
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** A snapshot of one open report, never the application library or Android filesystem. */
class DocumentAccessContext(
  val key: String,
  val document: ReportDocument,
  val files: Map<String, ByteArray>,
  val title: String = "",
  val description: String = "",
  val device: () -> com.karandaev.fieldfolio.core.domain.model.location.MapPoint? = { null },
  val weather: com.karandaev.fieldfolio.core.weather.WeatherProvider? = null,
) {
  private val mutex = Mutex()
  private var prepared: DocumentResources? = null

  suspend fun resources(): DocumentResources =
    mutex.withLock {
      prepared
        ?: withContext(Dispatchers.Default) { DocumentResources.create(this@DocumentAccessContext) }
          .also { prepared = it }
    }
}

/** Preview the unsaved source in its real report folder without changing the stored VFS. */
fun documentDraftAccess(
  parent: DocumentAccessContext?,
  draft: DocumentNode,
  files: Map<String, ByteArray>,
): DocumentAccessContext {
  val original = parent?.document ?: ReportDocument()
  val parentId =
    draft.parentId?.takeIf { id ->
      original.nodes.any { it.id == id && it.kind == BlockKind.FOLDER }
    }
  val siblings = original.nodes.filter { it.parentId == parentId && it.id != draft.id }
  val safe =
    draft.copy(
      parentId = parentId,
      name =
        com.karandaev.fieldfolio.core.names.ItemNames.unique(draft.name, siblings.map { it.name }),
    )
  val nodes =
    if (original.nodes.any { it.id == draft.id })
      original.nodes.map { if (it.id == draft.id) safe else it }
    else original.nodes + safe.copy(order = siblings.size)
  return DocumentAccessContext(
    (parent?.key ?: "document") + ":draft:${draft.id}",
    original.copy(nodes = nodes),
    files,
    parent?.title.orEmpty(),
    parent?.description.orEmpty(),
    parent?.device ?: { null },
    parent?.weather,
  )
}

val LocalDocumentAccess = staticCompositionLocalOf<DocumentAccessContext?> { null }

/** Native read-only object/asset provider. It never resolves a path on disk. */
class DocumentResources
private constructor(
  private val context: DocumentAccessContext,
  val tree: DocumentTree,
  private val audio: Map<String, AudioMetadata>,
) {
  data class Asset(val bytes: ByteArray, val mime: String)

  private val imageTypes =
    java.util.Collections.synchronizedMap(java.util.IdentityHashMap<ByteArray, String>())
  private val adapter = MoshiFactory.create().adapter(DocumentNode::class.java)
  private val ids = tree.document.nodes.associateBy { token(it.id) }

  fun path(node: DocumentNode): String = tree.path(node.id)

  private fun assets(node: DocumentNode): Map<String, Asset> = buildMap {
    fun add(role: String, path: String?, mime: String) {
      context.files[path]?.let { bytes ->
        val actual =
          if (mime.startsWith("image/") && mime != "image/svg+xml")
            imageTypes[bytes] ?: imageMime(bytes).also { imageTypes[bytes] = it }
          else mime
        put(role, Asset(bytes, actual))
      }
    }
    when (node.kind) {
      BlockKind.IMAGE -> add("data", node.asset, node.mime ?: "image/jpeg")
      BlockKind.AUDIO -> {
        add("data", node.asset, node.mime ?: "audio/mpeg")
        add("cover", node.audio.coverAsset, "image/jpeg")
        if ("cover" !in this) audio[node.id]?.cover?.let { put("cover", Asset(it, imageMime(it))) }
      }
      BlockKind.TEXT_FILE -> add("data", node.asset, node.mime ?: "text/plain")
      BlockKind.MODEL_RESULT -> {
        add("input", node.result?.inputAsset, "image/jpeg")
        add("mask", node.result?.maskAsset, "image/png")
        node.result?.layers?.forEachIndexed { index, layer ->
          add("layer-$index", layer.maskAsset, "image/png")
        }
      }
      else -> Unit
    }
  }

  private fun base(host: String, scope: String) = "https://$host/vfs/$scope/"

  private fun url(host: String, scope: String, node: DocumentNode, role: String) =
    base(host, scope) + "item/${token(node.id)}/$role"

  fun manifest(host: String, scope: String, documentId: String): JSONObject {
    val current = tree.node(documentId)
    val entries = JSONArray()
    var pathCharacters = 0
    tree.document.nodes.forEach { node ->
      val logicalPath = path(node)
      pathCharacters += logicalPath.length
      require(pathCharacters <= 4 * 1024 * 1024) {
        "Report paths exceed the document runtime limit"
      }
      val resources = JSONObject()
      assets(node).forEach { (role, value) ->
        resources.put(
          role,
          JSONObject().put("url", url(host, scope, node, role)).put("mime", value.mime),
        )
      }
      if (
        node.kind !in setOf(BlockKind.FOLDER, BlockKind.IMAGE, BlockKind.AUDIO, BlockKind.TEXT_FILE)
      )
        resources.put(
          "data",
          JSONObject()
            .put("url", url(host, scope, node, "data"))
            .put(
              "mime",
              if (node.kind in setOf(BlockKind.TEXT, BlockKind.HEADING, BlockKind.DOCUMENT))
                "text/plain"
              else "application/json",
            ),
        )
      entries.put(
        JSONObject()
          .put("id", node.id)
          .put("parentId", node.parentId ?: JSONObject.NULL)
          .put("name", node.name)
          .put("logicalName", node.name)
          .put("displayName", node.displayName ?: JSONObject.NULL)
          .put("hideTitle", node.hideTitle)
          .put("visible", node.visible)
          .put("order", node.order)
          .put("path", logicalPath)
          .put("kind", wireKind(node.kind))
          .put("metadataUrl", url(host, scope, node, "meta"))
          .put("assets", resources)
      )
    }
    val report = JSONObject().put("title", context.title).put("description", context.description)
    context.files[context.document.cover]?.let {
      val coverUrl = base(host, scope) + "report/cover"
      report
        .put("coverUrl", coverUrl)
        .put("cover", JSONObject().put("url", coverUrl).put("mime", imageMime(it)))
    }
    return JSONObject()
      .put("version", 1)
      .put("origin", "https://$host")
      .put("scope", scope)
      .put("documentId", documentId)
      .put("base", current.parentId?.let { path(tree.node(it)) } ?: "/")
      .put("entries", entries)
      .put("report", report)
  }

  private fun metadata(node: DocumentNode, host: String, scope: String): JSONObject {
    val value = JSONObject(adapter.toJson(node))
    value.remove("asset")
    value
      .put("kind", wireKind(node.kind))
      .put("logicalName", node.name)
      .put("path", path(node))
      .put("visualTitle", node.visualTitle)
      .put("displayName", node.displayName ?: JSONObject.NULL)
    if (node.kind == BlockKind.FOLDER)
      value.put("children", JSONArray(tree.children(node.id).map(::path)))
    if (node.kind == BlockKind.TEXT_FILE)
      context.files[node.asset]
        ?.let { TextResourceCodec.editable(it)?.text }
        ?.let { value.put("text", it) }
    else if (node.kind in setOf(BlockKind.TEXT, BlockKind.HEADING, BlockKind.DOCUMENT))
      value.put("text", node.body)
    val resourceUrls = JSONObject()
    assets(node).keys.forEach { resourceUrls.put(it, url(host, scope, node, it)) }
    value.put("resources", resourceUrls)
    if (node.kind in setOf(BlockKind.LOCATION, BlockKind.WEATHER, BlockKind.CLOCK)) {
      val point =
        if (node.deviceLocation && node.kind != BlockKind.CLOCK) context.device()
        else
          node.latitude?.let { lat ->
            node.longitude?.let { lon ->
              com.karandaev.fieldfolio.core.domain.model.location.MapPoint(lat, lon)
            }
          }
      value.put(
        "resolvedLocation",
        point?.let { JSONObject().put("latitude", it.latitude).put("longitude", it.longitude) }
          ?: JSONObject.NULL,
      )
      if (node.kind == BlockKind.CLOCK) {
        val zone =
          if (node.deviceLocation) java.time.ZoneId.systemDefault()
          else node.zoneId?.let(java.time.ZoneId::of)
        value
          .put("resolvedZoneId", zone?.id ?: JSONObject.NULL)
          .put("epochMillis", System.currentTimeMillis())
      }
      if (node.kind == BlockKind.WEATHER) {
        // Reading a document never starts a weather/network request. Only already cached native
        // data is shared.
        val cached =
          point?.let { runBlocking { context.weather?.cachedWeather(it.latitude, it.longitude) } }
        value.put(
          "weather",
          cached?.let {
            JSONObject()
              .put("temperature", it.temperature)
              .put("weatherCode", it.weatherCode)
              .put("zoneId", it.zoneId ?: JSONObject.NULL)
          } ?: JSONObject.NULL,
        )
      }
    }
    value.optJSONObject("audio")?.apply {
      remove("coverAsset")
      audio[node.id]?.let {
        put("tags", JSONObject(it.tags))
        put("displayedTags", JSONObject(it.displayed(node.audio)))
      }
      if (resourceUrls.has("cover")) {
        put("coverUrl", resourceUrls.getString("cover"))
        put(
          "cover",
          JSONObject()
            .put("url", resourceUrls.getString("cover"))
            .put("mime", assets(node).getValue("cover").mime),
        )
      }
    }
    value.optJSONObject("result")?.apply {
      remove("inputAsset")
      remove("maskAsset")
      if (resourceUrls.has("input")) put("inputUrl", resourceUrls.getString("input"))
      if (resourceUrls.has("mask")) put("maskUrl", resourceUrls.getString("mask"))
      optJSONArray("layers")?.let { layers ->
        for (i in 0 until layers.length()) layers.getJSONObject(i).apply {
          remove("maskAsset")
          if (resourceUrls.has("layer-$i")) put("maskUrl", resourceUrls.getString("layer-$i"))
        }
      }
      put("coordinateSystem", "normalized-image-0-1")
    }
    return value
  }

  /**
   * Only exact capability routes are accepted; query strings and encoded path tricks are rejected.
   */
  fun response(uri: Uri, host: String, scope: String): WebResourceResponse? {
    if (
      uri.scheme != "https" ||
        uri.host != host ||
        uri.port != -1 ||
        uri.query != null ||
        uri.fragment != null
    )
      return null
    val encoded = uri.encodedPath.orEmpty()
    if (!encoded.matches(Regex("/vfs/[a-z0-9-]+/(item/[A-Za-z0-9_-]+/[a-z0-9-]+|report/cover)")))
      return null
    val segments = uri.pathSegments
    if (segments[0] != "vfs" || segments[1] != scope) return null
    if (segments[2] == "report")
      return context.files[context.document.cover]?.let { result(Asset(it, imageMime(it))) }
    val node = ids[segments[3]] ?: return null
    val role = segments[4]
    val asset = assets(node)[role]
    if (asset != null) return result(asset)
    val body =
      when {
        role == "meta" -> metadata(node, host, scope).toString()
        role == "data" &&
          node.kind in setOf(BlockKind.TEXT, BlockKind.HEADING, BlockKind.DOCUMENT) -> node.body
        role == "data" && node.kind == BlockKind.MODEL_RESULT ->
          metadata(node, host, scope).optJSONObject("result")?.toString() ?: "null"
        role == "data" &&
          node.kind in setOf(BlockKind.LOCATION, BlockKind.WEATHER, BlockKind.CLOCK) ->
          metadata(node, host, scope).toString()
        else -> return null
      }
    val bytes = body.toByteArray(Charsets.UTF_8)
    if (bytes.size > 8 * 1024 * 1024)
      return WebResourceResponse(
        "text/plain",
        "UTF-8",
        413,
        "Too large",
        noStore,
        ByteArrayInputStream(ByteArray(0)),
      )
    return result(
      Asset(
        bytes,
        if (
          role == "data" &&
            node.kind in setOf(BlockKind.TEXT, BlockKind.HEADING, BlockKind.DOCUMENT)
        )
          "text/plain"
        else "application/json",
      )
    )
  }

  companion object {
    private val noStore =
      mapOf(
        "Cache-Control" to "no-store",
        "Access-Control-Allow-Origin" to "null",
        "X-Content-Type-Options" to "nosniff",
        "Content-Security-Policy" to
          "default-src 'none'; style-src 'unsafe-inline'; img-src data:; sandbox",
      )

    suspend fun create(context: DocumentAccessContext): DocumentResources {
      val tree = DocumentTree(context.document)
      val decoded = mutableMapOf<String, AudioMetadata>()
      val cache = mutableMapOf<String, AudioMetadata>()
      for (node in tree.document.nodes.filter { it.kind == BlockKind.AUDIO }) {
        val path = node.asset ?: continue
        val bytes = context.files[path] ?: continue
        decoded[node.id] = cache[path] ?: readAudioMetadata(bytes).also { cache[path] = it }
      }
      return DocumentResources(context, tree, decoded)
    }

    private fun token(id: String) =
      Base64.encodeToString(
        id.toByteArray(),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
      )

    private fun wireKind(kind: BlockKind) = kind.name.lowercase(Locale.ROOT).replace('_', '-')

    private fun imageMime(bytes: ByteArray): String {
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
      return options.outMimeType ?: "image/jpeg"
    }

    private fun result(asset: Asset) =
      WebResourceResponse(
        asset.mime,
        null,
        200,
        "OK",
        noStore + ("Content-Length" to asset.bytes.size.toString()),
        ByteArrayInputStream(asset.bytes),
      )
  }
}
