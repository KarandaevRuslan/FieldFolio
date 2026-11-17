package com.karandaev.fieldfolio.core.models

import android.content.Context
import android.util.AtomicFile
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/**
 * Local binaries are separate from report packets; results retain model snapshots, not
 * dependencies.
 */
class ModelCatalog(
  private val context: Context,
  moshi: Moshi,
  private val legacyDao: com.karandaev.fieldfolio.core.data.local.mlmodel.InspectionModelDao? = null,
) {
  private val directory = File(context.filesDir, "model_catalog").apply { mkdirs() }
  private val file = AtomicFile(File(directory, "catalog.json"))
  private val adapter =
    moshi.adapter<List<LocalModel>>(
      Types.newParameterizedType(List::class.java, LocalModel::class.java)
    )
  private val stateJson = moshi.adapter(ModelCatalogState::class.java)
  private var migrated = emptySet<Long>()
  private val mutex = Mutex()
  private val seed =
    LocalModel(
      "road-local",
      "Road damage · YOLO11s",
      ModelAdapter.DETECTION_NMS,
      assetPath = "models/road_damage_yolo_int8.tflite",
      labels = listOf("D00", "D10", "D20", "D40", "D44", "D50", "Repair"),
      inputShape = listOf(1, 640, 640, 3),
      outputShape = listOf(1, 300, 6),
      inputType = "FLOAT32",
      outputType = "FLOAT32",
      importedAt = 0,
    )
  private val _models = MutableStateFlow<List<LocalModel>>(emptyList())
  val models: StateFlow<List<LocalModel>> = _models.asStateFlow()

  suspend fun refresh() = withContext(Dispatchers.IO) { mutex.withLock { refreshLocked() } }

  private suspend fun refreshLocked() {
    val saved =
      if (file.baseFile.exists()) {
        val json = file.openRead().use { it.readBytes().toString(Charsets.UTF_8) }
        if (json.trimStart().startsWith("[")) adapter.fromJson(json).orEmpty()
        else
          requireNotNull(stateJson.fromJson(json))
            .also {
              require(it.version == 2)
              migrated = it.migratedLegacyIds.toSet()
            }
            .models
      } else emptyList()
    saved.forEach {
      require(it.id.matches(Regex("[a-zA-Z0-9-]{1,80}")) && it.fileName == "${it.id}.tflite")
      validate(it)
    }
    val all = saved.toMutableList()
    var changed = false
    legacyDao?.observeModels()?.first().orEmpty().forEach { old ->
      if (old.id !in migrated) {
        if (old.sourcePath != seed.assetPath || old.sourceType.name != "ASSET") {
          val id = "legacy-${old.id}"
          if (all.none { it.id == id })
            all +=
              LocalModel(
                id,
                old.name,
                if (old.resultType.name == "SEGMENTATION") ModelAdapter.SEGMENTATION
                else ModelAdapter.YOLO_RAW,
                fileName = "$id.tflite",
                inputShape = listOf(1, old.inputHeight, old.inputWidth, 3),
                importedAt = old.createdAtMillis,
                legacySourcePath = old.sourcePath,
                legacySourceAsset = old.sourceType.name == "ASSET",
              )
        }
        migrated = migrated + old.id
        changed = true
      }
    }
    val recovered =
      all.map { model ->
        if (model.legacySourcePath != null) {
          val target = File(directory, model.fileName!!)
          val copied =
            runCatching {
                val source =
                  if (model.legacySourceAsset) context.assets.open(model.legacySourcePath)
                  else File(model.legacySourcePath).inputStream()
                val data = source.use { bounded(it, 64 * 1024 * 1024) }
                require(
                  data.size >= 8 && data.copyOfRange(4, 8).contentEquals("TFL3".toByteArray())
                ) {
                  "Not a TFLite model"
                }
                val atomic = AtomicFile(target)
                val output = atomic.startWrite()
                try {
                  output.write(data)
                  atomic.finishWrite(output)
                } catch (e: Throwable) {
                  atomic.failWrite(output)
                  throw e
                }
                true
              }
              .getOrDefault(false)
          if (copied) {
            changed = true
            val metadata =
              runCatching {
                  ZipFile(target).use { zip ->
                    zip.getEntry("metadata.json")?.let { entry ->
                      JSONObject(
                        zip.getInputStream(entry).use {
                          bounded(it, 65536).toString(Charsets.UTF_8)
                        }
                      )
                    }
                  }
                }
                .getOrNull()
            val labels =
              metadata
                ?.optJSONObject("names")
                ?.let { names ->
                  names
                    .keys()
                    .asSequence()
                    .toList()
                    .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
                    .map { names.getString(it) }
                }
                .orEmpty()
            model.copy(
              legacySourcePath = null,
              labels = labels.ifEmpty { model.labels },
              adapter =
                if (metadata?.optString("task") == "detect") {
                  if (metadata.optJSONObject("args")?.optBoolean("nms") == true)
                    ModelAdapter.DETECTION_NMS
                  else ModelAdapter.YOLO_RAW
                } else model.adapter,
            )
          } else model
        } else model
      }
    publishModels(recovered, changed)
  }

  fun loadBuffer(model: LocalModel): java.nio.MappedByteBuffer =
    if (model.assetPath != null) {
      context.assets.openFd(model.assetPath).use { a ->
        FileInputStream(a.fileDescriptor).channel.use {
          it.map(FileChannel.MapMode.READ_ONLY, a.startOffset, a.declaredLength)
        }
      }
    } else {
      require(model.fileName == "${model.id}.tflite")
      val target = File(directory, model.fileName!!)
      require(target.isFile) { "Model file is unavailable. Re-import the model to restore it." }
      FileInputStream(target).channel.use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) }
    }

  /**
   * Validates and imports owned model bytes on IO, then atomically publishes the normalized
   * catalog.
   */
  suspend fun importModel(bytes: ByteArray, name: String): LocalModel =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        refreshLocked()
        require(bytes.size <= 72 * 1024 * 1024)
        var binary = bytes
        var supplied: LocalModel? = null
        if (bytes.size > 4 && bytes[0] == 80.toByte() && bytes[1] == 75.toByte()) {
          val parts = mutableMapOf<String, ByteArray>()
          ZipInputStream(bytes.inputStream()).use { z ->
            while (true) {
              val e = z.nextEntry ?: break
              require(
                !e.isDirectory &&
                  e.name in setOf("model.tflite", "model.json", "vocab.txt") &&
                  e.name !in parts
              )
              parts[e.name] =
                bounded(z, if (e.name == "model.tflite") 64 * 1024 * 1024 else 8 * 1024 * 1024)
            }
          }
          binary = requireNotNull(parts["model.tflite"])
          parts["model.json"]?.let {
            require(it.size <= 8 * 1024 * 1024)
            supplied = modelJson.fromJson(it.toString(Charsets.UTF_8))
          }
          parts["vocab.txt"]?.let { vocab ->
            val lines = decodeVocabulary(vocab)
            supplied =
              (supplied ?: LocalModel("package", name, ModelAdapter.TEXT_WORDPIECE)).let {
                it.copy(settings = it.settings.copy(vocabulary = lines))
              }
          }
        }
        require(
          binary.size in 8..64 * 1024 * 1024 &&
            binary.copyOfRange(4, 8).contentEquals("TFL3".toByteArray())
        ) {
          "Not a TensorFlow Lite model"
        }
        val id = UUID.randomUUID().toString()
        val target = File(directory, "$id.tflite")
        try {
          target.writeBytes(binary)
          val metadata =
            runCatching {
                ZipFile(target).use { z ->
                  z.getEntry("metadata.json")?.let { e ->
                    require(e.size <= 65536)
                    JSONObject(
                      z.getInputStream(e).use { bounded(it, 65536).toString(Charsets.UTF_8) }
                    )
                  }
                }
              }
              .getOrNull()
          val inspected =
            Interpreter(
                loadBuffer(LocalModel(id, name, ModelAdapter.DETECTION_NMS, fileName = target.name))
              )
              .use { interpreter ->
                require(
                  interpreter.inputTensorCount in 1..3 && interpreter.outputTensorCount in 1..16
                )
                val outputIndex = supplied?.settings?.outputIndex ?: 0
                require(outputIndex in 0 until interpreter.outputTensorCount)
                val input = interpreter.getInputTensor(0)
                val output = interpreter.getOutputTensor(outputIndex)
                val shape = output.shape().toList()
                val type =
                  when {
                    input.dataType() == DataType.STRING && output.dataType() == DataType.STRING ->
                      ModelAdapter.TEXT_STRING
                    input.dataType() == DataType.STRING -> ModelAdapter.TEXT_CLASSIFICATION
                    input.shape().size == 2 &&
                      input.dataType() in setOf(DataType.INT32, DataType.INT64) &&
                      (interpreter.inputTensorCount > 1 ||
                        Regex("(?i)(token|input_ids|attention|segment)")
                          .containsMatchIn(input.name())) -> ModelAdapter.TEXT_WORDPIECE
                    input.shape().size <= 2 -> ModelAdapter.NUMERIC_REGRESSION
                    metadata?.optString("task") == "detect" ->
                      if (metadata.optJSONObject("args")?.optBoolean("nms") == true)
                        ModelAdapter.DETECTION_NMS
                      else ModelAdapter.YOLO_RAW
                    shape.size == 2 || shape.size == 4 && shape[1] == 1 && shape[2] == 1 ->
                      ModelAdapter.CLASSIFICATION
                    shape.size == 4 -> ModelAdapter.SEGMENTATION
                    shape.size == 3 && shape.last() == 6 -> ModelAdapter.DETECTION_NMS
                    else -> ModelAdapter.YOLO_RAW
                  }
                val labels =
                  metadata
                    ?.optJSONObject("names")
                    ?.let { obj ->
                      obj
                        .keys()
                        .asSequence()
                        .toList()
                        .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
                        .map { obj.getString(it) }
                    }
                    .orEmpty()
                LocalModel(
                  id,
                  (supplied?.name ?: name.substringBeforeLast('.')).take(120).ifBlank { "Model" },
                  supplied?.adapter ?: type,
                  fileName = target.name,
                  labels = supplied?.labels ?: labels,
                  inputShape = input.shape().toList(),
                  outputShape = shape,
                  inputType = input.dataType().name,
                  outputType = output.dataType().name,
                  settings =
                    supplied?.settings
                      ?: ModelSettings(
                        scores =
                          if (
                            type in
                              setOf(ModelAdapter.TEXT_WORDPIECE, ModelAdapter.TEXT_CLASSIFICATION)
                          )
                            ScoreMode.SOFTMAX
                          else ScoreMode.PROBABILITIES
                      ),
                  inputNames =
                    (0 until interpreter.inputTensorCount).map {
                      interpreter.getInputTensor(it).name()
                    },
                )
              }
          validate(inspected)
          val all = _models.value.filter { it.assetPath == null } + inspected
          publishModels(all).last()
        } catch (e: Throwable) {
          target.delete()
          throw e
        }
      }
    }

  private val modelJson = moshi.adapter(LocalModel::class.java)

  suspend fun configure(model: LocalModel) =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        require(model.assetPath == null)
        validate(model)
        val current = _models.value.first { it.id == model.id }
        require(current.assetPath == null)
        require(current.fileName == model.fileName)
        val all =
          _models.value.filter { it.assetPath == null }.map { if (it.id == model.id) model else it }
        publishModels(all)
        Unit
      }
    }

  suspend fun remove(model: LocalModel) =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        require(model.assetPath == null)
        val all = _models.value.filter { it.assetPath == null && it.id != model.id }
        publishModels(all)
        File(directory, "${model.id}.tflite").delete()
        Unit
      }
    }

  suspend fun export(model: LocalModel, output: java.io.OutputStream) =
    withContext(Dispatchers.IO) {
      ZipOutputStream(output).use { z ->
        z.putNextEntry(ZipEntry("model.json"))
        z.write(
          modelJson
            .toJson(
              model.copy(
                settings = model.settings.copy(vocabulary = emptyList()),
                legacySourcePath = null,
              )
            )
            .toByteArray()
        )
        z.closeEntry()
        z.putNextEntry(ZipEntry("model.tflite"))
        if (model.assetPath != null) context.assets.open(model.assetPath).use { it.copyTo(z) }
        else File(directory, "${model.id}.tflite").inputStream().use { it.copyTo(z) }
        z.closeEntry()
        if (model.settings.vocabulary.isNotEmpty()) {
          z.putNextEntry(ZipEntry("vocab.txt"))
          z.write(model.settings.vocabulary.joinToString("\n").toByteArray())
          z.closeEntry()
        }
      }
    }

  private fun validate(m: LocalModel) {
    require(
      com.karandaev.fieldfolio.core.names.ItemNames.valid(m.name) &&
        m.name.length <= 120 &&
        m.labels.size <= 10000 &&
        m.labels.all { it.length <= 200 }
    )
    require(m.settings.sequenceLength in 2..4096 && m.settings.outputIndex in 0..15)
    require(
      m.settings.vocabulary.size <= 250000 && m.settings.vocabulary.all { it.length in 1..1000 }
    )
    require(m.settings.tokenInputs.size <= 3)
  }

  private fun publishModels(all: List<LocalModel>, forceWrite: Boolean = true): List<LocalModel> {
    require(all.map { it.id }.distinct().size == all.size && all.none { it.id == seed.id })
    val used = mutableListOf(seed.name)
    val normalized =
      all.map { model ->
        val name = com.karandaev.fieldfolio.core.names.ItemNames.unique(model.name, used)
        used += name
        model.copy(name = name)
      }
    if (forceWrite || normalized != all) write(normalized)
    _models.value = listOf(seed) + normalized
    all.zip(normalized).forEach { (old, next) ->
      com.karandaev.fieldfolio.core.names.NameResolutionEvents.record(old.name, next.name)
    }
    return normalized
  }

  private fun write(all: List<LocalModel>) {
    val out = file.startWrite()
    try {
      out.write(
        stateJson
          .toJson(ModelCatalogState(models = all, migratedLegacyIds = migrated.toList()))
          .toByteArray()
      )
      file.finishWrite(out)
    } catch (e: Throwable) {
      file.failWrite(out)
      throw e
    }
  }

  private fun bounded(input: java.io.InputStream, max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val b = ByteArray(8192)
    while (true) {
      val n = input.read(b)
      if (n < 0) break
      require(out.size().toLong() + n <= max)
      out.write(b, 0, n)
    }
    return out.toByteArray()
  }
}

@JsonClass(generateAdapter = true)
data class ModelCatalogState(
  val version: Int = 2,
  val models: List<LocalModel>,
  val migratedLegacyIds: List<Long> = emptyList(),
)

fun decodeVocabulary(bytes: ByteArray): List<String> {
  require(bytes.size <= 8 * 1024 * 1024)
  val text =
    Charsets.UTF_8.newDecoder()
      .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
      .decode(java.nio.ByteBuffer.wrap(bytes))
      .toString()
      .removePrefix("\uFEFF")
  return text
    .lineSequence()
    .map { it.trimEnd('\r') }
    .toList()
    .let { if (it.lastOrNull() == "") it.dropLast(1) else it }
    .also {
      require(
        it.size in 4..250000 &&
          it.all { token -> token.isNotEmpty() && token.length <= 1000 } &&
          it.distinct().size == it.size
      )
    }
}
