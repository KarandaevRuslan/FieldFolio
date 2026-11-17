@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.models

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.names.ItemNames
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.components.fields.ResourceNameField
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.*
import com.karandaev.fieldfolio.feature.document.route.*
import com.karandaev.fieldfolio.feature.document.state.*
import com.karandaev.fieldfolio.feature.document.viewer.*
import kotlinx.coroutines.*

@Composable
internal fun ModelConfigurationDialog(
  model: LocalModel,
  dismiss: () -> Unit,
  save: (LocalModel) -> Unit,
) {
  var name by rememberSession(model.id) { mutableStateOf(model.name) }
  var labels by rememberSession(model.id) { mutableStateOf(model.labels.joinToString("\n")) }
  var adapter by rememberSession(model.id) { mutableStateOf(model.adapter) }
  var settings by rememberSession(model.id) { mutableStateOf(model.settings) }
  var output by rememberSession(model.id) { mutableStateOf(model.settings.outputIndex.toString()) }
  var sequence by
    rememberSession(model.id) { mutableStateOf(model.settings.sequenceLength.toString()) }
  var vocabularyError by rememberSession(model.id) { mutableStateOf(false) }
  var editingLabels by rememberSession { mutableStateOf(false) }
  val app = rememberAppServices()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      app.reportLibrary.externalPicker = false
      if (uri != null)
        scope.launch {
          try {
            val bytes = readPickedBytes(context, uri, 8 * 1024 * 1024)
            val vocabulary = withContext(Dispatchers.Default) { decodeVocabulary(bytes) }
            settings = settings.copy(vocabulary = vocabulary)
            vocabularyError = false
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            vocabularyError = true
          }
        }
    }
  val close =
    rememberUnsavedClose(
      name != model.name ||
        labels != model.labels.joinToString("\n") ||
        adapter != model.adapter ||
        settings != model.settings ||
        output != model.settings.outputIndex.toString() ||
        sequence != model.settings.sequenceLength.toString(),
      dismiss,
    )
  val parsedOutput = output.toIntOrNull()?.takeIf { it in 0..15 }
  val parsedSequence = sequence.toIntOrNull()?.takeIf { it in 2..4096 }
  val labelState =
    produceState<Pair<String, List<String>>?>(null, labels) {
      value =
        withContext(Dispatchers.Default) {
          labels to labels.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }
  val parsedLabels = labelState.value?.takeIf { it.first == labels }?.second
  val valid =
    ItemNames.valid(name) &&
      parsedOutput != null &&
      (adapter != ModelAdapter.TEXT_WORDPIECE ||
        parsedSequence != null && settings.vocabulary.isNotEmpty()) &&
      parsedLabels != null &&
      parsedLabels.size <= 10000 &&
      parsedLabels.all { it.length <= 200 } &&
      (adapter != ModelAdapter.YOLO_RAW || parsedLabels.isNotEmpty())
  AdaptiveFormDialog(
    tr("configure"),
    close,
    actions = {
      HintIconButton(
        {
          save(
            model.copy(
              name = name.trim(),
              adapter = adapter,
              labels = parsedLabels!!,
              settings =
                settings.copy(
                  outputIndex = parsedOutput!!,
                  sequenceLength = parsedSequence ?: model.settings.sequenceLength,
                ),
            )
          )
        },
        enabled = valid,
        tooltip = tr("save"),
      ) {
        Icon(Icons.Default.Save, tr("save"))
      }
    },
    sharedScroll = true,
    left = {
      ResourceNameField(name, { name = it }, label = tr("name"))
      ModelOption(tr("adapter"), adapter, ModelAdapter.entries, { adapterLabel(it) }) {
        adapter = it
      }
      OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            stringResource(R.string.finish_model_tensors),
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            "${tr("model_input")}: ${model.inputType} ${model.inputShape}",
            style = MaterialTheme.typography.bodySmall,
          )
          Text(
            "${tr("output")}: ${model.outputType} ${model.outputShape}",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
      if (adapter.isImage) {
        ModelOption(tr("image_range"), settings.imageRange, ImageRange.entries) {
          settings = settings.copy(imageRange = it)
        }
        ModelOption(tr("input_layout"), settings.inputLayout, TensorLayout.entries) {
          settings = settings.copy(inputLayout = it)
        }
      }
      if (adapter.isNumeric)
        Text(stringResource(R.string.finish_numeric_hint, model.inputShape.toString()))
      if (adapter == ModelAdapter.TEXT_WORDPIECE) {
        Text(tr("wordpiece_help"), style = MaterialTheme.typography.bodySmall)
        ListItem(
          headlineContent = { Text(tr("vocabulary")) },
          supportingContent = { Text(settings.vocabulary.size.toString()) },
          trailingContent = {
            HintIconButton(
              {
                app.reportLibrary.externalPicker = true
                picker.launch(arrayOf("*/*"))
              },
              tooltip = tr("import_vocabulary"),
            ) {
              Icon(Icons.Default.FileOpen, tr("import_vocabulary"))
            }
          },
        )
        if (vocabularyError) Text(tr("vocabulary_error"), color = MaterialTheme.colorScheme.error)
        ListItem(
          headlineContent = { Text(tr("lowercase")) },
          trailingContent = {
            Switch(settings.lowercase, { settings = settings.copy(lowercase = it) })
          },
        )
        OutlinedTextField(
          sequence,
          { sequence = it.take(4) },
          Modifier.fillMaxWidth(),
          singleLine = true,
          label = { Text(tr("sequence_length")) },
          isError = parsedSequence == null,
        )
        model.inputNames.forEachIndexed { i, inputName ->
          val guessed = runCatching { tokenInputRoles(model.inputNames, emptyList()) }.getOrNull()
          val configured = settings.tokenInputs.takeIf { it.size == model.inputNames.size }
          val roles =
            configured
              ?: guessed
              ?: model.inputNames.mapIndexed { n, _ ->
                TokenInputRole.entries.getOrElse(n) { TokenInputRole.IDS }
              }
          ModelOption(inputName, roles[i], TokenInputRole.entries) { next ->
            settings = settings.copy(tokenInputs = roles.toMutableList().also { it[i] = next })
          }
        }
      }
    },
    right = {
      OutlinedTextField(
        output,
        { output = it.take(3) },
        Modifier.fillMaxWidth().testTag("model-output-index"),
        singleLine = true,
        label = { Text(tr("output_index")) },
        isError = parsedOutput == null,
      )
      if (
        adapter !in
          setOf(ModelAdapter.REGRESSION, ModelAdapter.NUMERIC_REGRESSION, ModelAdapter.TEXT_STRING)
      )
        ModelOption(tr("score_mode"), settings.scores, ScoreMode.entries) {
          settings = settings.copy(scores = it)
        }
      if (adapter == ModelAdapter.SEGMENTATION) {
        ModelOption(tr("output_layout"), settings.outputLayout, TensorLayout.entries) {
          settings = settings.copy(outputLayout = it)
        }
        Text(
          stringResource(R.string.finish_segmentation_help),
          style = MaterialTheme.typography.bodySmall,
        )
      }
      if (adapter in setOf(ModelAdapter.REGRESSION, ModelAdapter.NUMERIC_REGRESSION))
        OutlinedTextField(
          settings.regressionUnits.joinToString("\n"),
          { settings = settings.copy(regressionUnits = it.take(20000).lines()) },
          Modifier.fillMaxWidth(),
          label = { Text(tr("regression_units")) },
          minLines = 2,
          maxLines = 6,
        )
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
      ) {
        Text(tr("labels"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        HintIconButton({ editingLabels = true }, tooltip = tr("edit_text")) {
          Icon(Icons.Default.Code, tr("edit_text"))
        }
      }
      if (labels.length <= 65536)
        OutlinedTextField(
          labels,
          { labels = it.take(1_000_000) },
          Modifier.fillMaxWidth(),
          label = { Text(tr("labels")) },
          minLines = 4,
          maxLines = 8,
        )
      else DocumentText(labels, tr("labels"))
    },
  )
  if (editingLabels)
    AcodeBlockEditor(
      DocumentNode(
        id = "model-labels-${model.id}",
        name = "labels.txt",
        kind = BlockKind.TEXT,
        body = labels,
      ),
      emptyMap(),
      { editingLabels = false },
      allowRename = false,
    ) { next, _ ->
      labels = next.body
      editingLabels = false
    }
}

@Composable
internal fun adapterLabel(adapter: ModelAdapter): String =
  stringResource(
    when (adapter) {
      ModelAdapter.DETECTION_NMS -> R.string.finish_adapter_detection
      ModelAdapter.YOLO_RAW -> R.string.finish_adapter_yolo
      ModelAdapter.CLASSIFICATION -> R.string.finish_adapter_classification
      ModelAdapter.REGRESSION -> R.string.finish_adapter_regression
      ModelAdapter.SEGMENTATION -> R.string.finish_adapter_segmentation
      ModelAdapter.TEXT_STRING -> R.string.finish_adapter_text
      ModelAdapter.TEXT_CLASSIFICATION -> R.string.finish_adapter_text_classification
      ModelAdapter.TEXT_WORDPIECE -> R.string.finish_adapter_tokens
      ModelAdapter.NUMERIC_CLASSIFICATION -> R.string.finish_adapter_numeric_classification
      ModelAdapter.NUMERIC_REGRESSION -> R.string.finish_adapter_numeric_regression
    }
  )

@Composable
private fun <T> ModelOption(
  label: String,
  value: T,
  values: List<T>,
  title: @Composable (T) -> String = { it.toString() },
  select: (T) -> Unit,
) {
  var open by rememberSession { mutableStateOf(false) }
  ExposedDropdownMenuBox(open, { open = !open }, Modifier.fillMaxWidth()) {
    OutlinedTextField(
      title(value),
      {},
      Modifier.fillMaxWidth().menuAnchor(),
      readOnly = true,
      singleLine = true,
      label = { Text(label) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
    )
    ExposedDropdownMenu(open, { open = false }) {
      values.forEach { item ->
        DropdownMenuItem(
          text = { Text(title(item)) },
          onClick = {
            open = false
            select(item)
          },
        )
      }
    }
  }
}
