package com.karandaev.fieldfolio.feature.document.state

import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import java.util.UUID
import kotlinx.coroutines.*

internal fun DocumentViewModel.performAddFile(
  bytes: ByteArray,
  name: String,
  mime: String,
  kind: BlockKind,
  asCover: Boolean = false,
  identity: com.karandaev.fieldfolio.core.names.ResourceIdentity? = null,
) = action {
  com.karandaev.fieldfolio.core.names.ItemNames.requireName(name)
  require(
    bytes.isNotEmpty() &&
      bytes.size <= 48 * 1024 * 1024 &&
      files.values.sumOf { it.size.toLong() } + bytes.size <= 48L * 1024 * 1024
  ) {
    "Report attachment limit is 48 MB"
  }
  val ext =
    name.substringAfterLast('.', "bin").take(8).takeIf { it.matches(Regex("[a-zA-Z0-9]+")) }
      ?: "bin"
  val path = "resources/doc/${UUID.randomUUID()}.$ext"
  files = files + (path to bytes)
  if (asCover) setCover(path)
  else
    document =
      tree.insert(
        DocumentNode(
          name = name,
          kind = kind,
          asset = path,
          mime = mime,
          displayName =
            com.karandaev.fieldfolio.core.names.ItemNames.display(identity?.displayName),
          hideTitle = identity?.hideTitle ?: false,
          visible = identity?.visible ?: true,
        ),
        folder,
      )
  dirty = true
}

internal fun DocumentViewModel.performUpdateTextResource(node: DocumentNode, bytes: ByteArray) =
  action {
    require(node.kind == BlockKind.TEXT_FILE)
    require(bytes.size <= TextResourceCodec.EDIT_BYTES)
    val path = "resources/doc/${UUID.randomUUID()}.txt"
    val next = tree.update(node.copy(asset = path, mime = node.mime ?: "text/plain"))
    val retained = (files + (path to bytes.copyOf())).filterKeys { it in next.resourcePaths() }
    require(retained.values.sumOf { it.size.toLong() } <= 48L * 1024 * 1024)
    files = retained
    document = next
    dirty = true
  }

internal fun DocumentViewModel.performEditTextFile(node: DocumentNode, text: String) {
  if (busy) return
  val original = files[node.asset] ?: return
  busy = true
  job =
    viewModelScope.launch {
      try {
        val bytes =
          withContext(Dispatchers.Default) {
            val editable =
              requireNotNull(TextResourceCodec.editable(original)) { "Resource is read-only" }
            if (text == editable.text) null else TextResourceCodec.encode(text, editable.encoding)
          } ?: return@launch
        val path =
          "resources/doc/${UUID.randomUUID()}.${node.asset!!.substringAfterLast('.', "txt")}"
        val next = tree.update(node.copy(asset = path))
        val referenced = next.resourcePaths()
        require(
          (files + (path to bytes))
            .filterKeys { it in referenced }
            .values
            .sumOf { it.size.toLong() } <= 48L * 1024 * 1024
        )
        files = files + (path to bytes)
        document = next
        dirty = true
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        error = e
      } finally {
        busy = false
      }
    }
}

internal fun DocumentViewModel.performUpdateImage(
  node: DocumentNode,
  bytes: ByteArray,
  identity: com.karandaev.fieldfolio.core.names.ResourceIdentity,
) = action {
  require(node.kind == BlockKind.IMAGE && bytes.isNotEmpty() && bytes.size <= 48 * 1024 * 1024)
  val path = "resources/doc/${UUID.randomUUID()}.img"
  val info = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
  android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, info)
  val next =
    tree.update(identity.applyTo(node).copy(asset = path, mime = info.outMimeType ?: node.mime))
  val retained = (files + (path to bytes)).filterKeys { it in next.resourcePaths() }
  require(retained.values.sumOf { it.size.toLong() } <= 48L * 1024 * 1024)
  document = next
  files = retained
  dirty = true
}

internal fun DocumentViewModel.performUpdateAudio(node: DocumentNode, cover: ByteArray?) = action {
  var next = node
  if (cover != null) {
    require(files.values.sumOf { it.size.toLong() } + cover.size <= 48L * 1024 * 1024)
    val path = "resources/doc/${UUID.randomUUID()}.img"
    files = files + (path to cover)
    next = node.copy(audio = node.audio.copy(coverAsset = path))
  }
  document = tree.update(next)
  dirty = true
}
