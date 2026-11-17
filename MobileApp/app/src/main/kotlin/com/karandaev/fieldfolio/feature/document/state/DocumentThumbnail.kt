package com.karandaev.fieldfolio.feature.document.state

import android.graphics.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.ui.text.*
import kotlinx.coroutines.*

internal class DocumentThumbnailCache {
  private val generation = java.util.concurrent.atomic.AtomicLong()
  private val values =
    java.util.Collections.synchronizedMap(
      object : LinkedHashMap<String, Bitmap?>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap?>?) =
          size > 64
      }
    )

  suspend fun get(node: DocumentNode, files: Map<String, ByteArray>): Bitmap? =
    withContext(Dispatchers.IO) {
      val epoch = generation.get()
      val key = "${node.asset}:${node.audio.coverAsset}"
      if (values.containsKey(key)) return@withContext values[key]
      val bytes =
        when (node.kind) {
          BlockKind.IMAGE -> files[node.asset]
          BlockKind.AUDIO ->
            files[node.audio.coverAsset] ?: files[node.asset]?.let { readAudioMetadata(it).cover }
          else -> null
        }
      val bitmap =
        bytes?.let {
          val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          BitmapFactory.decodeByteArray(it, 0, it.size, info)
          var scale = 1
          while (maxOf(info.outWidth, info.outHeight) / scale > 160) scale *= 2
          BitmapFactory.decodeByteArray(
            it,
            0,
            it.size,
            BitmapFactory.Options().apply { inSampleSize = scale },
          )
        }
      if (generation.get() == epoch) {
        values[key] = bitmap
        bitmap
      } else {
        bitmap?.recycle()
        null
      }
    }

  fun clear() {
    generation.incrementAndGet()
    values.clear()
  }
}

@Composable
internal fun DocumentThumbnail(
  node: DocumentNode,
  files: Map<String, ByteArray>,
  cache: DocumentThumbnailCache,
  fallback: @Composable () -> Unit,
) {
  val bitmap by
    produceState<Bitmap?>(null, node.asset, node.audio, files) { value = cache.get(node, files) }
  bitmap?.let {
    Image(
      it.asImageBitmap(),
      null,
      Modifier.size(44.dp).clip(MaterialTheme.shapes.small).testTag("thumbnail-${node.id}"),
      contentScale = ContentScale.Crop,
    )
  } ?: fallback()
}
