package com.karandaev.fieldfolio.core.document

import android.graphics.*
import com.karandaev.fieldfolio.core.image.crop.*
import com.karandaev.fieldfolio.core.models.ModelExecution
import kotlin.math.*
import kotlinx.coroutines.*

fun projectPoint(point: NormalizedPoint, run: NormalizedCropBox, view: NormalizedCropBox) =
  NormalizedPoint(
    (run.xMin + point.x * run.width - view.xMin) / view.width,
    (run.yMin + point.y * run.height - view.yMin) / view.height,
  )

private fun clipRing(points: List<NormalizedPoint>): List<NormalizedPoint> {
  var current = points
  for (edge in 0..3) {
    if (current.isEmpty()) break
    fun inside(p: NormalizedPoint) =
      when (edge) {
        0 -> p.x >= 0
        1 -> p.x <= 1
        2 -> p.y >= 0
        else -> p.y <= 1
      }
    fun intersect(a: NormalizedPoint, b: NormalizedPoint): NormalizedPoint {
      val v = if (edge == 0 || edge == 2) 0.0 else 1.0
      val t = if (edge < 2) (v - a.x) / (b.x - a.x) else (v - a.y) / (b.y - a.y)
      return NormalizedPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }
    val output = mutableListOf<NormalizedPoint>()
    var previous = current.last()
    for (point in current) {
      val from = inside(previous)
      val to = inside(point)
      if (from != to) output += intersect(previous, point)
      if (to) output += point
      previous = point
    }
    current = output
  }
  return current
    .map { NormalizedPoint(it.x.coerceIn(0.0, 1.0), it.y.coerceIn(0.0, 1.0)) }
    .distinct()
}

fun projectResult(
  result: ModelResultData,
  run: NormalizedCropBox,
  view: NormalizedCropBox,
): ModelResultData {
  if (run == view) return result
  val boxes =
    result.boxes.mapNotNull { box ->
      val a = projectPoint(NormalizedPoint(box.xMin, box.yMin), run, view)
      val b = projectPoint(NormalizedPoint(box.xMax, box.yMax), run, view)
      val x0 = a.x.coerceIn(0.0, 1.0)
      val y0 = a.y.coerceIn(0.0, 1.0)
      val x1 = b.x.coerceIn(0.0, 1.0)
      val y1 = b.y.coerceIn(0.0, 1.0)
      if (x1 <= x0 || y1 <= y0) null else box.copy(xMin = x0, yMin = y0, xMax = x1, yMax = y1)
    }
  val layers =
    result.layers.map { layer ->
      layer.copy(
        polygons =
          layer.polygons.mapNotNull { polygon ->
            val rings =
              polygon.rings.map { ring -> clipRing(ring.map { projectPoint(it, run, view) }) }
            if (rings.firstOrNull().orEmpty().size < 3) null
            else SegmentationPolygon(rings.filter { it.size >= 3 })
          },
        originalPolygonJson = null,
      )
    }
  return result.copy(boxes = boxes, layers = layers)
}

suspend fun projectExecution(
  execution: ModelExecution,
  run: NormalizedCropBox,
  frame: ImageFrame,
): ModelExecution =
  withContext(Dispatchers.Default) {
    if (run == frame.bounds) return@withContext execution
    val files = execution.files.toMutableMap()
    val masks =
      (listOfNotNull(execution.result.maskAsset) +
          execution.result.layers.mapNotNull { it.maskAsset })
        .toSet()
    for (path in masks) {
      ensureActive()
      val input = files[path] ?: continue
      val source = com.karandaev.fieldfolio.core.models.decodeModelImage(input, 1024)
      val ratio = frame.width.toFloat() / frame.height
      val width = if (ratio >= 1) 1024 else max(1, (1024 * ratio).roundToInt())
      val height = if (ratio >= 1) max(1, (1024 / ratio).roundToInt()) else 1024
      val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      try {
        val b = frame.bounds
        val rect =
          RectF(
            ((run.xMin - b.xMin) / b.width * width).toFloat(),
            ((run.yMin - b.yMin) / b.height * height).toFloat(),
            ((run.xMax - b.xMin) / b.width * width).toFloat(),
            ((run.yMax - b.yMin) / b.height * height).toFloat(),
          )
        Canvas(target).drawBitmap(source, null, rect, Paint())
        files[path] =
          java.io
            .ByteArrayOutputStream()
            .also { check(target.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            .toByteArray()
      } finally {
        source.recycle()
        target.recycle()
      }
    }
    ModelExecution(projectResult(execution.result, run, frame.bounds), files)
  }
