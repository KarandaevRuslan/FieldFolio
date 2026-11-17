@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.models.decodeModelImage
import com.karandaev.fieldfolio.core.ui.text.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

fun segmentationColor(index: Int): Color =
  Color(android.graphics.Color.HSVToColor(floatArrayOf((index * 137.5f) % 360, .8f, 1f)))

/** Composite raster layers one at a time, retaining one bounded bitmap rather than N bitmaps. */
@Composable
internal fun layerMasks(
  result: ModelResultData?,
  resources: Map<String, ByteArray>,
  enabled: Set<Int>? = null,
): Map<String, android.graphics.Bitmap> {
  val masks by
    produceState<Map<String, android.graphics.Bitmap>>(emptyMap(), result, resources, enabled) {
      value = emptyMap()
      value =
        withContext(Dispatchers.Default) {
          var composite: android.graphics.Bitmap? = null
          for ((index, layer) in result?.layers.orEmpty().withIndex()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (enabled != null && index !in enabled) continue
            val bytes = resources[layer.maskAsset] ?: continue
            val bitmap = runCatching { decodeModelImage(bytes, 1024) }.getOrNull() ?: continue
            try {
              if (composite == null)
                composite =
                  android.graphics.Bitmap.createBitmap(
                    bitmap.width,
                    bitmap.height,
                    android.graphics.Bitmap.Config.ARGB_8888,
                  )
              val pixels = IntArray(bitmap.width * bitmap.height)
              bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
              val rgb = segmentationColor(index).toArgb() and 0xFFFFFF
              for (i in pixels.indices) pixels[i] =
                if ((pixels[i] ushr 24) != 0 && (pixels[i] and 0xFFFFFF) != 0) (140 shl 24) or rgb
                else 0
              val tinted =
                android.graphics.Bitmap.createBitmap(
                  pixels,
                  bitmap.width,
                  bitmap.height,
                  android.graphics.Bitmap.Config.ARGB_8888,
                )
              try {
                android.graphics
                  .Canvas(composite!!)
                  .drawBitmap(
                    tinted,
                    null,
                    android.graphics.Rect(0, 0, composite!!.width, composite!!.height),
                    android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
                  )
              } finally {
                tinted.recycle()
              }
            } finally {
              bitmap.recycle()
            }
          }
          composite?.let { mapOf("composite" to it) } ?: emptyMap()
        }
    }
  return masks
}
