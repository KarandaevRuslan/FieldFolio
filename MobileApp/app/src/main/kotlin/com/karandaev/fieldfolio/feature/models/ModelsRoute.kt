@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.models

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karandaev.fieldfolio.core.di.rememberAppServices
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.*
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.tr
import kotlinx.coroutines.*

@Composable
fun ModelsRoute() {
  val app = rememberAppServices()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val local by app.modelCatalog.models.collectAsStateWithLifecycle()
  val remote by app.serverModels.models.collectAsStateWithLifecycle()
  val choices = remember(local, remote) { catalogChoices(local, remote) }
  var testing by rememberSaveable { mutableStateOf<String?>(null) }
  var configuring by rememberSession { mutableStateOf<LocalModel?>(null) }
  var deleting by rememberSession { mutableStateOf<LocalModel?>(null) }
  var exporting by rememberSession { mutableStateOf<LocalModel?>(null) }
  var issue by rememberSession { mutableStateOf<Throwable?>(null) }
  LaunchedEffect(Unit) { runCatching { app.modelCatalog.refresh() }.onFailure { issue = it } }
  val import =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      app.reportLibrary.externalPicker = false
      if (uri != null)
        scope.launch {
          runCatching {
              app.modelCatalog.importModel(
                readPickedBytes(context, uri, 72 * 1024 * 1024),
                pickedName(context, uri),
              )
            }
            .onFailure { issue = it }
        }
    }
  val export =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
      uri ->
      app.reportLibrary.externalPicker = false
      val model = exporting
      exporting = null
      if (uri != null && model != null)
        scope.launch {
          runCatching {
              app.modelCatalog.export(
                model,
                requireNotNull(context.contentResolver.openOutputStream(uri)),
              )
            }
            .onFailure { issue = it }
        }
    }
  ModelCatalogBrowser(
    choices,
    { testing = "${it.remote}:${it.id}" },
    onImport = {
      app.reportLibrary.externalPicker = true
      import.launch(arrayOf("*/*"))
    },
    management = true,
    actions = { choice ->
      buildList {
        choice.local?.let { model ->
          add(
            ModelMenuAction(tr("export")) {
              exporting = model
              app.reportLibrary.externalPicker = true
              export.launch("${model.name.take(40)}.gimodel")
            }
          )
          if (model.assetPath == null) {
            add(ModelMenuAction(tr("configure")) { configuring = model })
            add(ModelMenuAction(tr("delete")) { deleting = model })
          }
        }
      }
    },
  )
  choices
    .firstOrNull { "${it.remote}:${it.id}" == testing }
    ?.let { choice -> AnalysisWorkspace(choice, onDismiss = { testing = null }, standalone = true) }
  configuring?.let { model ->
    ModelConfigurationDialog(model, { configuring = null }) { next ->
      scope.launch {
        runCatching { app.modelCatalog.configure(next) }
          .onSuccess { configuring = null }
          .onFailure { issue = it }
      }
    }
  }
  deleting?.let { model ->
    SafeAlertDialog(
      { deleting = null },
      title = { Text(tr("delete")) },
      text = { Text(model.name) },
      confirmButton = {
        TextButton({
          scope.launch {
            runCatching { app.modelCatalog.remove(model) }.onFailure { issue = it }
            deleting = null
          }
        }) {
          Text(tr("delete"))
        }
      },
      dismissButton = { TextButton({ deleting = null }) { Text(tr("cancel")) } },
    )
  }
  issue?.let { error ->
    SafeAlertDialog(
      { issue = null },
      title = { Text(tr("error")) },
      text = { Text(error.message.orEmpty()) },
      confirmButton = { TextButton({ issue = null }) { Text(tr("close")) } },
    )
  }
}

@Composable
fun ModelPickerDialog(dismiss: () -> Unit, choose: (ModelChoice) -> Unit) {
  val app = rememberAppServices()
  val local by app.modelCatalog.models.collectAsStateWithLifecycle()
  val remote by app.serverModels.models.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { app.modelCatalog.refresh() }
  SafeAppDialog(dismiss) {
    Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
      androidx.compose.material3.Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
          androidx.compose.material3.TopAppBar(
            title = { Text(tr("choose_model")) },
            navigationIcon = {
              com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton(
                dismiss,
                tooltip = tr("close"),
              ) {
                androidx.compose.material3.Icon(
                  androidx.compose.material.icons.Icons.Default.Close,
                  tr("close"),
                )
              }
            },
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
          )
        },
      ) { pad ->
        androidx.compose.foundation.layout.Box(
          androidx.compose.ui.Modifier.fillMaxSize().padding(pad)
        ) {
          ModelCatalogBrowser(catalogChoices(local, remote), choose)
        }
      }
    }
  }
}
