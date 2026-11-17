package com.karandaev.fieldfolio.core.models

import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.inspectionapi.InspectionProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Server adapters are supplied by the app, never imported from the catalog UI. */
enum class ModelInputKind {
  IMAGE,
  TEXT,
  NUMBERS,
}

interface ServerModelAdapter {
  val inputKind: ModelInputKind
    get() = if (task == "TEXT") ModelInputKind.TEXT else ModelInputKind.IMAGE

  val id: String
  val name: String
  val task: String

  suspend fun analyze(
    provider: InspectionProvider,
    image: ByteArray?,
    text: String,
  ): ModelResultData
}

class ServerModelRegistry(adapters: List<ServerModelAdapter> = listOf(RoadDamageServerAdapter)) {
  private val state =
    MutableStateFlow(
      adapters.also {
        require(it.map { a -> a.id }.distinct().size == it.size)
        require(
          it.all { a -> com.karandaev.fieldfolio.core.names.ItemNames.valid(a.name) } &&
            it
              .map { a -> com.karandaev.fieldfolio.core.names.ItemNames.key(a.name) }
              .distinct()
              .size == it.size
        )
      }
    )
  val models: StateFlow<List<ServerModelAdapter>> = state

  fun register(adapter: ServerModelAdapter) {
    require(adapter.id.isNotBlank())
    require(com.karandaev.fieldfolio.core.names.ItemNames.valid(adapter.name))
    require(
      state.value.none {
        it.id != adapter.id &&
          com.karandaev.fieldfolio.core.names.ItemNames.key(it.name) ==
            com.karandaev.fieldfolio.core.names.ItemNames.key(adapter.name)
      }
    )
    state.value = state.value.filterNot { it.id == adapter.id } + adapter
  }

  fun get(id: String) =
    requireNotNull(state.value.firstOrNull { it.id == id }) { "Server model is unavailable" }
}

object RoadDamageServerAdapter : ServerModelAdapter {
  override val id = "road-server"
  override val name = "Road damage"
  override val task = "DETECTION"

  override suspend fun analyze(
    provider: InspectionProvider,
    image: ByteArray?,
    text: String,
  ): ModelResultData {
    val bitmap = decodeModelImage(requireNotNull(image), 2048)
    val bytes =
      java.io
        .ByteArrayOutputStream()
        .also { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        .toByteArray()
    bitmap.recycle()
    val response = provider.detectBytes(bytes).getOrThrow()
    return ModelResultData(
      modelId = id,
      modelName = name,
      provider = "SERVER",
      task = task,
      boxes =
        response.detections.map {
          ResultBox(it.clazz, it.confidence, it.bbox.xMin, it.bbox.yMin, it.bbox.xMax, it.bbox.yMax)
        },
    )
  }
}

data class ModelChoice(
  val id: String,
  val name: String,
  val task: String,
  val local: LocalModel? = null,
  val server: ServerModelAdapter? = null,
  val createdAt: Long = 0,
) {
  val remote
    get() = server != null

  val numericInput
    get() = local?.adapter?.isNumeric == true || server?.inputKind == ModelInputKind.NUMBERS

  val textInput
    get() =
      local?.adapter?.let { it.isText || it.isNumeric } == true ||
        numericInput ||
        server?.inputKind == ModelInputKind.TEXT ||
        task == "TEXT"
}

enum class ModelSort {
  NAME_ASC,
  NAME_DESC,
  NEWEST,
  OLDEST,
}

fun catalogChoices(local: List<LocalModel>, server: List<ServerModelAdapter>) =
  local.map { ModelChoice(it.id, it.name, it.task, local = it, createdAt = it.importedAt) } +
    server.map { ModelChoice(it.id, it.name, it.task, server = it) }

fun queryModels(
  models: List<ModelChoice>,
  remote: Boolean,
  query: String,
  task: String?,
  sort: ModelSort,
): List<ModelChoice> {
  val selected =
    models.filter {
      it.remote == remote &&
        (task == null || it.task == task) &&
        (it.name.contains(query, true) || it.task.contains(query, true))
    }
  val name = compareBy<ModelChoice> { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.id }
  return selected.sortedWith(
    when (sort) {
      ModelSort.NAME_ASC -> name
      ModelSort.NAME_DESC -> name.reversed()
      ModelSort.NEWEST -> compareByDescending<ModelChoice> { it.createdAt }.then(name)
      ModelSort.OLDEST -> compareBy<ModelChoice> { it.createdAt }.then(name)
    }
  )
}
