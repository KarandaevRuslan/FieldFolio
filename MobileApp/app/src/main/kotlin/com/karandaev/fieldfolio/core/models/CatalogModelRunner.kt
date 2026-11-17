package com.karandaev.fieldfolio.core.models

import android.graphics.*
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.image.preprocessing.LetterboxTransform
import com.karandaev.fieldfolio.core.inspectionapi.InspectionProvider
import com.karandaev.fieldfolio.core.inspectionapi.tflite.YoloTfliteOutputParser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.*
import kotlinx.coroutines.*
import org.tensorflow.lite.*

class ModelExecution(val result: ModelResultData, val files: Map<String, ByteArray> = emptyMap())

class CatalogModelRunner(
  private val catalog: ModelCatalog,
  private val server: InspectionProvider,
  val serverModels: ServerModelRegistry = ServerModelRegistry(),
) {
  /**
   * Runs the selected local/server adapter off the caller thread and returns a portable typed
   * result.
   */
  suspend fun run(
    model: LocalModel?,
    image: ByteArray? = null,
    text: String = "",
    serverModelId: String = "road-server",
  ): ModelExecution =
    withContext(Dispatchers.Default) {
      val start = System.nanoTime()
      if (model == null) {
        val adapter = serverModels.get(serverModelId)
        val result =
          adapter
            .analyze(server, image, text)
            .copy(
              modelId = adapter.id,
              modelName = adapter.name,
              provider = "SERVER",
              task = adapter.task,
              inputKind = adapter.inputKind.name,
            )
        ensureActive()
        return@withContext ModelExecution(
          result.copy(elapsedMillis = (System.nanoTime() - start) / 1_000_000)
        )
      }
      Interpreter(catalog.loadBuffer(model), Interpreter.Options().setNumThreads(2)).use {
        interpreter ->
        val input = interpreter.getInputTensor(0)
        val output = interpreter.getOutputTensor(model.settings.outputIndex)
        var result =
          ModelResultData(
            modelId = model.id,
            modelName = model.name,
            provider = "LOCAL",
            task = model.task,
            inputKind =
              when {
                model.adapter.isNumeric -> "NUMBERS"
                model.adapter.isText -> "TEXT"
                else -> "IMAGE"
              },
          )
        var files = emptyMap<String, ByteArray>()
        if (model.adapter.isNumeric) {
          result = runNumericModel(interpreter, model, text, result)
        } else if (model.adapter.isText) {
          result = runTextModel(interpreter, model, text, result)
        } else {
          val nchw = model.settings.inputLayout == TensorLayout.NCHW
          require(
            interpreter.inputTensorCount == 1 &&
              input.shape().size == 4 &&
              input.shape()[0] == 1 &&
              input.shape()[if (nchw) 1 else 3] == 3
          ) {
            "Image adapter requires one RGB image input"
          }
          val h = input.shape()[if (nchw) 2 else 1]
          val w = input.shape()[if (nchw) 3 else 2]
          require(w in 1..2048 && h in 1..2048 && output.numBytes() <= 64 * 1024 * 1024)
          val original = decodeModelImage(requireNotNull(image), 4096)
          val detect = model.adapter in setOf(ModelAdapter.DETECTION_NMS, ModelAdapter.YOLO_RAW)
          val transform =
            LetterboxTransform(
              original.width,
              original.height,
              w,
              h,
              minOf(w.toFloat() / original.width, h.toFloat() / original.height),
            )
          val resized = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
          Canvas(resized).apply {
            drawColor(Color.rgb(114, 114, 114))
            val rect =
              if (detect)
                RectF(
                  transform.padX,
                  transform.padY,
                  transform.padX + transform.scaledWidth,
                  transform.padY + transform.scaledHeight,
                )
              else RectF(0f, 0f, w.toFloat(), h.toFloat())
            drawBitmap(original, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
          }
          original.recycle()
          val buffer = ByteBuffer.allocateDirect(input.numBytes()).order(ByteOrder.nativeOrder())
          val pixels = IntArray(w * h)
          resized.getPixels(pixels, 0, w, 0, 0, w, h)
          resized.recycle()
          val q = input.quantizationParams()
          fun writeChannel(v: Int) {
            val normalized =
              when (model.settings.imageRange) {
                ImageRange.ZERO_ONE -> v / 255f
                ImageRange.MINUS_ONE_ONE -> v / 127.5f - 1
                ImageRange.RGB_255 -> v.toFloat()
              }
            when (input.dataType()) {
              DataType.FLOAT32 -> buffer.putFloat(normalized)
              DataType.UINT8 -> {
                require(q.scale > 0 || model.settings.imageRange == ImageRange.RGB_255) {
                  "Unquantized UINT8 input needs RGB_255 range"
                }
                val value = if (q.scale > 0) normalized / q.scale + q.zeroPoint else normalized
                buffer.put(value.roundToInt().coerceIn(0, 255).toByte())
              }
              DataType.INT8 -> {
                require(q.scale > 0)
                buffer.put(
                  (normalized / q.scale + q.zeroPoint).roundToInt().coerceIn(-128, 127).toByte()
                )
              }
              else -> error("Unsupported image input type: ${input.dataType()}")
            }
          }
          fun channel(p: Int, c: Int) =
            when (c) {
              0 -> Color.red(p)
              1 -> Color.green(p)
              else -> Color.blue(p)
            }
          if (nchw) for (c in 0..2) pixels.forEach { writeChannel(channel(it, c)) }
          else pixels.forEach { p -> for (c in 0..2) writeChannel(channel(p, c)) }
          buffer.rewind()
          val raw = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder())
          interpreter.runForMultipleInputsOutputs(
            arrayOf(buffer),
            mutableMapOf<Int, Any>(model.settings.outputIndex to raw),
          )
          raw.rewind()
          ensureActive()
          val oq = output.quantizationParams()
          val values =
            FloatArray(output.numElements()) {
              when (output.dataType()) {
                DataType.FLOAT32 -> raw.float
                DataType.UINT8 ->
                  ((raw.get().toInt() and 255) - oq.zeroPoint) *
                    (if (oq.scale > 0) oq.scale else 1f)
                DataType.INT8 ->
                  (raw.get().toInt() - oq.zeroPoint) * (if (oq.scale > 0) oq.scale else 1f)
                DataType.INT32 -> raw.int.toFloat()
                DataType.INT64 -> raw.long.toFloat()
                else -> error("Unsupported output type: ${output.dataType()}")
              }
            }
          require(values.all { it.isFinite() }) { "Non-finite model output" }
          when (model.adapter) {
            ModelAdapter.DETECTION_NMS -> {
              require(output.shape().size == 3 && output.shape()[2] == 6)
              result = result.copy(boxes = decodeNmsRows(values, model.labels, transform))
            }
            ModelAdapter.YOLO_RAW -> {
              require(output.shape().size == 3 && model.labels.isNotEmpty()) {
                "Raw YOLO adapter requires class labels"
              }
              val s = output.shape()
              val rows =
                Array(1) { Array(s[1]) { r -> FloatArray(s[2]) { c -> values[r * s[2] + c] } } }
              result =
                result.copy(
                  boxes =
                    YoloTfliteOutputParser(model.labels).parse(rows, transform).map {
                      ResultBox(
                        it.clazz,
                        it.confidence,
                        it.bbox.xMin,
                        it.bbox.yMin,
                        it.bbox.xMax,
                        it.bbox.yMax,
                      )
                    }
                )
            }
            ModelAdapter.REGRESSION -> {
              require(output.shape().size in 1..2 && values.size <= 10000)
              result =
                result.copy(
                  values =
                    values.mapIndexed { i, value ->
                      ResultValue(
                        model.labels.getOrNull(i) ?: i.toString(),
                        value.toDouble(),
                        model.settings.regressionUnits.getOrNull(i),
                      )
                    }
                )
            }
            ModelAdapter.CLASSIFICATION -> {
              require(
                output.shape().size <= 2 ||
                  output.shape().size == 4 && output.shape()[1] == 1 && output.shape()[2] == 1
              )
              val scores = scoreValues(values, model.settings.scores)
              result =
                result.copy(
                  scores =
                    scores
                      .mapIndexed { i, v ->
                        ResultScore(model.labels.getOrNull(i) ?: i.toString(), v.toDouble())
                      }
                      .sortedByDescending { it.score }
                      .take(10000)
                )
            }
            ModelAdapter.SEGMENTATION -> {
              val s = output.shape()
              require(s.size in 3..4 && s[0] == 1)
              val decoded =
                decodeSegmentation(values, s, model.settings.outputLayout, model.settings.scores)
              val mh = decoded.height
              val mw = decoded.width
              val mask = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
              val argb =
                decoded.classes
                  .map { cls ->
                    if (cls == 0) 0
                    else Color.HSVToColor(160, floatArrayOf((cls * 137.5f) % 360, .8f, 1f))
                  }
                  .toIntArray()
              val counts = decoded.classes.toList().groupingBy { it }.eachCount()
              mask.setPixels(argb, 0, mw, 0, 0, mw, mh)
              val path = "resources/doc/${UUID.randomUUID()}.png"
              val png =
                java.io
                  .ByteArrayOutputStream()
                  .also { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }
                  .toByteArray()
              mask.recycle()
              files = mapOf(path to png)
              result =
                result.copy(
                  maskAsset = path,
                  scores =
                    counts.entries
                      .sortedByDescending { it.value }
                      .map {
                        ResultScore(
                          model.labels.getOrNull(it.key) ?: it.key.toString(),
                          it.value.toDouble() / argb.size,
                          if (it.key == 0) 0xFF808080L
                          else
                            (Color.HSVToColor(floatArrayOf((it.key * 137.5f) % 360, .8f, 1f))
                              .toLong() and 0xFFFFFFFFL),
                        )
                      },
                )
            }
            else -> error("Unsupported adapter")
          }
        }
        ensureActive()
        ModelExecution(result.copy(elapsedMillis = (System.nanoTime() - start) / 1_000_000), files)
      }
    }
}

