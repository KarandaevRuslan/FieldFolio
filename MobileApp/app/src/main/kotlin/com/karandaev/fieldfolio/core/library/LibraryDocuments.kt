package com.karandaev.fieldfolio.core.library

import com.karandaev.fieldfolio.core.data.local.report.mapper.toDomain
import com.karandaev.fieldfolio.core.data.local.report.mapper.toEntityWithResults
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint

/** Media stays in the same authenticated/encrypted packet as the document tree. */
data class DocumentBundle(
  val report: InspectionReport,
  val document: ReportDocument,
  val files: Map<String, ByteArray>,
  val name: String? = null,
  val displayName: String? = null,
)

internal fun ReportPacket.document(): ReportDocument {
  fun convertedLayers(modelId: String) =
    snapshot.report.results
      .firstOrNull { "legacy:${it.result.id}:${it.result.sortOrder}" == modelId }
      ?.segmentations
      .orEmpty()
      .sortedBy { it.sortOrder }
      .map { segment ->
        SegmentationLayer(
          segment.clazz,
          segment.confidence,
          segment.maskPath,
          runCatching { parseSegmentationPolygons(segment.polygonJson) }.getOrDefault(emptyList()),
          segment.polygonJson,
        )
      }
  snapshot.document?.let { document ->
    return document
      .copy(
        nodes =
          document.nodes.map { node ->
            val result = node.result
            if (result != null && result.layers.isEmpty() && result.modelId.startsWith("legacy:"))
              node.copy(result = result.copy(layers = convertedLayers(result.modelId), text = ""))
            else node
          }
      )
      .withUniqueNames()
  }
  val r = snapshot.report.report
  val nodes =
    mutableListOf(
      DocumentNode(
        id = "legacy-location",
        name = "Location",
        kind = BlockKind.LOCATION,
        latitude = r.latitude,
        longitude = r.longitude,
      )
    )
  snapshot.detectionImage?.let {
    nodes +=
      DocumentNode(
        id = "legacy-image",
        order = nodes.size,
        name = "Image",
        kind = BlockKind.IMAGE,
        asset = it,
        mime = "image/*",
      )
  }
  snapshot.report.results
    .sortedWith(compareBy({ it.result.sortOrder }, { it.result.id }))
    .forEachIndexed { i, payload ->
      val result = payload.result
      val mask = payload.segmentations.firstOrNull { it.maskPath != null }?.maskPath
      nodes +=
        DocumentNode(
          id = "legacy-result-$i",
          order = nodes.size,
          name = result.title.take(120).ifBlank { "Result ${i+1}" },
          kind = BlockKind.MODEL_RESULT,
          body = result.description,
          result =
            ModelResultData(
              id = "legacy-run-$i",
              modelId = "legacy:${result.id}:${result.sortOrder}",
              modelName = result.title.ifBlank { "Legacy model" },
              provider = "LEGACY",
              task = result.type.name,
              inputAsset = snapshot.detectionImage,
              maskAsset = mask,
              layers = convertedLayers("legacy:${result.id}:${result.sortOrder}"),
              boxes =
                payload.detections
                  .sortedBy { it.sortOrder }
                  .map { ResultBox(it.clazz, it.confidence, it.xMin, it.yMin, it.xMax, it.yMax) },
              text = "",
            ),
        )
    }
  return ReportDocument(cover = snapshot.detectionImage, nodes = nodes).withUniqueNames()
}

suspend fun ReportLibrary.loadDocument(id: Long): DocumentBundle = access {
  val tree = LibraryTree(dao.all())
  val packet = readPacket(tree, id)
  DocumentBundle(
    packet.snapshot.report.toDomain().copy(id = id),
    packet.document(),
    packet.files,
    tree.node(id).name,
    tree.node(id).displayName,
  )
}

suspend fun ReportLibrary.saveDocument(
  id: Long,
  parent: Long?,
  title: String,
  description: String,
  document: ReportDocument,
  files: Map<String, ByteArray>,
  expectedUpdatedAt: Long? = null,
  identity: com.karandaev.fieldfolio.core.names.ResourceIdentity? = null,
): Long = access {
  require(
    com.karandaev.fieldfolio.core.names.ItemNames.valid(title) &&
      title.length <= 120 &&
      description.length <= 1_000_000
  )
  identity?.let {
    require(it.valid && !it.hideTitle && it.visible)
    LibraryTree.name(it.name)
  }
  DocumentTree(document)
  val point =
    document.locations().firstOrNull { !it.device && it.latitude != null && it.longitude != null }
  mutate { tx ->
    val tree = tx.tree()
    val previous = if (id == 0L) null else readPacket(tree, id)
    if (previous != null && expectedUpdatedAt != null)
      require(previous.snapshot.report.report.lastUpdatedAtMillis == expectedUpdatedAt) {
        "Document changed; reopen it before saving"
      }
    val now =
      maxOf(
        System.currentTimeMillis(),
        (previous?.snapshot?.report?.report?.lastUpdatedAtMillis ?: 0) + 1,
      )
    val report =
      InspectionReport(
        id = id,
        title = title.trim(),
        content = description,
        lastUpdatedAtMillis = now,
        point = MapPoint(point?.latitude ?: 0.0, point?.longitude ?: 0.0),
      )
    // Keep legacy result payloads for lossless round trips, including masks/polygons older UIs
    // cannot render.
    val legacy = previous?.snapshot?.report
    val legacyKeys = document.nodes.mapNotNull { it.result?.modelId }.toSet()
    val relation =
      report.toEntityWithResults().let {
        if (legacy == null) it
        else
          it.copy(
            results =
              legacy.results.filter { r ->
                "legacy:${r.result.id}:${r.result.sortOrder}" in legacyKeys
              }
          )
      }
    val retained =
      document.resourcePaths() +
        relation.results.flatMap { it.segmentations.mapNotNull { m -> m.maskPath } }
    val legacyImage = previous?.snapshot?.detectionImage?.takeIf { it in retained }
    val remaining = files.filterKeys { it in retained }
    val missing =
      previous?.snapshot?.missingResources.orEmpty().filter { it in retained && it !in remaining }
    val packet =
      ReportPacket(
        ReportSnapshot(
          version = 2,
          report = relation,
          detectionImage = legacyImage,
          missingResources = missing,
          document = DocumentTree(document).document,
        ),
        remaining,
      )
    if (id == 0L) {
      val created = createReport(tx, packet, parent, identity?.name)
      if (identity != null)
        tx.set(
          tx
            .tree()
            .node(created)
            .copy(
              displayName =
                com.karandaev.fieldfolio.core.names.ItemNames.display(identity.displayName)
            )
        )
      created
    } else {
      val node = tree.node(id)
      requireUnlocked(tree, id)
      val name =
        if (identity == null) node.name
        else
          com.karandaev.fieldfolio.core.names.ItemNames.unique(
            identity.name,
            tree.nodes.filter { it.parentId == node.parentId && it.id != id }.map { it.name },
            notify = true,
          )
      tx.payload(
        node.copy(
          name = name,
          displayName =
            if (identity == null) node.displayName
            else com.karandaev.fieldfolio.core.names.ItemNames.display(identity.displayName),
          updatedAt = now,
        ),
        encrypt(tree, node, packets.encode(packet)),
      )
      id
    }
  }
}
