@file:OptIn(
  androidx.compose.foundation.ExperimentalFoundationApi::class,
  androidx.compose.material.ExperimentalMaterialApi::class,
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
  androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.karandaev.fieldfolio.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.library.LibraryNode
import com.karandaev.fieldfolio.core.library.displayLabel
import com.karandaev.fieldfolio.core.ui.components.actions.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.scroll.AppLazyColumn as LazyColumn
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import java.text.DateFormat
import java.util.Date

@Composable
internal fun LibraryBrowser(
  vm: LibraryViewModel,
  onOpen: (Long) -> Unit,
  onEdit: (Long) -> Unit,
  onCreate: (Long?) -> Unit,
  header: (@Composable () -> Unit)? = null,
) {
  val transfers = rememberLibraryTransfers(vm.library)
  BackHandler(vm.folder != null || vm.selected.isNotEmpty()) {
    if (vm.selected.isNotEmpty()) vm.selected = emptySet() else vm.up()
  }
  val pull = rememberPullRefreshState(vm.loading, { if (!vm.busy) vm.refresh() })
  BoxWithConstraints(Modifier.fillMaxSize().pullRefresh(pull)) {
    val wide = maxWidth >= 600.dp && maxWidth > maxHeight
    Row(Modifier.fillMaxSize()) {
      if (wide) LibraryToolbar(vm, onCreate, transfers, vertical = true)
      if (wide && vm.folder == null && header != null)
        Column(
          Modifier.weight(0.35f).padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          header?.invoke()
        }
      LazyColumn(
        Modifier.weight(1f).testTag("library-browser"),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (!wide && vm.folder == null && header != null)
          item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { header?.invoke() } }
        stickyHeader {
          Surface(color = MaterialTheme.colorScheme.background) {
            Column {
              LibraryBreadcrumbs(vm)
              if (!wide) LibraryToolbar(vm, onCreate, transfers)
              var sorting by rememberSession { mutableStateOf(false) }
              OutlinedTextField(
                vm.query,
                { vm.query = it },
                Modifier.fillMaxWidth().testTag("library-search"),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.library_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                  Row {
                    LibraryAction(R.string.library_refresh, Icons.Default.Refresh, !vm.busy) {
                      vm.error = null
                      vm.refresh()
                    }
                    Box {
                      LibraryAction(R.string.library_sort, Icons.AutoMirrored.Filled.Sort) {
                        sorting = true
                      }
                      DropdownMenu(sorting, { sorting = false }) {
                        DropdownMenuItem(
                          { Text(stringResource(R.string.library_sort_name)) },
                          {
                            vm.sortByDate = false
                            sorting = false
                          },
                        )
                        DropdownMenuItem(
                          { Text(stringResource(R.string.library_sort_date)) },
                          {
                            vm.sortByDate = true
                            sorting = false
                          },
                        )
                      }
                    }
                  }
                },
              )
              if (vm.selected.isNotEmpty())
                Text(
                  stringResource(R.string.library_selected, vm.selected.size),
                  Modifier.padding(8.dp),
                  style = MaterialTheme.typography.labelMedium,
                )
              if (vm.busy || transfers.busy || vm.loading)
                LinearProgressIndicator(Modifier.fillMaxWidth())
              else Spacer(Modifier.height(4.dp))
            }
          }
        }
        if (!vm.loading && vm.entries.isEmpty())
          item {
            Text(
              stringResource(
                if (vm.query.isEmpty()) R.string.library_empty else R.string.library_no_matches
              ),
              Modifier.padding(16.dp),
            )
          }
        items(vm.entries, key = { it.id }) { node ->
          LibraryItem(
            vm,
            node,
            { vm.open(node, onOpen) },
            { onEdit(node.id) },
            { transfers.export(listOf(node.id)) },
          )
        }
      }
    }
    PullRefreshIndicator(vm.loading, pull, Modifier.align(androidx.compose.ui.Alignment.TopCenter))
  }
  vm.contextItem?.let { id ->
    vm.nodes
      .firstOrNull { it.id == id }
      ?.let { node ->
        LibraryItemActions(
          vm,
          node,
          { vm.open(node, onOpen) },
          { onEdit(id) },
          { transfers.export(listOf(id)) },
        )
      }
  }
  LibraryBrowserDialogs(vm, onOpen)
}

