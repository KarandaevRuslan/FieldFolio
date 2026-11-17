package com.karandaev.fieldfolio.core.library

internal class LibraryTree(val nodes: List<LibraryNode>) {
  val byId = nodes.associateBy { it.id }

  init {
    require(nodes.size <= 5000 && byId.size == nodes.size)
    nodes.forEach { n ->
      require(n.id > 0)
      name(n.name)
      if (n.parentId != null) require(byId[n.parentId]?.folder == true) { "Invalid parent folder" }
      ancestors(n.id)
    }
    require(
      nodes
        .groupBy { it.parentId to com.karandaev.fieldfolio.core.names.ItemNames.key(it.name) }
        .values
        .none { it.size > 1 }
    ) {
      "Duplicate sibling name"
    }
  }

  fun node(id: Long) = requireNotNull(byId[id]) { "Item no longer exists" }

  fun ancestors(id: Long): List<LibraryNode> {
    var current = byId[id]?.parentId
    val result = mutableListOf<LibraryNode>()
    val seen = mutableSetOf(id)
    while (current != null) {
      require(seen.add(current) && seen.size <= 101) {
        "Cannot move into itself or a descendant; maximum depth is 100"
      }
      val parent = requireNotNull(byId[current]) { "Missing parent" }
      result.add(parent)
      current = parent.parentId
    }
    return result.reversed()
  }

  fun locks(id: Long) = (ancestors(id) + node(id)).filter { it.lockId != null }

  fun roots(ids: List<Long>): List<Long> {
    require(ids.isNotEmpty())
    ids.forEach(::node)
    val selected = ids.toSet()
    return ids.distinct().filter { id -> ancestors(id).none { it.id in selected } }
  }

  fun subtree(ids: List<Long>): List<LibraryNode> {
    val roots = roots(ids).toSet()
    return nodes
      .filter { it.id in roots || ancestors(it.id).any { a -> a.id in roots } }
      .sortedBy { ancestors(it.id).size }
  }

  fun requireDestination(parent: Long?) {
    if (parent != null) require(node(parent).folder)
  }

  fun availableName(parent: Long?, value: String, except: Long? = null): Boolean =
    nodes.none {
      it.parentId == parent &&
        com.karandaev.fieldfolio.core.names.ItemNames.key(it.name) ==
          com.karandaev.fieldfolio.core.names.ItemNames.key(value) &&
        it.id != except
    }

  companion object {
    fun name(value: String) {
      require(
        value.isNotBlank() &&
          value == value.trim() &&
          value.length <= 120 &&
          value !in setOf(".", "..") &&
          value.none { it.isISOControl() || it in "/\\:" }
      ) {
        "Use a name without slashes, colon, or control characters (up to 120 characters)"
      }
    }
  }
}
