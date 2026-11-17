package com.karandaev.fieldfolio.core.image.crop

import android.graphics.*
import androidx.compose.runtime.*
import com.karandaev.fieldfolio.core.models.decodeModelImage
import kotlin.math.*
import kotlinx.coroutines.*

class UniformBorderNotFound : IllegalArgumentException()

val FullImageBounds = NormalizedCropBox(0.0, 0.0, 1.0, 1.0)
val NormalizedCropBox.width
  get() = xMax - xMin
val NormalizedCropBox.height
  get() = yMax - yMin

fun NormalizedCropBox.inside(parent: NormalizedCropBox) =
  NormalizedCropBox(
    parent.xMin + xMin * parent.width,
    parent.yMin + yMin * parent.height,
    parent.xMin + xMax * parent.width,
    parent.yMin + yMax * parent.height,
  )

data class ImageFrame(
  val bytes: ByteArray,
  val bounds: NormalizedCropBox,
  val width: Int,
  val height: Int,
)

suspend fun renderFrame(source: ByteArray, bounds: NormalizedCropBox): ImageFrame =
  withContext(Dispatchers.Default) {
    val bitmap = decodeModelImage(source)
    try {
      val x = (bounds.xMin * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
      val y = (bounds.yMin * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
      val right = (bounds.xMax * bitmap.width).roundToInt().coerceIn(x + 1, bitmap.width)
      val bottom = (bounds.yMax * bitmap.height).roundToInt().coerceIn(y + 1, bitmap.height)
      val actual = NormalizedCropBox.fromPixels(x, y, right, bottom, bitmap.width, bitmap.height)
      if (actual == FullImageBounds)
        return@withContext ImageFrame(source, actual, bitmap.width, bitmap.height)
      val cropped = Bitmap.createBitmap(bitmap, x, y, right - x, bottom - y)
      try {
        ImageFrame(
          java.io
            .ByteArrayOutputStream()
            .also { check(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            .toByteArray(),
          actual,
          cropped.width,
          cropped.height,
        )
      } finally {
        if (cropped !== bitmap) cropped.recycle()
      }
    } finally {
      bitmap.recycle()
    }
  }

/** Private source and frame bounds survive a configuration change in their owning ViewModel. */
class ImageFrameState {
  var source by mutableStateOf<ByteArray?>(null)
    private set

  var bytes by mutableStateOf<ByteArray?>(null)
    private set

  var bounds by mutableStateOf(FullImageBounds)
    private set

  var base by mutableStateOf(FullImageBounds)
    private set

  var baseBytes by mutableStateOf<ByteArray?>(null)
    private set

  var revision by mutableLongStateOf(0)
    private set

  fun installOwned(value: ByteArray) {
    clear()
    source = value
    bytes = value
    baseBytes = value
    revision++
  }

  fun commit(frame: ImageFrame, newBase: NormalizedCropBox? = null) {
    bytes = frame.bytes
    bounds = frame.bounds
    if (newBase != null) {
      base = frame.bounds
      baseBytes = frame.bytes
    }
  }

  suspend fun prepare(bounds: NormalizedCropBox) = renderFrame(requireNotNull(source), bounds)

  fun clear() {
    val arrays = listOfNotNull(source, bytes, baseBytes).distinctBy { System.identityHashCode(it) }
    arrays.forEach { it.fill(0) }
    source = null
    bytes = null
    baseBytes = null
    bounds = FullImageBounds
    base = FullImageBounds
  }
}

/**
 * Detects an edge-connected, approximately uniform rectangular matte. Keeps interior dark areas.
 */
suspend fun uniformBorderBounds(bytes: ByteArray): NormalizedCropBox? =
  withContext(Dispatchers.Default) {
    val bitmap = decodeModelImage(bytes, 2048)
    try {
      val w = bitmap.width
      val h = bitmap.height
      if (w < 16 || h < 16) return@withContext null
      val pixels = IntArray(w * h)
      bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
      val edge = mutableListOf<Int>()
      for (x in 0 until w step max(1, w / 128)) {
        edge += pixels[x]
        edge += pixels[(h - 1) * w + x]
      }
      for (y in 0 until h step max(1, h / 128)) {
        edge += pixels[y * w]
        edge += pixels[y * w + w - 1]
      }
      fun median(channel: (Int) -> Int) = edge.map(channel).sorted()[edge.size / 2]
      val a = median(Color::alpha)
      val r = median(Color::red)
      val g = median(Color::green)
      val b = median(Color::blue)
      fun background(c: Int): Boolean =
        if (a < 16) Color.alpha(c) < 24
        else
          abs(Color.alpha(c) - a) < 24 &&
            maxOf(abs(Color.red(c) - r), abs(Color.green(c) - g), abs(Color.blue(c) - b)) <= 18
      if (edge.count(::background) < edge.size * .90) return@withContext null
      fun row(y: Int) = (0 until w).count { !background(pixels[y * w + it]) } >= max(3, w / 150)
      fun col(x: Int) = (0 until h).count { !background(pixels[it * w + x]) } >= max(3, h / 150)
      val top = (0 until h).firstOrNull(::row) ?: return@withContext null
      val bottom = (h - 1 downTo top).first(::row) + 1
      val left = (0 until w).firstOrNull(::col) ?: return@withContext null
      val right = (w - 1 downTo left).first(::col) + 1
      if ((right - left).toLong() * (bottom - top) < w.toLong() * h * .03) return@withContext null
      if (top + left + (w - right) + (h - bottom) < 4) return@withContext null
      NormalizedCropBox.fromPixels(left, top, right, bottom, w, h)
    } finally {
      bitmap.recycle()
    }
  }