@Composable
internal fun LibraryAction(
  label: Int,
  icon: ImageVector,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val text = stringResource(label)
  TooltipBox(
    TooltipDefaults.rememberPlainTooltipPositionProvider(),
    { PlainTooltip { Text(text) } },
    rememberTooltipState(),
  ) {
    IconButton(onClick, enabled = enabled) { Icon(icon, text) }
  }
}

@Composable
private fun LibraryBreadcrumbs(vm: LibraryViewModel) {
  val scroll = rememberScrollState()
  LaunchedEffect(vm.folder, scroll.maxValue) { scroll.animateScrollTo(scroll.maxValue) }
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    LibraryAction(R.string.library_back, Icons.AutoMirrored.Filled.ArrowBack, vm.canBack) {
      vm.back()
    }
    LibraryAction(R.string.library_forward, Icons.AutoMirrored.Filled.ArrowForward, vm.canForward) {
      vm.forward()
    }
    LibraryAction(R.string.library_up, Icons.Default.ArrowUpward, vm.folder != null) { vm.up() }
    Row(
      Modifier.weight(1f).horizontalScroll(scroll),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton({ vm.navigate(null) }) { Text(stringResource(R.string.library_root)) }
      vm.path.forEach { node ->
        Text("›")
        TextButton({ vm.navigate(node.id) }) { Text(node.name, maxLines = 1) }
      }
    }
    LibraryAction(R.string.library_path, Icons.Default.EditLocationAlt) { vm.goPath = true }
  }
}

@Composable
private fun LibraryToolbar(
  vm: LibraryViewModel,
  onCreate: (Long?) -> Unit,
  transfers: LibraryTransferActions,
  vertical: Boolean = false,
) {
  val enabled = !vm.busy && !transfers.busy && !vm.loading
  @Composable
  fun Actions() {
    if (vm.selected.isEmpty()) {
      LibraryAction(R.string.library_new_folder, Icons.Default.CreateNewFolder, enabled) {
        vm.createFolder = true
      }
      LibraryAction(R.string.library_new_report, Icons.AutoMirrored.Filled.NoteAdd, enabled) {
        onCreate(vm.folder)
      }
      LibraryAction(
        R.string.library_import,
        com.karandaev.fieldfolio.core.ui.FileTransferIcons.Import,
        enabled,
      ) {
        transfers.import(vm.folder)
      }
    }
    vfsSelectionActions(vm.entries.isNotEmpty(), vm.selected.size, vm.clipboard != null).forEach {
      action ->
      VfsSelectionButton(action, enabled) {
        when (action) {
          VfsSelectionAction.SELECT_ALL -> vm.selected = vm.entries.map { it.id }.toSet()
          VfsSelectionAction.CLEAR_SELECTION -> vm.selected = emptySet()
          VfsSelectionAction.RENAME -> vm.rename = vm.selected.singleOrNull()
          VfsSelectionAction.COPY -> vm.copy(vm.selected.toList(), false)
          VfsSelectionAction.CUT -> vm.copy(vm.selected.toList(), true)
          VfsSelectionAction.MOVE -> vm.moving = LibraryClipboard(vm.selected.toList(), true)
          VfsSelectionAction.PASTE -> vm.paste()
          VfsSelectionAction.CLEAR_CLIPBOARD -> vm.clipboard = null
          VfsSelectionAction.DELETE -> vm.deleting = vm.selected.toList()
        }
      }
    }
    val exportIds =
      if (vm.selected.isNotEmpty()) vm.selected.toList()
      else vm.folder?.let(::listOf) ?: vm.nodes.filter { it.parentId == null }.map { it.id }
    if (exportIds.isNotEmpty())
      LibraryAction(
        R.string.library_export,
        com.karandaev.fieldfolio.core.ui.FileTransferIcons.Export,
        enabled,
      ) {
        transfers.export(exportIds)
      }
    if (vm.nodes.any { it.lockId != null })
      LibraryAction(R.string.library_lock_all, Icons.Default.Lock, enabled) { vm.lock() }
  }
  VfsActionBar(vertical, if (vertical) "library-tool-rail" else "library-tool-bar") { Actions() }
}

