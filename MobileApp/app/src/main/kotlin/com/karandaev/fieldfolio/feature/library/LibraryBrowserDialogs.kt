@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAlertDialog
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog
import com.karandaev.fieldfolio.core.ui.scroll.AppLazyColumn as LazyColumn
import com.karandaev.fieldfolio.core.ui.state.rememberSession

@Composable
internal fun LibraryBrowserDialogs(vm: LibraryViewModel, onOpen: (Long) -> Unit) {
  if (vm.createFolder)
    LibraryIdentityDialog(R.string.library_new_folder, dismiss = { vm.createFolder = false }) {
      name,
      display ->
      vm.perform({ vm.library.createFolder(name, vm.folder, display) }) { vm.createFolder = false }
    }
  vm.rename?.let { id ->
    vm.nodes
      .firstOrNull { it.id == id }
      ?.let { node ->
        com.karandaev.fieldfolio.core.ui.components.fields.ResourceIdentityDialog(
          node.name,
          node.displayName,
          { vm.rename = null },
          valid = { runCatching { LibraryTree.name(it) }.isSuccess },
        ) { name, display ->
          vm.perform({ vm.library.rename(id, name, display) }) { vm.rename = null }
        }
      }
  }
  vm.deleting?.let { ids ->
    LibraryConfirmDialog(
      R.string.library_delete,
      R.string.library_delete_body,
      { vm.deleting = null },
    ) {
      vm.perform({ vm.library.delete(ids) }) {
        vm.deleting = null
        vm.selected = emptySet()
      }
    }
  }
  vm.password?.let { id ->
    LibraryPasswordDialog(R.string.library_password, true, vm.busy, { vm.password = null }) {
      password ->
      vm.perform({ vm.library.setPassword(id, password) }) { vm.password = null }
    }
  }
  vm.removePassword?.let { id ->
    LibraryConfirmDialog(
      R.string.library_remove_password,
      R.string.library_password_remove_body,
      { vm.removePassword = null },
    ) {
      vm.perform({ vm.library.setPassword(id, null) }) { vm.removePassword = null }
    }
  }
  if (vm.goPath)
    com.karandaev.fieldfolio.core.ui.components.fields.VfsPathDialog(
      "/" + vm.path.joinToString("/") { android.net.Uri.encode(it.name) },
      { vm.goPath = false },
      vm.busy,
      tag = "library-path",
    ) {
      vm.visitPath(it, onOpen)
    }

  vm.moving?.let { source ->
    LibraryDestinationDialog(vm, source, { vm.moving = null }) { target ->
      vm.paste(source, target)
    }
  }
  vm.confirmUnprotect?.let { (clip, destination) ->
    LibraryConfirmDialog(
      R.string.library_unprotect,
      R.string.library_unprotect_body,
      { vm.confirmUnprotect = null },
    ) {
      vm.confirmUnprotect = null
      vm.paste(clip, destination, true)
    }
  }
  vm.unlock?.let { id ->
    LibraryPasswordDialog(
      R.string.library_unlock,
      false,
      vm.busy,
      {
        vm.unlock = null
        vm.pendingPath = null
      },
    ) { password ->
      vm.perform({
        vm.library.unlock(id, password)
        if (vm.nodes.firstOrNull { it.id == id }?.folder == false)
          vm.library.getInspectionReportById(id)
      }) {
        vm.unlock = null
        val pending = vm.pendingPath
        if (pending != null) {
          vm.resumePendingPath(onOpen)
        } else
          vm.nodes
            .firstOrNull { it.id == id }
            ?.let { node -> if (node.folder) vm.navigate(id) else onOpen(id) }
      }
    }
  }
  vm.error?.let { error ->
    SafeAlertDialog(
      onDismissRequest = { vm.error = null },
      title = { Text(stringResource(R.string.library_error)) },
      text = { Text(stringResource(libraryErrorResource(error))) },
      confirmButton = {
        TextButton({ vm.error = null }) { Text(stringResource(R.string.library_confirm)) }
      },
    )
  }
}

@Composable
internal fun LibraryDestinationDialog(
  vm: LibraryViewModel,
  source: LibraryClipboard,
  dismiss: () -> Unit,
  choose: (Long?) -> Unit,
) {
  var parent by rememberSession { mutableStateOf(vm.folder) }
  var unlock by rememberSession { mutableStateOf<Long?>(null) }
  var create by rememberSession { mutableStateOf(false) }
  val tree = vm.tree
  val sourceIds = source.ids.filter { id -> vm.nodes.any { it.id == id } }
  val sourceAvailable = !vm.loading && sourceIds.isNotEmpty() && sourceIds.size == source.ids.size
  val invalid =
    if (sourceIds.isEmpty()) emptySet() else tree.subtree(sourceIds).map { it.id }.toSet()
  val path =
    parent
      ?.takeIf { id -> vm.nodes.any { it.id == id } }
      ?.let { tree.ancestors(it) + tree.node(it) }
      .orEmpty()
  SafeAppDialog(
    dismiss,
    properties =
      DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn),
  ) {
    Surface(Modifier.fillMaxSize()) {
      Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
          TopAppBar(
            title = {
              Text(
                stringResource(R.string.library_destination),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
              )
            },
            navigationIcon = {
              HintIconButton(dismiss, tooltip = stringResource(R.string.library_cancel)) {
                Icon(Icons.Default.Close, stringResource(R.string.library_cancel))
              }
            },
          )
        },
        bottomBar = {
          Surface(tonalElevation = 3.dp) {
            Button(
              { choose(parent) },
              enabled =
                sourceAvailable &&
                  !vm.busy &&
                  parent !in invalid &&
                  (parent == null ||
                    vm.nodes.any { it.id == parent && it.folder } && !vm.locked(parent!!)),
              modifier =
                Modifier.padding(12.dp)
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("library-move-here"),
            ) {
              Text(stringResource(R.string.library_move_here))
            }
          }
        },
      ) { padding ->
        LazyColumn(
          Modifier.fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .padding(horizontal = 12.dp)
            .testTag("destination-folders")
        ) {
          item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
              TextButton({ parent = null }) { Text(stringResource(R.string.library_root)) }
              path.forEach { node -> TextButton({ parent = node.id }) { Text("› " + node.name) } }
            }
          }
          item {
            LibraryAction(
              R.string.library_new_folder,
              Icons.Default.CreateNewFolder,
              !vm.busy && (parent == null || !vm.locked(parent!!)),
            ) {
              create = true
            }
          }
          items(
            vm.nodes.filter { it.folder && it.parentId == parent && it.id !in invalid },
            key = { it.id },
          ) { folder ->
            ListItem(
              headlineContent = { Text(folder.name) },
              leadingContent = {
                Icon(if (vm.locked(folder.id)) Icons.Default.Lock else Icons.Default.Folder, null)
              },
              modifier =
                Modifier.clickable {
                  if (vm.locked(folder.id)) unlock = folder.id else parent = folder.id
                },
            )
          }
        }
      }
    }
  }

  if (create)
    LibraryIdentityDialog(R.string.library_new_folder, dismiss = { create = false }) { name, display
      ->
      vm.perform({ parent = vm.library.createFolder(name, parent, display) }) { create = false }
    }
  unlock?.let { id ->
    LibraryPasswordDialog(R.string.library_unlock, false, vm.busy, { unlock = null }) { password ->
      vm.perform({ vm.library.unlock(id, password) }) {
        parent = id
        unlock = null
      }
    }
  }
}
