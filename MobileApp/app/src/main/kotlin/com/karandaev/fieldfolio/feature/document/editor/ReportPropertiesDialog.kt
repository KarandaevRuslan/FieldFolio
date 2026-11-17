@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karandaev.fieldfolio.feature.document.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAppDialog
import com.karandaev.fieldfolio.core.ui.scroll.verticalScroll
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.DocumentViewModel
import com.karandaev.fieldfolio.feature.document.viewer.DocumentImage
import com.karandaev.fieldfolio.feature.document.viewer.DocumentText

@Composable
fun ReportPropertiesDialog(
  vm: DocumentViewModel,
  dismiss: () -> Unit,
  addCover: () -> Unit,
  viewCover: () -> Unit,
) {
  val original =
    rememberSession(vm) {
      listOf(vm.title, vm.description, vm.document.cover, vm.displayName, vm.logicalName) to
        vm.dirty
    }
  val close =
    com.karandaev.fieldfolio.core.ui.components.dialog.rememberUnsavedClose(
      vm.title != original.first[0] ||
        vm.displayName != original.first[3] ||
        vm.logicalName != original.first[4] ||
        vm.description != original.first[1] ||
        vm.document.cover != original.first[2]
    ) {
      vm.displayName = original.first[3]
      vm.logicalName = original.first[4]
      vm.restoreProperties(
        original.first[0].orEmpty(),
        original.first[1].orEmpty(),
        original.first[2],
        original.second,
      )
      dismiss()
    }
  var code by rememberSession { mutableStateOf(false) }
  val fields: @Composable () -> Unit = {
    com.karandaev.fieldfolio.core.ui.components.fields.ResourceIdentityFields(
      com.karandaev.fieldfolio.core.names.ResourceIdentity(
        vm.logicalName ?: vm.title,
        vm.displayName,
      ),
      {
        vm.logicalName = it.name
        vm.title = it.displayName?.takeIf { alias -> alias.isNotBlank() } ?: it.name
        vm.displayName = it.displayName
        vm.changed()
      },
      tag = "report-logical-name",
    )
    Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
      Text(tr("description"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
      HintIconButton({ code = true }, tooltip = tr("edit_text")) {
        Icon(Icons.Default.Code, tr("edit_text"))
      }
    }
    if (vm.description.length <= 65536)
      OutlinedTextField(
        vm.description,
        {
          if (it.length <= 65536) {
            vm.description = it
            vm.changed()
          }
        },
        Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 6,
      )
    else DocumentText(vm.description, tr("description"))
  }
  val cover: @Composable () -> Unit = {
    vm.document.cover?.let {
      DocumentImage(
        vm.files[it],
        fillViewport = true,
        modifier = Modifier.fillMaxSize(),
        onClick = viewCover,
      )
    }
  }
  SafeAppDialog(close) {
    Scaffold(
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        TopAppBar(
          title = { Text(tr(if (vm.reportId == 0L) "new_report" else "report_properties")) },
          navigationIcon = {
            HintIconButton(close, tooltip = tr("close")) { Icon(Icons.Default.Close, tr("close")) }
          },
          actions = {
            com.karandaev.fieldfolio.core.ui.components.actions.EditorActionRow {
              HintIconButton(addCover, tooltip = tr("add_cover")) {
                Icon(Icons.Default.AddPhotoAlternate, tr("add_cover"))
              }
              if (vm.document.cover != null)
                HintIconButton({ vm.setCover(null) }, tooltip = tr("clear_cover")) {
                  Icon(Icons.Default.Delete, tr("clear_cover"))
                }
            }
            HintIconButton(
              dismiss,
              enabled =
                com.karandaev.fieldfolio.core.names
                  .ResourceIdentity(vm.logicalName ?: vm.title, vm.displayName)
                  .valid &&
                  runCatching {
                      com.karandaev.fieldfolio.core.library.LibraryTree.name(
                        (vm.logicalName ?: vm.title).trim()
                      )
                    }
                    .isSuccess,
              tooltip = tr("done"),
            ) {
              Icon(Icons.Default.Check, tr("done"))
            }
          },
          windowInsets = WindowInsets(0, 0, 0, 0),
        )
      },
    ) { padding ->
      BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        val portraitCoverHeight = (maxHeight * .55f).coerceAtLeast(220.dp)
        if (maxWidth >= 600.dp && maxWidth > maxHeight)
          Row(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
          ) {
            Column(
              Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              fields()
            }
            Box(Modifier.weight(1f).fillMaxHeight().testTag("report-cover-pane")) { cover() }
          }
        else
          Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            fields()
            Box(Modifier.fillMaxWidth().height(portraitCoverHeight)) { cover() }
          }
      }
    }
  }
  if (code)
    AcodeBlockEditor(
      DocumentNode(
        id = "report-description",
        kind = BlockKind.TEXT,
        name = tr("description"),
        body = vm.description,
      ),
      emptyMap(),
      { code = false },
      allowRename = false,
    ) { next, _ ->
      vm.description = next.body
      vm.changed()
      code = false
    }
}
