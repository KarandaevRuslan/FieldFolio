package com.karandaev.fieldfolio.core.document

import java.util.UUID

/** Ordered document tree. All structural edits return validated immutable snapshots. */
class DocumentTree(input: ReportDocument) {
  val document = input.withUniqueNames()
  val byId = document.nodes.associateBy { it.id }

  init {
    require(
      document.version == 1 && document.nodes.size <= 1000 && byId.size == document.nodes.size
    )
    document.nodes.forEach { n ->
      require(
        n.id.isNotBlank() &&
          n.id.length <= 100 &&
          n.name.isNotBlank() &&
          n.name.length <= 120 &&
          n.body.length <= 1_000_000
      )
      if (n.kind == BlockKind.DOCUMENT)
        require(n.asset == null) {
          "Document source belongs to its node, not a private resource directory"
        }
      require(n.level in 1..6 && n.order >= 0)
      require(n.headingLevel == null || n.headingLevel in 1..6)
      require(n.syntaxExtension == null || n.syntaxExtension.matches(Regex("[a-z0-9]{1,12}")))
      require(n.audio.overrides.size <= 12 && n.audio.hiddenTags.size <= 12)
      require(
        n.audio.overrides.all { (key, value) -> key in AudioMetadata.TAGS && value.length <= 500 }
      )
      require(n.audio.hiddenTags.all { it in AudioMetadata.TAGS })
      if (n.parentId != null)
        require(byId[n.parentId]?.kind == BlockKind.FOLDER) { "Invalid document folder" }
      ancestors(n.id)
      require((n.latitude == null) == (n.longitude == null))
      n.latitude?.let { require(it.isFinite() && it in -90.0..90.0) }
      n.longitude?.let { require(it.isFinite() && it in -180.0..180.0) }
      n.zoneId?.let(java.time.ZoneId::of)
      n.result?.let { r ->
        require(r.boxes.size <= 100_000 && r.scores.size <= 10000 && r.text.length <= 1_000_000)
        r.boxes.forEach { b ->
          require(listOf(b.confidence, b.xMin, b.yMin, b.xMax, b.yMax).all(Double::isFinite))
          require(b.confidence in 0.0..1.0 && b.xMin <= b.xMax && b.yMin <= b.yMax)
        }
        require(r.values.size <= 10000 && r.values.all { it.value.isFinite() })
        r.scores.forEach { require(it.score.isFinite()) }
        require(r.layers.size <= 10000)
        r.layers.forEach { layer ->
          require(layer.confidence == null || layer.confidence.isFinite())
          require(layer.polygons.sumOf { it.rings.sumOf { ring -> ring.size } } <= 100000)
          layer.polygons.forEach { polygon ->
            polygon.rings.forEach { ring ->
              require(ring.size >= 3)
              ring.forEach { p ->
                require(p.x.isFinite() && p.y.isFinite() && p.x in 0.0..1.0 && p.y in 0.0..1.0)
              }
            }
          }
        }
      }
    }
  }

  fun node(id: String) = requireNotNull(byId[id]) { "Document item no longer exists" }

  fun children(parent: String?) =
    document.nodes.filter { it.parentId == parent }.sortedWith(compareBy({ it.order }, { it.id }))

  fun ancestors(id: String): List<DocumentNode> {
    var current = byId[id]?.parentId
    val result = mutableListOf<DocumentNode>()
    val seen = mutableSetOf(id)
    while (current != null) {
      require(seen.add(current) && seen.size <= 101) { "Document folder cycle or excessive depth" }
      val n = node(current)
      result += n
      current = n.parentId
    }
    return result.reversed()
  }

  fun subtree(ids: List<String>): List<DocumentNode> {
    ids.forEach(::node)
    val roots = ids.toSet()
    return document.nodes.filter { it.id in roots || ancestors(it.id).any { a -> a.id in roots } }
  }

  fun availableName(parent: String?, name: String, notify: Boolean = false): String {
    return com.karandaev.fieldfolio.core.names.ItemNames.unique(
      name,
      children(parent).map { it.name },
      notify = notify,
    )
  }

  fun insert(
    node: DocumentNode,
    parent: String?,
    index: Int = children(parent).size,
  ): ReportDocument {
    require(node.id !in byId)
    destination(parent)
    val siblings = children(parent).toMutableList()
    siblings.add(
      index.coerceIn(0, siblings.size),
      node.copy(parentId = parent, name = availableName(parent, node.name, notify = true)),
    )
    return normalized(
      document.nodes.filter { it.parentId != parent } +
        siblings.mapIndexed { i, n -> n.copy(order = i) }
    )
  }

  /**
   * Replaces metadata/content, preserving parent and order; resolves sibling logical-name
   * conflicts.
   */
  fun update(node: DocumentNode): ReportDocument {
    val old = node(node.id)
    require(old.parentId == node.parentId && old.order == node.order) {
      "Use move to change document position"
    }
    val name =
      com.karandaev.fieldfolio.core.names.ItemNames.unique(
        node.name,
        children(node.parentId).filter { it.id != node.id }.map { it.name },
        notify = true,
      )
    return normalized(document.nodes.map { if (it.id == node.id) node.copy(name = name) else it })
  }

  fun delete(ids: List<String>): ReportDocument {
    val removed = subtree(ids).map { it.id }.toSet()
    return normalized(document.nodes.filter { it.id !in removed })
  }

  fun transfer(
    ids: List<String>,
    parent: String?,
    index: Int = children(parent).size,
    copy: Boolean = false,
  ): ReportDocument {
    require(ids.isNotEmpty())
    destination(parent)
    val chosen =
      ids.distinct().filter { id ->
        node(id)
        ancestors(id).none { it.id in ids }
      }
    val contents = subtree(chosen)
    val removed = contents.map { it.id }.toSet()
    require(parent !in removed) { "Cannot move a folder into itself" }
    val mapping = contents.associate { it.id to if (copy) UUID.randomUUID().toString() else it.id }
    val base = if (copy) document.nodes else document.nodes.filter { it.id !in removed }
    val siblings =
      base
        .filter { it.parentId == parent }
        .sortedWith(compareBy({ it.order }, { it.id }))
        .toMutableList()
    val used = siblings.map { it.name }.toMutableList()
    val roots =
      chosen.map { id ->
        val name =
          com.karandaev.fieldfolio.core.names.ItemNames.unique(node(id).name, used, notify = true)
        used += name
        node(id).copy(id = mapping.getValue(id), parentId = parent, name = name)
      }
    siblings.addAll(index.coerceIn(0, siblings.size), roots)
    val nested =
      contents
        .filter { it.id !in chosen }
        .map { it.copy(id = mapping.getValue(it.id), parentId = mapping[it.parentId]) }
    return normalized(
      base.filter { it.parentId != parent } +
        nested +
        siblings.mapIndexed { i, n -> n.copy(order = i) }
    )
  }

  private fun destination(id: String?) {
    if (id != null) require(node(id).kind == BlockKind.FOLDER)
  }

  private fun normalized(nodes: List<DocumentNode>): ReportDocument {
    val ordered =
      nodes
        .groupBy { it.parentId }
        .values
        .flatMap { list ->
          list.sortedWith(compareBy({ it.order }, { it.id })).mapIndexed { i, n ->
            n.copy(order = i)
          }
        }
    return document.copy(nodes = ordered).withUniqueNames().also { DocumentTree(it) }
  }
}
