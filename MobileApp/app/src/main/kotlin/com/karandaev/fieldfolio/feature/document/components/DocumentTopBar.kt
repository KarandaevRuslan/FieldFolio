@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.material.ExperimentalMaterialApi::class,
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.karandaev.fieldfolio.feature.document.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.*
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconToggleButton
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import com.karandaev.fieldfolio.feature.library.LibraryViewModel
import kotlinx.coroutines.*

/** Standard editor/viewer toolbar; the destructive action never occupies the Save position. */
@Composable
internal fun DocumentTopBar(
  vm: DocumentViewModel,
  editing: Boolean,
  reportId: Long,
  transfers: com.karandaev.fieldfolio.feature.library.LibraryTransferActions,
  libraryActions: LibraryViewModel,
  headerVisible: Boolean,
  headerLoaded: Boolean,
  onHeaderVisible: (Boolean) -> Unit,
  onBack: () -> Unit,
  onProperties: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  var passwordOptions by rememberSession { mutableStateOf(false) }
  TopAppBar(
    title = {
      Column {
        Text(
          if (editing) tr("editor")
          else
            (vm.displayName?.takeIf { it.isNotBlank() } ?: vm.title).ifBlank { tr("new_report") },
          maxLines = 1,
        )
        if (editing && vm.dirty) Text(tr("unsaved"), style = MaterialTheme.typography.labelSmall)
      }
    },
    navigationIcon = {
      HintIconButton(onBack, tooltip = tr("back")) { Icon(Icons.Default.ArrowBack, tr("back")) }
    },
    windowInsets = WindowInsets(0, 0, 0, 0),
    actions = {
      if (editing) {
        HintIconButton(
          onProperties,
          enabled = !vm.loading && !vm.busy,
          tooltip = tr("report_properties"),
        ) {
          Icon(Icons.Default.Tune, tr("report_properties"))
        }
        HintIconButton(
          { vm.save() },
          enabled = vm.title.isNotBlank() && !vm.busy && !vm.loading,
          tooltip = tr("save"),
        ) {
          Icon(Icons.Default.Save, tr("save"))
        }
      } else {
        HintIconButton(
          { transfers.export(listOf(reportId)) },
          enabled = reportId > 0 && !vm.loading && !transfers.busy,
          tooltip = tr("export"),
        ) {
          Icon(com.karandaev.fieldfolio.core.ui.FileTransferIcons.Export, tr("export"))
        }
        Box {
          HintIconButton(
            {
              if (libraryActions.nodes.firstOrNull { it.id == reportId }?.lockId != null)
                passwordOptions = true
              else libraryActions.password = reportId
            },
            enabled = reportId > 0 && !vm.loading && !libraryActions.busy,
            tooltip = stringResource(R.string.library_password),
          ) {
            Icon(Icons.Default.Lock, stringResource(R.string.library_password))
          }
          DropdownMenu(passwordOptions, { passwordOptions = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.library_change_password)) },
              onClick = {
                passwordOptions = false
                libraryActions.password = reportId
              },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.library_remove_password)) },
              onClick = {
                passwordOptions = false
                libraryActions.removePassword = reportId
              },
            )
          }
        }
        HintIconButton(onEdit, tooltip = tr("edit")) { Icon(Icons.Default.Edit, tr("edit")) }
        HintIconButton(onDelete, tooltip = tr("report_delete")) {
          Icon(Icons.Default.Delete, tr("report_delete"))
        }
        HintIconToggleButton(
          headerVisible,
          { visible -> onHeaderVisible(visible) },
          enabled = headerLoaded,
          tooltip =
            stringResource(
              if (headerVisible) R.string.polish_hide_header else R.string.polish_show_header
            ),
        ) {
          Icon(
            if (headerVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            stringResource(
              if (headerVisible) R.string.polish_hide_header else R.string.polish_show_header
            ),
          )
        }
      }
    },
  )
}
