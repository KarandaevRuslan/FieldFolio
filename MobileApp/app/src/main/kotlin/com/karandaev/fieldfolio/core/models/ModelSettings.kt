package com.karandaev.fieldfolio.core.models

import com.squareup.moshi.JsonClass
import kotlin.math.exp

enum class ImageRange {
  ZERO_ONE,
  MINUS_ONE_ONE,
  RGB_255,
}

enum class TensorLayout {
  NHWC,
  NCHW,
}

enum class ScoreMode {
  PROBABILITIES,
  SOFTMAX,
  SIGMOID,
}

enum class TokenInputRole {
  IDS,
  MASK,
  SEGMENTS,
}

@JsonClass(generateAdapter = true)
data class ModelSettings(
  val imageRange: ImageRange = ImageRange.ZERO_ONE,
  val inputLayout: TensorLayout = TensorLayout.NHWC,
  val outputLayout: TensorLayout = TensorLayout.NHWC,
  val scores: ScoreMode = ScoreMode.PROBABILITIES,
  val vocabulary: List<String> = emptyList(),
  val lowercase: Boolean = true,
  val tokenInputs: List<TokenInputRole> = emptyList(),
  val sequenceLength: Int = 128,
  val outputIndex: Int = 0,
  val regressionUnits: List<String> = emptyList(),
)

fun scoreValues(values: FloatArray, mode: ScoreMode): List<Double> {
  require(values.isNotEmpty() && values.all(Float::isFinite))
  return when (mode) {
    ScoreMode.PROBABILITIES -> {
      require(values.all { it in 0f..1f }) {
        "Output is not probabilities; select Softmax or Sigmoid in model settings"
      }
      values.map { it.toDouble() }
    }
    ScoreMode.SOFTMAX -> {
      val max = values.max()
      val ex = values.map { exp((it - max).toDouble()) }
      val sum = ex.sum()
      ex.map { it / sum }
    }
    ScoreMode.SIGMOID ->
      values.map { x ->
        if (x >= 0) 1.0 / (1 + exp(-x.toDouble())) else exp(x.toDouble()) / (1 + exp(x.toDouble()))
      }
  }
}

val ModelAdapter.isText: Boolean
  get() =
    this in
      setOf(ModelAdapter.TEXT_STRING, ModelAdapter.TEXT_CLASSIFICATION, ModelAdapter.TEXT_WORDPIECE)

val ModelAdapter.isNumeric: Boolean
  get() = this in setOf(ModelAdapter.NUMERIC_CLASSIFICATION, ModelAdapter.NUMERIC_REGRESSION)
val ModelAdapter.isImage: Boolean
  get() = !isText && !isNumeric
