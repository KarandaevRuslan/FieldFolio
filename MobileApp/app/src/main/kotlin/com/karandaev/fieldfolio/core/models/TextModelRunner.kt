package com.karandaev.fieldfolio.core.models

import com.karandaev.fieldfolio.core.document.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.*

internal fun readModelValues(tensor: Tensor, buffer: ByteBuffer): FloatArray {
  buffer.rewind()
  val q = tensor.quantizationParams()
  return FloatArray(tensor.numElements()) {
      when (tensor.dataType()) {
        DataType.FLOAT32 -> buffer.float
        DataType.UINT8 ->
          ((buffer.get().toInt() and 255) - q.zeroPoint) * (if (q.scale > 0) q.scale else 1f)
        DataType.INT8 -> (buffer.get().toInt() - q.zeroPoint) * (if (q.scale > 0) q.scale else 1f)
        DataType.INT32 -> buffer.int.toFloat()
        DataType.INT64 -> buffer.long.toFloat()
        else -> error("Unsupported output type: ${tensor.dataType()}")
      }
    }
    .also { require(it.all(Float::isFinite)) { "Non-finite model output" } }
}

internal fun runTextModel(
  interpreter: Interpreter,
  model: LocalModel,
  text: String,
  result: ModelResultData,
): ModelResultData {
  require(text.length <= 1_000_000)
  require(model.settings.outputIndex in 0 until interpreter.outputTensorCount)
  var truncated = false
  val inputs: Array<Any>
  if (model.adapter == ModelAdapter.TEXT_WORDPIECE) {
    require(interpreter.inputTensorCount in 1..3)
    val tensors = (0 until interpreter.inputTensorCount).map(interpreter::getInputTensor)
    require(
      tensors.all {
        it.shape().size == 2 &&
          it.shape()[0] == 1 &&
          it.dataType() in setOf(DataType.INT32, DataType.INT64)
      }
    )
    val fixed = tensors.map { it.shape()[1] }.filter { it > 0 }.distinct()
    require(fixed.size <= 1) { "Token inputs have different lengths" }
    val dynamic = tensors.any { it.shapeSignature().getOrNull(1) == -1 }
    val length = if (dynamic) model.settings.sequenceLength else fixed.single()
    require(length in 2..4096)
    tensors.forEachIndexed { i, t ->
      if (t.shape()[1] != length) interpreter.resizeInput(i, intArrayOf(1, length), true)
    }
    interpreter.allocateTensors()
    val tokens =
      WordPieceTokenizer(model.settings.vocabulary, model.settings.lowercase).encode(text, length)
    truncated = tokens.truncated
    val roles = tokenInputRoles(tensors.map { it.name() }, model.settings.tokenInputs)
    inputs =
      Array(tensors.size) { i ->
        val tensor = interpreter.getInputTensor(i)
        val values =
          when (roles[i]) {
            TokenInputRole.IDS -> tokens.ids
            TokenInputRole.MASK -> tokens.mask
            TokenInputRole.SEGMENTS -> tokens.segments
          }
        ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder()).apply {
          values.forEach {
            if (tensor.dataType() == DataType.INT64) putLong(it.toLong()) else putInt(it)
          }
          rewind()
        }
      }
  } else {
    require(interpreter.inputTensorCount == 1)
    val input = interpreter.getInputTensor(0)
    require(input.dataType() == DataType.STRING && input.shape().contentEquals(intArrayOf(1))) {
      "Text adapter needs STRING [1] input"
    }
    inputs = arrayOf(arrayOf(text))
  }
  val output = interpreter.getOutputTensor(model.settings.outputIndex)
  if (model.adapter == ModelAdapter.TEXT_STRING) {
    require(
      output.dataType() == DataType.STRING &&
        output.shape().size == 1 &&
        output.numElements() <= 10000
    )
    val target = Array(output.numElements()) { "" }
    interpreter.runForMultipleInputsOutputs(
      inputs,
      mutableMapOf<Int, Any>(model.settings.outputIndex to target),
    )
    val answer = target.joinToString("\n")
    require(answer.length <= 1_000_000)
    return result.copy(text = answer, inputText = text)
  }
  require(
    output.shape().size in 1..2 &&
      (output.shape().size == 1 || output.shape()[0] == 1) &&
      output.numElements() in 1..10000 &&
      output.numBytes() <= 1024 * 1024
  )
  val target = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder())
  interpreter.runForMultipleInputsOutputs(
    inputs,
    mutableMapOf<Int, Any>(model.settings.outputIndex to target),
  )
  val values = scoreValues(readModelValues(output, target), model.settings.scores)
  return result.copy(
    inputText = text,
    inputTruncated = truncated,
    scores =
      values
        .mapIndexed { i, v -> ResultScore(model.labels.getOrNull(i) ?: i.toString(), v) }
        .sortedByDescending { it.score },
  )
}
