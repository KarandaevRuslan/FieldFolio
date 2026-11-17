package com.karandaev.fieldfolio.core.models

import com.karandaev.fieldfolio.core.document.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.*

fun parseNumericInput(text: String, expected: Int): DoubleArray {
  require(expected in 1..1_000_000 && text.length <= 1_000_000)
  val tokens = text.trim().split(Regex("[\\s,;\\[\\]]+")).filter { it.isNotEmpty() }
  require(tokens.size == expected) { "Expected $expected numeric values, got ${tokens.size}" }
  return tokens
    .map {
      requireNotNull(it.toDoubleOrNull()) { "Invalid number" }
        .also { v -> require(v.isFinite()) { "Non-finite number" } }
    }
    .toDoubleArray()
}

internal fun numericBuffer(
  values: DoubleArray,
  type: DataType,
  scale: Float = 1f,
  zero: Int = 0,
): ByteBuffer {
  val size =
    when (type) {
      DataType.INT64 -> 8
      DataType.FLOAT32,
      DataType.INT32 -> 4
      DataType.UINT8,
      DataType.INT8 -> 1
      else -> error("Unsupported numeric input: $type")
    }
  return ByteBuffer.allocateDirect(values.size * size).order(ByteOrder.nativeOrder()).apply {
    values.forEach { v ->
      require(v.isFinite())
      when (type) {
        DataType.FLOAT32 -> {
          require(v.toFloat().isFinite())
          putFloat(v.toFloat())
        }
        DataType.INT32 -> {
          require(v % 1.0 == 0.0 && v >= Int.MIN_VALUE && v <= Int.MAX_VALUE)
          putInt(v.toInt())
        }
        DataType.INT64 -> {
          require(v % 1.0 == 0.0 && v >= Long.MIN_VALUE.toDouble() && v < Long.MAX_VALUE.toDouble())
          putLong(v.toLong())
        }
        DataType.UINT8,
        DataType.INT8 -> {
          require(scale > 0f)
          val q = kotlin.math.round(v / scale + zero)
          val range = if (type == DataType.UINT8) 0..255 else -128..127
          require(q >= range.first && q <= range.last) {
            "Numeric value exceeds quantized input range"
          }
          put(q.toInt().toByte())
        }
        else -> error("Unsupported numeric input")
      }
    }
    rewind()
  }
}

internal fun numericTextBuffer(
  text: String,
  expected: Int,
  type: DataType,
  scale: Float = 1f,
  zero: Int = 0,
): ByteBuffer {
  if (type != DataType.INT32 && type != DataType.INT64)
    return numericBuffer(parseNumericInput(text, expected), type, scale, zero)
  require(expected in 1..1_000_000 && text.length <= 1_000_000)
  val tokens = text.trim().split(Regex("[\\s,;\\[\\]]+")).filter { it.isNotEmpty() }
  require(tokens.size == expected) { "Wrong numeric input size" }
  return ByteBuffer.allocateDirect(expected * if (type == DataType.INT64) 8 else 4)
    .order(ByteOrder.nativeOrder())
    .apply {
      tokens.forEach { token ->
        val value = java.math.BigDecimal(token)
        if (type == DataType.INT64) putLong(value.longValueExact())
        else putInt(value.intValueExact())
      }
      rewind()
    }
}

internal fun runNumericModel(
  interpreter: Interpreter,
  model: LocalModel,
  text: String,
  result: ModelResultData,
): ModelResultData {
  require(interpreter.inputTensorCount == 1) { "Numeric adapter requires a single tensor" }
  val input = interpreter.getInputTensor(0)
  val output = interpreter.getOutputTensor(model.settings.outputIndex)
  require(output.numElements() in 1..10000 && output.numBytes() <= 1024 * 1024)
  val q = input.quantizationParams()
  val data =
    numericTextBuffer(
      text,
      input.numElements(),
      input.dataType(),
      if (q.scale > 0f) q.scale else 1f,
      q.zeroPoint,
    )
  val target = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder())
  interpreter.runForMultipleInputsOutputs(
    arrayOf(data),
    mutableMapOf<Int, Any>(model.settings.outputIndex to target),
  )
  val numbers = readModelValues(output, target)
  return if (model.adapter == ModelAdapter.NUMERIC_CLASSIFICATION)
    result.copy(
      inputText = text,
      scores =
        scoreValues(numbers, model.settings.scores)
          .mapIndexed { i, v -> ResultScore(model.labels.getOrNull(i) ?: i.toString(), v) }
          .sortedByDescending { it.score },
    )
  else
    result.copy(
      inputText = text,
      values =
        numbers.mapIndexed { i, v ->
          ResultValue(
            model.labels.getOrNull(i) ?: i.toString(),
            v.toDouble(),
            model.settings.regressionUnits.getOrNull(i),
          )
        },
    )
}
