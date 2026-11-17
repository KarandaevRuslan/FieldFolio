package com.karandaev.fieldfolio.feature.document.state

import androidx.compose.runtime.*
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import java.util.UUID
import kotlinx.coroutines.*

internal fun DocumentViewModel.performStageModelProperties(node: DocumentNode) {
  modelDraft = node
}

internal fun DocumentViewModel.performDiscardModelDraft() {
  modelFiles.values.forEach { it.fill(0) }
  modelFiles = emptyMap()
  modelDraft = null
}

internal suspend fun DocumentViewModel.performStageAnalysisResult(
  execution: ModelExecution,
  image: ByteArray?,
) {
  val draft = modelDraft ?: return
  try {
    val path = image?.let { "resources/doc/${UUID.randomUUID()}.img" }
    val staged =
      withContext(Dispatchers.Default) {
        execution.files.mapValues { it.value.copyOf() } +
          (if (path != null) mapOf(path to image!!.copyOf()) else emptyMap())
      }
    require(
      files.values.sumOf { it.size.toLong() } + staged.values.sumOf { it.size.toLong() } <=
        48L * 1024 * 1024
    )
    modelFiles.values.forEach { it.fill(0) }
    modelFiles = staged
    modelDraft =
      draft.copy(result = execution.result.copy(inputAsset = path ?: execution.result.inputAsset))
  } catch (e: CancellationException) {
    throw e
  } catch (e: Throwable) {
    error = e
  }
}

internal fun DocumentViewModel.performCommitModelDraft(node: DocumentNode) = action {
  val next = tree.update(node)
  val combined = files + modelFiles
  val referenced = next.resourcePaths()
  require(
    combined.filterKeys { it in referenced }.values.sumOf { it.size.toLong() } <= 48L * 1024 * 1024
  )
  document = next
  files = combined.filterKeys { it in referenced }
  modelFiles = emptyMap()
  modelDraft = null
  dirty = true
}