fun decodeNmsRows(
  values: FloatArray,
  labels: List<String>,
  transform: LetterboxTransform,
  threshold: Float = 0.25f,
): List<ResultBox> {
  require(values.size % 6 == 0)
  return (values.indices step 6)
    .mapNotNull { i ->
      val confidence = values[i + 4]
      val cls = values[i + 5]
      if (
        !confidence.isFinite() ||
          confidence < threshold ||
          confidence > 1 ||
          !cls.isFinite() ||
          cls < 0 ||
          abs(cls - cls.roundToInt()) > 0.001
      )
        return@mapNotNull null
      val coords = values.copyOfRange(i, i + 4)
      if (coords.any { !it.isFinite() }) return@mapNotNull null
      val b =
        transform.modelNormalizedToOriginalNormalizedBox(coords[0], coords[1], coords[2], coords[3])
      if (b.xMax <= b.xMin || b.yMax <= b.yMin) return@mapNotNull null
      ResultBox(
        labels.getOrNull(cls.roundToInt()) ?: cls.roundToInt().toString(),
        confidence.toDouble(),
        b.xMin.toDouble(),
        b.yMin.toDouble(),
        b.xMax.toDouble(),
        b.yMax.toDouble(),
      )
    }
    .sortedByDescending { it.confidence }
}
