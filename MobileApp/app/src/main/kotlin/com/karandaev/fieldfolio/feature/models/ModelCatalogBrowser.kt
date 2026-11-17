package com.karandaev.fieldfolio.feature.models

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.tr

@Composable
fun ModelCatalogBrowser(
  choices: List<ModelChoice>,
  onChoose: (ModelChoice) -> Unit,
  onImport: (() -> Unit)? = null,
  management: Boolean = false,
  actions: @Composable (ModelChoice) -> List<ModelMenuAction> = { emptyList() },
) {
  var remote by rememberSession { mutableStateOf(false) }
  var query by rememberSession { mutableStateOf("") }
  var task by rememberSession { mutableStateOf<String?>(null) }
  var sort by rememberSession { mutableStateOf(ModelSort.NAME_ASC) }
  var filtering by rememberSession { mutableStateOf(false) }
  var sorting by rememberSession { mutableStateOf(false) }
  val visible =
    remember(choices, remote, query, task, sort) { queryModels(choices, remote, query, task, sort) }
  @Composable
  fun SourceTabs(vertical: Boolean) {
    if (vertical)
      Column(Modifier.width(88.dp).fillMaxHeight().testTag("models-source-rail")) {
        listOf(false, true).forEach { server ->
          Surface(
            color =
              if (remote == server) MaterialTheme.colorScheme.secondaryContainer
              else MaterialTheme.colorScheme.surface
          ) {
            Column(
              Modifier.fillMaxWidth()
                .height(88.dp)
                .testTag(if (server) "models-server-tab" else "models-local-tab")
                .selectable(
                  remote == server,
                  role = androidx.compose.ui.semantics.Role.Tab,
                  onClick = { remote = server },
                ),
              horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Icon(if (server) Icons.Default.Cloud else Icons.Default.Smartphone, null)
              Text(
                tr(if (server) "server" else "local"),
                Modifier.padding(4.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
              )
            }
          }
        }
      }
    else
      TabRow(if (remote) 1 else 0) {
        Tab(
          !remote,
          { remote = false },
          text = { Text(tr("local")) },
          icon = { Icon(Icons.Default.Smartphone, null) },
          modifier = Modifier.height(64.dp).testTag("models-local-tab"),
        )
        Tab(
          remote,
          { remote = true },
          text = { Text(tr("server")) },
          icon = { Icon(Icons.Default.Cloud, null) },
          modifier = Modifier.height(64.dp).testTag("models-server-tab"),
        )
      }
  }
  BoxWithConstraints(Modifier.fillMaxSize().testTag("model-catalog")) {
    val wide = maxWidth >= 600.dp && maxWidth > maxHeight
    Row(Modifier.fillMaxSize()) {
      if (wide) SourceTabs(true)
      Column(Modifier.weight(1f).fillMaxHeight()) {
        if (!wide) SourceTabs(false)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
          OutlinedTextField(
            query,
            { query = it },
            Modifier.weight(1f).testTag("models-search"),
            singleLine = true,
            label = { Text(tr("search_models")) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
              if (query.isNotBlank())
                HintIconButton({ query = "" }, tooltip = tr("clear")) {
                  Icon(Icons.Default.Close, tr("clear"))
                }
            },
          )
          if (!remote && onImport != null)
            HintIconButton(
              onImport,
              Modifier.padding(top = 12.dp).testTag("models-import"),
              tooltip = tr("import"),
            ) {
              Icon(com.karandaev.fieldfolio.core.ui.FileTransferIcons.Import, tr("import"))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
          Box {
            HintIconButton({ filtering = true }, tooltip = tr("filter")) {
              Icon(Icons.Default.FilterList, tr("filter"))
            }
            DropdownMenu(filtering, { filtering = false }) {
              DropdownMenuItem(
                text = { Text(tr("all_tasks")) },
                onClick = {
                  task = null
                  filtering = false
                },
              )
              choices
                .filter { it.remote == remote }
                .map { it.task }
                .distinct()
                .sorted()
                .forEach { type ->
                  DropdownMenuItem(
                    text = { Text(taskLabel(type)) },
                    onClick = {
                      task = type
                      filtering = false
                    },
                  )
                }
            }
          }
          Box {
            HintIconButton({ sorting = true }, tooltip = tr("sort")) {
              Icon(Icons.Default.Sort, tr("sort"))
            }
            DropdownMenu(sorting, { sorting = false }) {
              ModelSort.entries.forEach { mode ->
                DropdownMenuItem(
                  text = { Text(tr("sort_${mode.name.lowercase()}")) },
                  onClick = {
                    sort = mode
                    sorting = false
                  },
                )
              }
            }
          }
          Text(
            task?.let { taskLabel(it) } ?: tr("all_tasks"),
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.labelLarge,
          )
          Spacer(Modifier.weight(1f))
          Text("${visible.size}", Modifier.padding(12.dp))
        }
        com.karandaev.fieldfolio.core.ui.scroll.AppLazyStaggeredGrid(
          columns = if (wide) 2 else 1,
          modifier = Modifier.fillMaxWidth().weight(1f).testTag("model-grid"),
        ) {
          if (visible.isEmpty()) item { Text(tr("no_models")) }
          items(visible, key = { "${it.remote}:${it.id}" }) { choice ->
            OutlinedCard(
              Modifier.fillMaxWidth().testTag("model-card-${choice.id}").clickable {
                onChoose(choice)
              }
            ) {
              ModelCardContent(
                headlineContent = {
                  Text(
                    choice.name,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                  )
                },
                supportingContent = {
                  Column {
                    Text(taskLabel(choice))
                    if (choice.local?.assetPath != null)
                      Text(
                        androidx.compose.ui.res.stringResource(
                          com.karandaev.fieldfolio.R.string.finish_builtin_model
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    if (choice.local?.legacySourcePath != null)
                      Text(tr("model_file_missing"), color = MaterialTheme.colorScheme.error)
                  }
                },
                leadingContent = {
                  Icon(taskIcon(choice.task), null, Modifier.testTag("model-icon-${choice.id}"))
                },
                trailingContent = {
                  Row {
                    HintIconButton({ onChoose(choice) }, tooltip = tr("use_model")) {
                      Icon(Icons.Default.PlayArrow, tr("use_model"))
                    }
                    if (management && choice.local != null) {
                      var menu by rememberSession { mutableStateOf(false) }
                      Box {
                        HintIconButton({ menu = true }, tooltip = tr("actions")) {
                          Icon(Icons.Default.MoreVert, tr("actions"))
                        }
                        DropdownMenu(menu, { menu = false }) {
                          actions(choice).forEach { action ->
                            DropdownMenuItem(
                              text = { Text(action.label) },
                              onClick = {
                                menu = false
                                action.perform()
                              },
                            )
                          }
                        }
                      }
                    }
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun taskLabel(task: String): String =
  when (task) {
    "DETECTION" -> tr("task_detection")
    "SEGMENTATION" -> tr("task_segmentation")
    "CLASSIFICATION" -> tr("task_classification")
    "REGRESSION" -> tr("task_regression")
    "TEXT" -> tr("task_text")
    else -> task
  }

fun taskIcon(task: String) =
  when (task) {
    "DETECTION" -> Icons.Default.CenterFocusStrong
    "SEGMENTATION" -> Icons.Default.Layers
    "CLASSIFICATION" -> Icons.Default.Category
    "REGRESSION" -> Icons.Default.ShowChart
    "TEXT" -> Icons.Default.TextFields
    else -> Icons.Default.Memory
  }

data class ModelMenuAction(val label: String, val perform: () -> Unit)

@Composable
fun taskLabel(choice: ModelChoice): String =
  if (choice.numericInput)
    androidx.compose.ui.res.stringResource(
      if (choice.task == "CLASSIFICATION")
        com.karandaev.fieldfolio.R.string.finish_numeric_classification
      else com.karandaev.fieldfolio.R.string.finish_numeric_regression
    )
  else taskLabel(choice.task)

@Composable
fun taskLabel(result: com.karandaev.fieldfolio.core.document.ModelResultData): String =
  if (result.inputKind == "NUMBERS")
    androidx.compose.ui.res.stringResource(
      if (result.task == "CLASSIFICATION")
        com.karandaev.fieldfolio.R.string.finish_numeric_classification
      else com.karandaev.fieldfolio.R.string.finish_numeric_regression
    )
  else taskLabel(result.task)

@Composable
private fun ModelCardContent(
  headlineContent: @Composable () -> Unit,
  supportingContent: @Composable () -> Unit,
  leadingContent: @Composable () -> Unit,
  trailingContent: @Composable () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    leadingContent()
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      ProvideTextStyle(MaterialTheme.typography.bodyLarge) { headlineContent() }
      ProvideTextStyle(MaterialTheme.typography.bodyMedium) { supportingContent() }
    }
    trailingContent()
  }
}