@Composable
private fun LibraryItem(
  vm: LibraryViewModel,
  node: LibraryNode,
  open: () -> Unit,
  edit: () -> Unit,
  export: () -> Unit,
) {
  val locked = vm.locked(node.id)
  Card(Modifier.fillMaxWidth()) {
    com.karandaev.fieldfolio.core.ui.components.fields.VfsItemRow(
      modifier =
        Modifier.testTag("library-node-${node.id}")
          .combinedClickable(
            onClick = { if (vm.selected.isNotEmpty()) vm.select(node.id) else open() },
            onLongClick = { vm.select(node.id) },
          ),
      headlineContent = { Text(node.displayLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
      supportingContent = {
        Column {
          if (node.displayName != null)
            Text(
              node.name,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.labelSmall,
            )
          Text(
            if (locked) stringResource(R.string.library_locked)
            else if (node.folder) stringResource(R.string.library_folder)
            else
              DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(node.updatedAt)),
            maxLines = 1,
          )
        }
      },
      leadingContent = {
        if (vm.selected.isNotEmpty()) Checkbox(node.id in vm.selected, { vm.select(node.id) })
        else if (locked) Icon(Icons.Default.Lock, null)
        else if (node.folder) Icon(Icons.Default.Folder, null) else LibraryCover(vm, node)
      },
      trailingContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (!node.folder && !locked && vm.selected.isEmpty())
            HintIconButton(edit, tooltip = stringResource(R.string.library_edit)) {
              Icon(Icons.Default.Edit, stringResource(R.string.library_edit))
            }
          Box {
            HintIconButton(
              { vm.contextItem = node.id },
              tooltip = stringResource(R.string.library_actions),
            ) {
              Icon(Icons.Default.MoreVert, stringResource(R.string.library_actions))
            }
          }
        }
      },
    )
  }
}

@Composable
private fun LibraryItemActions(
  vm: LibraryViewModel,
  node: LibraryNode,
  open: () -> Unit,
  edit: () -> Unit,
  export: () -> Unit,
) {
  val locked = vm.locked(node.id)
  AdaptiveActionMenu(true, { vm.contextItem = null }, node.displayLabel) {
    fun close(run: () -> Unit) {
      vm.contextItem = null
      run()
    }
    AdaptiveActionItem(
      { Text(stringResource(if (locked) R.string.library_unlock else R.string.library_open)) },
      { close(open) },
    )
    if (!node.folder && !locked)
      AdaptiveActionItem({ Text(stringResource(R.string.library_edit)) }, { close(edit) })
    AdaptiveActionItem(
      { Text(stringResource(R.string.library_rename)) },
      { close { vm.rename = node.id } },
    )
    AdaptiveActionItem(
      { Text(stringResource(R.string.library_copy_path)) },
      { close { vm.copyPath(node.id) } },
    )
    AdaptiveActionItem(
      { Text(stringResource(R.string.library_copy)) },
      { close { vm.copy(listOf(node.id), false) } },
    )
    AdaptiveActionItem(
      { Text(stringResource(R.string.library_cut)) },
      { close { vm.copy(listOf(node.id), true) } },
    )
    AdaptiveActionItem(
      { Text(stringResource(R.string.library_move)) },
      { close { vm.moving = LibraryClipboard(listOf(node.id), true) } },
    )
    AdaptiveActionItem({ Text(stringResource(R.string.library_export)) }, { close(export) })
    if (!locked) {
      AdaptiveActionItem(
        {
          Text(
            stringResource(
              if (node.lockId == null) R.string.library_password
              else R.string.library_change_password
            )
          )
        },
        { close { vm.password = node.id } },
      )
      if (node.lockId != null)
        AdaptiveActionItem(
          { Text(stringResource(R.string.library_remove_password)) },
          { close { vm.removePassword = node.id } },
        )
    }
    AdaptiveActionItem(
      { Text(stringResource(R.string.library_delete)) },
      { close { vm.deleting = listOf(node.id) } },
    )
  }
}
