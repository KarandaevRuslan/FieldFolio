package com.karandaev.fieldfolio.core.library

import java.util.UUID

suspend fun ReportLibrary.unlock(id: Long, password: CharArray) {
  try {
    access {
      val epoch = generation.get()
      val tree = LibraryTree(dao.all())
      val node = tree.node(id)
      tree.ancestors(id).forEach {
        if (it.lockId != null && !keys.containsKey(it.id)) throw LibraryLockedException()
      }
      require(node.lockId != null && node.salt != null && node.verifier != null)
      val key = LibraryCrypto.derive(password, node.salt, node.iterations)
      try {
        val verified = LibraryCrypto.open(node.verifier, key, "unlock:${node.lockId}")
        require(verified.contentEquals("geoinspect-library-key-v1".toByteArray()))
        synchronized(sessionMonitor) {
          if (epoch != generation.get()) throw LibraryLockedException()
          keys.put(id, key.copyOf())?.fill(0)
        }
        try {
          listOf(node)
            .filter { !it.folder }
            .forEach { leaf ->
              var bytes = objects.read(requireNotNull(leaf.payloadRef))
              val layers = tree.locks(leaf.id)
              val available = layers.takeWhile { keys.containsKey(it.id) }
              available.forEach { layer ->
                bytes =
                  LibraryCrypto.open(
                    bytes,
                    keys[layer.id] ?: throw LibraryLockedException(),
                    "payload:${layer.lockId}",
                  )
              }
              if (available.size == layers.size) packets.decode(bytes)
            }
          if (epoch != generation.get()) throw LibraryLockedException()
          notifyKeys()
        } catch (e: Exception) {
          keys.remove(id)?.fill(0)
          throw e
        }
      } finally {
        key.fill(0)
      }
    }
  } finally {
    password.fill('\u0000')
  }
}

/** Rewrites every affected leaf, retaining independent passwords on nested folders/reports. */
suspend fun ReportLibrary.setPassword(id: Long, password: CharArray?) {
  try {
    password?.let(LibraryPasswordPolicy::requireNew)
    access {
      val epoch = generation.get()
      val before = LibraryTree(dao.all())
      val node = before.node(id)
      requireUnlocked(before, id)
      val leaves = before.subtree(listOf(id)).filter { !it.folder }
      val outside = before.ancestors(id).filter { it.lockId != null }
      val oldKey = keys[id]?.copyOf()
      val salt = password?.let { LibraryCrypto.salt() }
      val key = password?.let { LibraryCrypto.derive(it, requireNotNull(salt)) }
      val lockId = password?.let { node.lockId ?: UUID.randomUUID().toString() }
      val verifier =
        key?.let {
          LibraryCrypto.seal("geoinspect-library-key-v1".toByteArray(), it, "unlock:$lockId")
        }
      synchronized(sessionMonitor) {
        if (epoch != generation.get()) {
          key?.fill(0)
          oldKey?.fill(0)
          throw LibraryLockedException()
        }
        if (key == null) keys.remove(id)?.fill(0) else keys.put(id, key)?.fill(0)
      }
      try {
        mutate(epoch) { tx ->
          tx.set(
            node.copy(
              lockId = lockId,
              salt = salt,
              verifier = verifier,
              iterations = LibraryCrypto.ITERATIONS,
            )
          )
          val after = tx.tree()
          leaves.forEach { leaf ->
            var bytes = objects.read(requireNotNull(leaf.payloadRef))
            (outside + listOf(node).filter { it.lockId != null }).forEach { layer ->
              val previousKey = if (layer.id == id) oldKey else keys[layer.id]
              bytes =
                LibraryCrypto.open(
                  bytes,
                  previousKey ?: throw LibraryLockedException(),
                  "payload:${layer.lockId}",
                )
            }
            val inner = bytes
            try {
              if (key != null) bytes = LibraryCrypto.seal(bytes, key, "payload:$lockId")
              outside.asReversed().forEach { layer ->
                bytes =
                  LibraryCrypto.seal(
                    bytes,
                    keys[layer.id] ?: throw LibraryLockedException(),
                    "payload:${layer.lockId}",
                  )
              }
              tx.payload(after.node(leaf.id), bytes)
            } finally {
              inner.fill(0)
            }
          }
        }
        oldKey?.fill(0)
        notifyKeys()
      } catch (e: Exception) {
        synchronized(sessionMonitor) {
          keys.remove(id)?.fill(0)
          if (oldKey != null && epoch == generation.get()) keys[id] = oldKey else oldKey?.fill(0)
        }
        throw e
      }
    }
  } finally {
    password?.fill('\u0000')
  }
}

