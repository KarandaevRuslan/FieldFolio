@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.karandaev.fieldfolio.feature.document.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.ui.components.actions.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.scroll.AppLazyColumn as LazyColumn
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.DocumentThumbnail
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import com.karandaev.fieldfolio.feature.document.state.action
import com.karandaev.fieldfolio.feature.document.viewer.DocumentBreadcrumbs
import com.karandaev.fieldfolio.feature.document.viewer.icon
import sh.calvin.reorderable.*

/** Document positions remain authoritative; a search never changes the persisted order. */
@Composable
fun DocumentFileBrowser(
  vm: DocumentViewModel,
  folder: String?,
  onNavigate: (String?) -> Unit,
  onOpen: (DocumentNode) -> Unit,
  onEdit: (DocumentNode) -> Unit,
  add: @Composable () -> Unit,
) {
  val app = com.karandaev.fieldfolio.core.di.rememberAppServices()
  val context = androidx.compose.ui.platform.LocalContext.current
  val exporter =
    androidx.activity.compose.rememberLauncherForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
      app.reportLibrary.externalPicker = false
      vm.writeElementExport(context.applicationContext, uri)
    }
  LaunchedEffect(vm.elementExportReady) {
    if (vm.elementExportReady) {
      vm.elementExportReady = false
      app.reportLibrary.externalPicker = true
      try {
        exporter.launch(vm.elementExportName)
      } catch (failure: Exception) {
        app.reportLibrary.externalPicker = false
        vm.clearElementExport()
        vm.error = failure
      }
    }
  }
  fun export(ids: List<String>) {
    vm.prepareElementExport(ids, app.weatherProvider)
  }
  fun copyPath(id: String) {
    com.karandaev.fieldfolio.core.ui.clipboard.copyPathToClipboard(context, vm.tree.path(id))
  }
  val tree = remember(vm.document) { DocumentTree(vm.document) }
  var query by rememberSession(folder) { mutableStateOf("") }
  var selected by rememberSession(folder) { mutableStateOf(setOf<String>()) }
  var rename by rememberSession { mutableStateOf<String?>(null) }
  var goPath by rememberSession { mutableStateOf(false) }
  var move by rememberSession { mutableStateOf<List<String>?>(null) }
  var deleting by rememberSession { mutableStateOf<List<String>?>(null) }
  var contextId by rememberSaveable { mutableStateOf<String?>(null) }
  val children = tree.children(folder)
  val visible =
    children.filter {
      it.name.contains(query, ignoreCase = true) ||
        it.displayLabel.contains(query, ignoreCase = true)
    }
  androidx.activity.compose.BackHandler(selected.isNotEmpty()) { selected = emptySet() }
  LaunchedEffect(children.map { it.id }) {
    selected = selected.intersect(children.map { it.id }.toSet())
  }
  val state = rememberLazyListState()
  val reorder =
    rememberReorderableLazyListState(state) { from, to ->
      if (query.isBlank() && !vm.busy) {
        val id = from.key as? String
        val target = to.key as? String
        if (id != null && target != null)
          vm.moveToIndex(id, children.indexOfFirst { it.id == target })
      }
    }
  BoxWithConstraints(Modifier.fillMaxSize().testTag("document-file-browser")) {
    val wide = maxWidth >= 600.dp && maxWidth > maxHeight
    @Composable
    fun Tools() {
      val single = children.firstOrNull { it.id == selected.singleOrNull() }
      if (selected.isEmpty()) add()
      vfsSelectionActions(visible.isNotEmpty(), selected.size, vm.clipboard != null).forEach {
        action ->
        VfsSelectionButton(action, enabled = !vm.busy) {
          when (action) {
            VfsSelectionAction.SELECT_ALL -> selected = visible.map { it.id }.toSet()
            VfsSelectionAction.CLEAR_SELECTION -> selected = emptySet()
            VfsSelectionAction.RENAME -> rename = selected.singleOrNull()
            VfsSelectionAction.COPY -> {
              vm.clipboard = selected.toList() to true
              selected = emptySet()
            }
            VfsSelectionAction.CUT -> {
              vm.clipboard = selected.toList() to false
              selected = emptySet()
            }
            VfsSelectionAction.MOVE -> move = selected.toList()
            VfsSelectionAction.PASTE -> vm.paste()
            VfsSelectionAction.CLEAR_CLIPBOARD -> vm.clipboard = null
            VfsSelectionAction.DELETE -> deleting = selected.toList()
          }
        }
        if (action == VfsSelectionAction.MOVE)
          VfsToolIcon(
            com.karandaev.fieldfolio.core.ui.FileTransferIcons.Export,
            tr("export"),
            "vfs-export",
            enabled = !vm.elementExportBusy,
          ) {
            export(selected.toList())
          }
        if (action == VfsSelectionAction.RENAME && single != null) {
          if (selected.size == 1)
            VfsToolIcon(
              Icons.Default.Link,
              androidx.compose.ui.res.stringResource(
                com.karandaev.fieldfolio.R.string.library_copy_path
              ),
              "vfs-copy-path",
            ) {
              copyPath(selected.single())
            }
          VfsToolIcon(
            if (single.hideTitle) Icons.Default.FormatClear else Icons.Default.Title,
            androidx.compose.ui.res.stringResource(
              if (single.hideTitle) com.karandaev.fieldfolio.R.string.v16_show_title
              else com.karandaev.fieldfolio.R.string.next_hide_title
            ),
            "vfs-title",
            selected = single.hideTitle,
          ) {
            vm.update(single.copy(hideTitle = !single.hideTitle))
          }
          VfsToolIcon(
            if (single.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            androidx.compose.ui.res.stringResource(
              if (single.visible) com.karandaev.fieldfolio.R.string.next_hide_object
              else com.karandaev.fieldfolio.R.string.next_show_object
            ),
            "vfs-visible",
            selected = single.visible,
          ) {
            vm.update(single.copy(visible = !single.visible))
          }
        }
      }
    }
    Row(Modifier.fillMaxSize()) {
      if (wide) VfsActionBar(true, "document-tool-rail") { Tools() }
      Column(Modifier.weight(1f).fillMaxHeight()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          BrowserIcon(Icons.Default.ArrowBack, "history_back", vm.canGoBack) { vm.goBack() }
          BrowserIcon(Icons.Default.ArrowForward, "history_forward", vm.canGoForward) {
            vm.goForward()
          }
          BrowserIcon(Icons.Default.ArrowUpward, "parent_folder", folder != null) {
            onNavigate(folder?.let { tree.node(it).parentId })
          }
          Box(Modifier.weight(1f)) { DocumentBreadcrumbs(tree, folder, onNavigate) }
          HintIconButton(
            { goPath = true },
            tooltip =
              androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.library_path),
          ) {
            Icon(
              Icons.Default.EditLocationAlt,
              androidx.compose.ui.res.stringResource(com.karandaev.fieldfolio.R.string.library_path),
            )
          }
        }
        if (!wide) VfsActionBar(false, "document-tool-bar") { Tools() }
        if (vm.elementExportBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(
          query,
          { query = it },
          Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("document-file-search"),
          singleLine = true,
          label = { Text(tr("search_files")) },
          leadingIcon = { Icon(Icons.Default.Search, null) },
          trailingIcon = {
            if (query.isNotEmpty())
              HintIconButton({ query = "" }, tooltip = tr("clear")) {
                Icon(Icons.Default.Close, tr("clear"))
              }
          },
        )
        LazyColumn(
          Modifier.fillMaxSize().testTag("document-editor-files"),
          state = state,
          contentPadding = PaddingValues(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (visible.isEmpty()) item { Text(tr("empty"), Modifier.padding(16.dp)) }
          items(visible, key = { it.id }) { node ->
            ReorderableItem(reorder, key = node.id, enabled = query.isBlank() && !vm.busy) {
              dragging ->
              Surface(
                Modifier.fillMaxWidth()
                  .testTag("document-node-${node.id}")
                  .combinedClickable(
                    enabled = !vm.busy,
                    onClick = {
                      if (selected.isNotEmpty())
                        selected =
                          if (node.id in selected) selected - node.id else selected + node.id
                      else if (node.kind == BlockKind.FOLDER) onNavigate(node.id) else onOpen(node)
                    },
                    onLongClick = {
                      selected = if (node.id in selected) selected - node.id else selected + node.id
                    },
                  ),
                shape = MaterialTheme.shapes.medium,
                color =
                  if (node.id in selected) MaterialTheme.colorScheme.secondaryContainer
                  else MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = if (dragging) 8.dp else 0.dp,
              ) {
                com.karandaev.fieldfolio.core.ui.components.fields.VfsItemRow(
                  headlineContent = {
                    Text(
                      if (node.hideTitle) node.name else node.displayLabel,
                      maxLines = 1,
                      overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                  },
                  supportingContent = {
                    Column {
                      Text(node.kind.label())
                      if (node.hideTitle)
                        Text(
                          androidx.compose.ui.res.stringResource(
                            com.karandaev.fieldfolio.R.string.next_title_empty
                          ),
                          style = MaterialTheme.typography.labelSmall,
                        )
                      if (!node.visible)
                        Text(
                          androidx.compose.ui.res.stringResource(
                            com.karandaev.fieldfolio.R.string.next_hidden
                          ),
                          style = MaterialTheme.typography.labelSmall,
                        )
                      if (node.displayName != null && !node.hideTitle)
                        Text(node.name, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                  },
                  leadingContent = {
                    if (selected.isNotEmpty())
                      Checkbox(
                        node.id in selected,
                        { checked ->
                          selected = if (checked) selected + node.id else selected - node.id
                        },
                      )
                    else
                      DocumentThumbnail(node, vm.files, vm.thumbnails) {
                        Icon(node.kind.icon(), null)
                      }
                  },
                  trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      HintIconButton(
                        {},
                        Modifier.longPressDraggableHandle().testTag("drag-${node.id}"),
                        enabled = query.isBlank() && !vm.busy,
                        tooltip = tr("reorder"),
                        observeHold = true,
                      ) {
                        Icon(Icons.Default.DragHandle, tr("reorder"))
                      }
                      Box {
                        HintIconButton({ contextId = node.id }, tooltip = tr("actions")) {
                          Icon(Icons.Default.MoreVert, tr("actions"))
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
  contextId?.let { id ->
    tree.byId[id]?.let { node ->
      AdaptiveActionMenu(
        true,
        { contextId = null },
        if (node.hideTitle) node.name else node.displayLabel,
      ) {
        (listOf("rename") +
            (if (node.kind == BlockKind.FOLDER) emptyList() else listOf("edit")) +
            listOf(
              "title_visibility",
              "visibility",
              "copy",
              "cut",
              "move",
              "export",
              "copy_path",
              "up",
              "down",
              "delete",
            ))
          .forEach { action ->
            AdaptiveActionItem(
              text = {
                Text(
                  if (action == "title_visibility")
                    androidx.compose.ui.res.stringResource(
                      if (node.hideTitle) com.karandaev.fieldfolio.R.string.v16_show_title
                      else com.karandaev.fieldfolio.R.string.next_hide_title
                    )
                  else if (action == "visibility")
                    androidx.compose.ui.res.stringResource(
                      if (node.visible) com.karandaev.fieldfolio.R.string.next_hide_object
                      else com.karandaev.fieldfolio.R.string.next_show_object
                    )
                  else if (action == "copy_path")
                    androidx.compose.ui.res.stringResource(
                      com.karandaev.fieldfolio.R.string.library_copy_path
                    )
                  else tr(action)
                )
              },
              onClick = {
                contextId = null
                when (action) {
                  "title_visibility" -> vm.update(node.copy(hideTitle = !node.hideTitle))
                  "visibility" -> vm.update(node.copy(visible = !node.visible))
                  "rename" -> rename = node.id
                  "edit" -> onEdit(node)
                  "export" -> export(listOf(node.id))
                  "copy_path" -> copyPath(node.id)
                  "copy" -> vm.clipboard = listOf(node.id) to true
                  "cut" -> vm.clipboard = listOf(node.id) to false
                  "move" -> move = listOf(node.id)
                  "up" -> vm.reorder(node.id, -1)
                  "down" -> vm.reorder(node.id, 1)
                  "delete" -> deleting = listOf(node.id)
                }
              },
            )
          }
      }
    }
  }
  rename?.let { id ->
    tree.byId[id]?.let { node ->
      NodeIdentityDialog(node, { rename = null }) { next ->
        vm.update(next)
        if (vm.error == null) rename = null
      }
    }
  }
  if (goPath)
    com.karandaev.fieldfolio.core.ui.components.fields.VfsPathDialog(
      tree.path(folder),
      { goPath = false },
      tag = "document-path",
    ) { path ->
      runCatching { tree.resolvePath(path, folder, foldersOnly = false) }
        .onSuccess {
          val target = it?.let(tree.byId::get)
          if (target == null || target.kind == BlockKind.FOLDER) onNavigate(it)
          else {
            onNavigate(target.parentId)
            onOpen(target)
          }
          goPath = false
        }
        .onFailure { vm.error = it }
    }

  move?.let { ids ->
    DocumentDestinationDialog(vm.document, ids, folder, { move = null }) { target ->
      vm.transfer(ids, target, false)
      move = null
      selected = emptySet()
    }
  }
  deleting?.let { ids ->
    com.karandaev.fieldfolio.core.ui.components.dialog.SafeAlertDialog(
      { deleting = null },
      title = { Text(tr("delete")) },
      text = { Text(tr("confirm_delete")) },
      confirmButton = {
        TextButton({
          ids.filter { it in vm.tree.byId }.forEach(vm::remove)
          selected = emptySet()
          deleting = null
        }) {
          Text(tr("delete"))
        }
      },
      dismissButton = { TextButton({ deleting = null }) { Text(tr("cancel")) } },
    )
  }
}

@Composable
private fun BrowserIcon(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  enabled: Boolean = true,
  action: () -> Unit,
) {
  HintIconButton(action, enabled = enabled, tooltip = tr(label)) { Icon(icon, tr(label)) }
}
