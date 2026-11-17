package com.karandaev.fieldfolio.core.models

import com.squareup.moshi.JsonClass
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Supported local tensor adapters; server models use the separate registry. */
enum class ModelAdapter {
  DETECTION_NMS,
  YOLO_RAW,
  CLASSIFICATION,
  REGRESSION,
  SEGMENTATION,
  TEXT_STRING,
  TEXT_CLASSIFICATION,
  TEXT_WORDPIECE,
  NUMERIC_CLASSIFICATION,
  NUMERIC_REGRESSION,
}

@JsonClass(generateAdapter = true)
data class LocalModel(
  val id: String,
  val name: String,
  val adapter: ModelAdapter,
  val fileName: String? = null,
  val assetPath: String? = null,
  val labels: List<String> = emptyList(),
  val inputShape: List<Int> = emptyList(),
  val outputShape: List<Int> = emptyList(),
  val inputType: String = "",
  val outputType: String = "",
  val importedAt: Long = System.currentTimeMillis(),
  val settings: ModelSettings = ModelSettings(),
  val inputNames: List<String> = emptyList(),
  val legacySourcePath: String? = null,
  val legacySourceAsset: Boolean = false,
) {
  val task: String
    get() =
      when (adapter) {
        ModelAdapter.DETECTION_NMS,
        ModelAdapter.YOLO_RAW -> "DETECTION"
        ModelAdapter.CLASSIFICATION,
        ModelAdapter.NUMERIC_CLASSIFICATION -> "CLASSIFICATION"
        ModelAdapter.REGRESSION,
        ModelAdapter.NUMERIC_REGRESSION -> "REGRESSION"
        ModelAdapter.SEGMENTATION -> "SEGMENTATION"
        ModelAdapter.TEXT_STRING,
        ModelAdapter.TEXT_CLASSIFICATION,
        ModelAdapter.TEXT_WORDPIECE -> "TEXT"
      }
}
