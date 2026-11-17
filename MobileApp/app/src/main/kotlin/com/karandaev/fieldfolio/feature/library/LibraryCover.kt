package com.karandaev.fieldfolio.feature.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.library.*
import kotlinx.coroutines.*

internal class LibraryCoverCache {
  private val cache =
    java.util.Collections.synchronizedMap(
      object : LinkedHashMap<String, Bitmap?>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap?>?) =
          size > 32
      }
    )
  private val mutex = kotlinx.coroutines.sync.Mutex()

  suspend fun get(library: ReportLibrary, node: LibraryNode, generation: Long): Bitmap? =
    withContext(Dispatchers.IO) {
      mutex.lock()
      try {
        val key = "${node.id}:${node.payloadRef}:$generation"
        if (cache.containsKey(key)) return@withContext cache[key]
        val image =
          runCatching {
              val bundle = library.loadDocument(node.id)
              try {
                val bytes = bundle.files[bundle.document.cover] ?: return@runCatching null
                val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, info)
                var sample = 1
                while (maxOf(info.outWidth, info.outHeight) / sample > 192) sample *= 2
                BitmapFactory.decodeByteArray(
                  bytes,
                  0,
                  bytes.size,
                  BitmapFactory.Options().apply { inSampleSize = sample },
                )
              } finally {
                bundle.files.values.forEach { it.fill(0) }
              }
            }
            .getOrNull()
        if (library.generation.get() == generation) {
          cache[key] = image
          image
        } else null
      } finally {
        mutex.unlock()
      }
    }

  fun clear() {
    cache.clear()
  }
}

@Composable
internal fun LibraryCover(vm: LibraryViewModel, node: LibraryNode) {
  val epoch = vm.session
  val bitmap by
    produceState<Bitmap?>(null, node.id, node.payloadRef, epoch) {
      value = null
      if (!vm.locked(node.id)) value = vm.covers.get(vm.library, node, vm.library.generation.get())
    }
  val visible = bitmap.takeUnless { vm.locked(node.id) }
  if (visible == null) Icon(Icons.Default.Description, null)
  else
    Image(
      visible.asImageBitmap(),
      null,
      Modifier.size(44.dp)
        .testTag("library-cover-${node.id}")
        .clip(androidx.compose.material3.MaterialTheme.shapes.small),
      contentScale = ContentScale.Crop,
    )
}
