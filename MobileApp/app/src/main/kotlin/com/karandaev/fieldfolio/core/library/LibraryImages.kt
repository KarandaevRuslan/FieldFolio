package com.karandaev.fieldfolio.core.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Session images are decoded from memory. Protected report images never enter a disk cache. */
object LibraryImages {
  @Volatile
  var managed = false
    internal set

  private data class Image(val model: String, val bytes: ByteArray)

  private val images = ConcurrentHashMap<Long, Image>()

  fun path(id: Long): String? = images[id]?.model

  fun isModel(path: String?) = path?.startsWith("/geoinspect-memory/") == true

  fun data(path: String?): ByteArray? =
    images.values.firstOrNull { it.model == path }?.bytes?.copyOf()

  internal fun put(id: Long, bytes: ByteArray?, name: String?) {
    if (bytes == null) {
      remove(id)
      return
    }
    if (images[id]?.bytes?.contentEquals(bytes) == true) return
    val next =
      Image(
        "/geoinspect-memory/$id/${UUID.randomUUID()}/${name?.substringAfterLast('/') ?: "image"}",
        bytes.copyOf(),
      )
    images.put(id, next)?.bytes?.fill(0)
    while (images.size > 8 || images.values.sumOf { it.bytes.size.toLong() } > 96L * 1024 * 1024) {
      val victim = images.keys.firstOrNull { it != id } ?: break
      remove(victim)
    }
  }

  internal fun remove(id: Long) {
    images.remove(id)?.bytes?.fill(0)
  }

  internal fun clear() {
    images.keys.toList().forEach(::remove)
  }

  fun decode(path: String, options: BitmapFactory.Options? = null): Bitmap? {
    val bytes = data(path)
    return if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    else if (isModel(path)) null else BitmapFactory.decodeFile(path, options)
  }
}