suspend fun ReportLibrary.transfer(
  ids: List<Long>,
  destination: Long?,
  cut: Boolean,
  allowUnprotect: Boolean = false,
) = access {
  val before = LibraryTree(dao.all())
  before.requireDestination(destination)
  if (destination != null) requireUnlocked(before, destination)
  val roots = before.roots(ids)
  val subtree = before.subtree(roots)
  if (destination != null)
    require(subtree.none { it.id == destination }) { "Cannot move into itself or a descendant" }
  if (cut && roots.all { before.node(it).parentId == destination }) return@access
  roots.forEach { root ->
    val outside = before.ancestors(root).filter { it.lockId != null }
    val targetLocks = destination?.let { before.locks(it) }.orEmpty()
    if (!allowUnprotect && outside.isNotEmpty() && targetLocks.isEmpty())
      throw LibraryUnprotectRequired()
  }
  val temporaryKeys = mutableListOf<Long>()
  try {
    mutate { tx ->
      val mapping = subtree.associate { it.id to if (cut) it.id else tx.id() }
      val names = mutableSetOf<String>()
      roots.forEach { id ->
        val node = before.node(id)
        val name =
          com.karandaev.fieldfolio.core.names.ItemNames.unique(
            node.name,
            before.nodes
              .filter { it.parentId == destination && (!cut || it.id !in roots) }
              .map { it.name } + names,
            notify = true,
          )
        names += name
        val parent = destination
        tx.set(node.copy(id = mapping.getValue(id), parentId = parent, name = name))
      }
      subtree
        .filter { it.id !in roots }
        .forEach { node ->
          tx.set(node.copy(id = mapping.getValue(node.id), parentId = mapping[node.parentId]))
        }
      if (!cut)
        subtree.forEach { node ->
          keys[node.id]?.let {
            keys[mapping.getValue(node.id)] = it.copyOf()
            temporaryKeys += mapping.getValue(node.id)
          }
        }
      val after = tx.tree()
      val rootFor =
        subtree.associate { node ->
          node.id to
            roots.first {
              it == node.id || before.ancestors(node.id).any { parent -> parent.id == it }
            }
        }
      subtree
        .filter { !it.folder }
        .forEach { leaf ->
          val root = rootFor.getValue(leaf.id)
          val oldOutside = before.ancestors(root).filter { it.lockId != null }.map { it.lockId }
          val newOutside = destination?.let { before.locks(it).map { n -> n.lockId } }.orEmpty()
          val target = after.node(mapping.getValue(leaf.id))
          val bytes =
            if (oldOutside == newOutside) objects.read(requireNotNull(leaf.payloadRef))
            else {
              var data = objects.read(requireNotNull(leaf.payloadRef))
              before
                .ancestors(root)
                .filter { it.lockId != null }
                .forEach { layer ->
                  data =
                    LibraryCrypto.open(
                      data,
                      keys[layer.id] ?: throw LibraryLockedException(),
                      "payload:${layer.lockId}",
                    )
                }
              destination
                ?.let { before.locks(it).asReversed() }
                .orEmpty()
                .forEach { layer ->
                  data =
                    LibraryCrypto.seal(
                      data,
                      keys[layer.id] ?: throw LibraryLockedException(),
                      "payload:${layer.lockId}",
                    )
                }
              data
            }
          tx.payload(target, bytes)
        }
    }
  } catch (e: Exception) {
    temporaryKeys.forEach { keys.remove(it)?.fill(0) }
    throw e
  }
}

class LibraryUnprotectRequired :
  IllegalStateException("Moving out of a password-protected folder removes inherited protection")
