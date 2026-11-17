package com.karandaev.fieldfolio.core.inspectionapi.tflite

/**
 * Converts raw YOLO TFLite output tensor into a list of candidates.
 *
 * Supported layouts:
 * - `[1, candidateCount, featureCount]`
 * - `[1, featureCount, candidateCount]`
 *
 * Where `featureCount = 4 + classCount`.
 *
 * @param expectedFeatureCount Expected number of values per candidate.
 * @return Flat list of YOLO candidates.
 * @throws IllegalArgumentException if output tensor is empty or malformed.
 * @throws IllegalStateException if output tensor shape is unsupported.
 */
internal fun Array<Array<FloatArray>>.toYoloCandidates(
  expectedFeatureCount: Int
): List<YoloCandidate> {
  require(isNotEmpty()) { "TFLite output is empty." }

  val batch = this[0]

  require(batch.isNotEmpty()) { "TFLite output batch is empty." }

  val firstRowSize = batch[0].size

  require(firstRowSize > 0) { "TFLite output row is empty." }

  require(batch.all { row: FloatArray -> row.size == firstRowSize }) {
    "TFLite output rows have different sizes."
  }

  val firstDim = batch.size
  val secondDim = firstRowSize

  val candidateValues =
    when {
      secondDim == expectedFeatureCount -> {
        batch.toList()
      }

      firstDim == expectedFeatureCount -> {
        batch.transpose()
      }

      else -> {
        error(
          "Unsupported YOLO output shape: [1, $firstDim, $secondDim]. " +
            "Expected feature count=$expectedFeatureCount."
        )
      }
    }

  return candidateValues.map { values: FloatArray -> YoloCandidate(values = values) }
}

/**
 * Transposes a 2D float array.
 *
 * Used for YOLO output layout: `[1, featureCount, candidateCount]`
 *
 * into: `[candidateCount, featureCount]`.
 */
private fun Array<FloatArray>.transpose(): List<FloatArray> {
  val rowCount = size
  val columnCount = this[0].size

  return List(columnCount) { columnIndex: Int ->
    FloatArray(rowCount) { rowIndex: Int -> this[rowIndex][columnIndex] }
  }
}
