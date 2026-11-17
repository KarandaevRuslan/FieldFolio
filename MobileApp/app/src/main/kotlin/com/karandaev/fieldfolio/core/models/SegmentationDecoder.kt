package com.karandaev.fieldfolio.core.models

data class DecodedSegmentation(val width: Int, val height: Int, val classes: IntArray)

fun decodeSegmentation(
  values: FloatArray,
  shape: IntArray,
  layout: TensorLayout,
  scores: ScoreMode,
): DecodedSegmentation {
  require(shape.size in 3..4 && shape[0] == 1 && values.all(Float::isFinite))
  val indexed = shape.size == 3
  val channelFirst = !indexed && layout == TensorLayout.NCHW
  val h = shape[if (channelFirst) 2 else 1]
  val w = shape[if (channelFirst) 3 else 2]
  val channels = if (indexed) 0 else shape[if (channelFirst) 1 else 3]
  require(h > 0 && w > 0 && h.toLong() * w <= 4_000_000 && channels in 0..10000)
  require(values.size.toLong() == h.toLong() * w * maxOf(1, channels))
  val result =
    IntArray(h * w) { index ->
      if (indexed) {
        val v = values[index]
        require(v >= 0 && v <= 10000 && kotlin.math.abs(v - kotlin.math.round(v)) < .001f)
        v.toInt()
      } else if (channels == 1) {
        val v = values[index]
        if (scores == ScoreMode.PROBABILITIES) require(v in 0f..1f)
        if (v >= if (scores == ScoreMode.PROBABILITIES) .5f else 0f) 1 else 0
      } else {
        require(channels > 1)
        (0 until channels).maxBy { c ->
          values[if (channelFirst) c * h * w + index else index * channels + c]
        }
      }
    }
  return DecodedSegmentation(w, h, result)
}
