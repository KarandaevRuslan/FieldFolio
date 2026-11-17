package com.karandaev.fieldfolio.core.document

import com.squareup.moshi.JsonClass
import java.util.UUID

enum class BlockKind {
  FOLDER,
  HEADING,
  TEXT,
  DOCUMENT,
  TEXT_FILE,
  IMAGE,
  AUDIO,
  LOCATION,
  WEATHER,
  CLOCK,
  MODEL_RESULT,
}

@JsonClass(generateAdapter = true)
data class ReportDocument(
  val version: Int = 1,
  val cover: String? = null,
  val nodes: List<DocumentNode> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class DocumentNode(
  val id: String = UUID.randomUUID().toString(),
  val parentId: String? = null,
  val order: Int = 0,
  val name: String,
  val kind: BlockKind,
  val body: String = "",
  val level: Int = 1,
  val asset: String? = null,
  val mime: String? = null,
  val latitude: Double? = null,
  val longitude: Double? = null,
  val deviceLocation: Boolean = false,
  val zoneId: String? = null,
  val result: ModelResultData? = null,
  val audio: AudioPresentation = AudioPresentation(),
  val displayName: String? = null,
  val hideTitle: Boolean = false,
  val visible: Boolean = true,
  val headingLevel: Int? = null,
  val textOpenMode: TextOpenMode = TextOpenMode.PAGED,
  val syntaxExtension: String? = null,
)

@JsonClass(generateAdapter = true)
data class ModelResultData(
  val id: String = UUID.randomUUID().toString(),
  val modelId: String,
  val modelName: String,
  val provider: String,
  val task: String,
  val createdAt: Long = System.currentTimeMillis(),
  val elapsedMillis: Long = 0,
  val inputAsset: String? = null,
  val maskAsset: String? = null,
  val boxes: List<ResultBox> = emptyList(),
  val layers: List<SegmentationLayer> = emptyList(),
  val scores: List<ResultScore> = emptyList(),
  val values: List<ResultValue> = emptyList(),
  val text: String = "",
  val inputText: String = "",
  val inputTruncated: Boolean = false,
  val inputKind: String? = null,
)

@JsonClass(generateAdapter = true)
data class ResultBox(
  val label: String,
  val confidence: Double,
  val xMin: Double,
  val yMin: Double,
  val xMax: Double,
  val yMax: Double,
)

@JsonClass(generateAdapter = true)
data class ResultScore(val label: String, val score: Double, val color: Long? = null)

@JsonClass(generateAdapter = true)
data class DocumentLocation(
  val nodeId: String,
  val label: String,
  val latitude: Double?,
  val longitude: Double?,
  val device: Boolean = false,
  val parentId: String? = null,
)

fun ReportDocument.resourcePaths(): Set<String> = buildSet {
  cover?.let(::add)
  nodes.forEach { n ->
    n.asset?.let(::add)
    n.audio.coverAsset?.let(::add)
    n.result?.inputAsset?.let(::add)
    n.result?.maskAsset?.let(::add)
    n.result?.layers?.mapNotNull { it.maskAsset }?.forEach(::add)
  }
}

fun ReportDocument.locations(): List<DocumentLocation> =
  nodes
    .filter {
      it.kind in setOf(BlockKind.LOCATION, BlockKind.WEATHER, BlockKind.CLOCK) &&
        (it.kind != BlockKind.CLOCK || !it.deviceLocation) &&
        (it.deviceLocation || it.latitude != null && it.longitude != null)
    }
    .map {
      DocumentLocation(
        it.id,
        it.displayLabel,
        it.latitude,
        it.longitude,
        it.deviceLocation,
        it.parentId,
      )
    }

@JsonClass(generateAdapter = true)
data class ResultValue(val label: String, val value: Double, val unit: String? = null)

@JsonClass(generateAdapter = true)
data class AudioPresentation(
  val showTags: Boolean = true,
  val showCover: Boolean = true,
  val hiddenTags: List<String> = emptyList(),
  val overrides: Map<String, String> = emptyMap(),
  val coverAsset: String? = null,
)

val DocumentNode.displayLabel: String
  get() = displayName?.takeIf { it.isNotBlank() } ?: name

fun ReportDocument.withUniqueNames(): ReportDocument {
  val used = mutableMapOf<String?, MutableList<String>>()
  return copy(
    nodes =
      nodes.map { node ->
        val names = used.getOrPut(node.parentId) { mutableListOf() }
        val name =
          com.karandaev.fieldfolio.core.names.ItemNames.unique(node.name, names, notify = true)
        names += name
        node.copy(
          name = name,
          displayName = com.karandaev.fieldfolio.core.names.ItemNames.display(node.displayName),
        )
      }
  )
}

/** HEADING/TEXT_FILE remain readable for old archives; new text UI edits every variant. */
enum class TextOpenMode {
  PAGED,
  ACODE,
}

val DocumentNode.isTextBlock
  get() = kind in setOf(BlockKind.TEXT, BlockKind.TEXT_FILE, BlockKind.HEADING)
val DocumentNode.effectiveHeadingLevel
  get() = headingLevel ?: level.takeIf { kind == BlockKind.HEADING }
