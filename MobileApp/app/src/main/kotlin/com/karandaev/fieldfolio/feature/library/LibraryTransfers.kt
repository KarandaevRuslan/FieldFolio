package com.karandaev.fieldfolio.feature.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAlertDialog
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

internal data class LibraryTransferActions(
  val export: (List<Long>) -> Unit,
  val import: (Long?) -> Unit,
  val busy: Boolean,
  val isExporting: Boolean,
  val isImporting: Boolean,
)

private data class PendingImport(val uri: Uri, val parent: Long?)

@Composable
internal fun rememberLibraryTransfers(library: ReportLibrary): LibraryTransferActions {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var exporting by rememberSaveable { mutableStateOf<List<Long>?>(null) }
  var destination by rememberSaveable { mutableStateOf<Long?>(null) }
  var choosingImport by rememberSession { mutableStateOf(false) }
  var pendingImport by rememberSession { mutableStateOf<PendingImport?>(null) }
  var pendingExportPath by rememberSaveable { mutableStateOf<String?>(null) }
  var operation by rememberSession { mutableStateOf("") }
  var busy by rememberSession { mutableStateOf(false) }
  fun message(id: Int) {
    Toast.makeText(context, id, Toast.LENGTH_LONG).show()
  }
  fun failure(e: Exception) {
    message(libraryErrorResource(e))
  }
  fun importFile(request: PendingImport, password: CharArray? = null) {
    if (busy) {
      password?.fill('\u0000')
      return
    }
    operation = "import"
    busy = true
    scope.launch {
      try {
        library.importArchive(request.uri, request.parent, password)
        pendingImport = null
        message(R.string.library_imported)
      } catch (e: CancellationException) {
        throw e
      } catch (_: LibraryArchivePasswordRequired) {
        pendingImport = request
      } catch (e: Exception) {
        failure(e)
      } finally {
        password?.fill('\u0000')
        busy = false
      }
    }
  }
  val create =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
      library.externalPicker = false
      val file = pendingExportPath?.let(::File)
      pendingExportPath = null
      if (uri == null || file == null) {
        file?.delete()
      } else {
        operation = "export"
        busy = true
        scope.launch {
          try {
            withContext(Dispatchers.IO) {
              context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                file.inputStream().use { it.copyTo(output) }
              } ?: error("Cannot write archive")
            }
            message(R.string.library_exported)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            failure(e)
          } finally {
            file.delete()
            busy = false
          }
        }
      }
    }
  val open =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      library.externalPicker = false
      if (uri != null) importFile(PendingImport(uri, destination))
    }
  val legacy =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      library.externalPicker = false
      if (uri != null) {
        operation = "import"
        busy = true
        scope.launch {
          try {
            library.importLegacyDirectory(uri, destination)
            message(R.string.library_imported)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            failure(e)
          } finally {
            busy = false
          }
        }
      }
    }
  DisposableEffect(Unit) {
    onDispose {
      if ((context as? android.app.Activity)?.isChangingConfigurations != true) {
        pendingExportPath?.let(::File)?.delete()
        library.externalPicker = false
      }
    }
  }
  exporting?.let { ids ->
    ExportArchiveDialog(busy, { if (!busy) exporting = null }) { password ->
      operation = "export"
      busy = true
      scope.launch {
        try {
          val encrypted = password != null
          pendingExportPath = library.prepareArchiveFile(ids, password).absolutePath
          exporting = null
          val name =
            "Fieldfolio-" +
              SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date()) +
              if (encrypted) ".gipack" else ".zip"
          library.externalPicker = true
          create.launch(name)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          failure(e)
        } finally {
          password?.fill('\u0000')
          busy = false
        }
      }
    }
  }
  pendingImport?.let { request ->
    LibraryPasswordDialog(
      R.string.library_archive_password,
      false,
      busy,
      { pendingImport = null },
    ) {
      importFile(request, it)
    }
  }
  if (choosingImport)
    SafeAlertDialog(
      onDismissRequest = { choosingImport = false },
      title = { Text(stringResource(R.string.library_import)) },
      text = {
        Column {
          TextButton({
            choosingImport = false
            library.externalPicker = true
            open.launch(
              arrayOf(
                "application/zip",
                "application/octet-stream",
                "application/json",
                "text/plain",
              )
            )
          }) {
            Text(stringResource(R.string.library_import))
          }
          TextButton({
            choosingImport = false
            library.externalPicker = true
            legacy.launch(null)
          }) {
            Text(stringResource(R.string.library_legacy_import))
          }
        }
      },
      confirmButton = {
        TextButton({ choosingImport = false }) { Text(stringResource(R.string.library_cancel)) }
      },
    )
  return LibraryTransferActions(
    { ids -> if (!busy && ids.isNotEmpty()) exporting = ids },
    { parent ->
      if (!busy) {
        destination = parent
        choosingImport = true
      }
    },
    busy,
    busy && operation == "export",
    busy && operation == "import",
  )
}

@Composable
internal fun ExportArchiveDialog(busy: Boolean, dismiss: () -> Unit, export: (CharArray?) -> Unit) {
  var encrypted by rememberSession { mutableStateOf(false) }
  var value by rememberSession { mutableStateOf("") }
  var repeat by rememberSession { mutableStateOf("") }
  val close =
    com.karandaev.fieldfolio.core.ui.components.dialog.rememberUnsavedClose(
      encrypted || value.isNotEmpty() || repeat.isNotEmpty()
    ) {
      if (!busy) {
        value = ""
        repeat = ""
        dismiss()
      }
    }
  com.karandaev.fieldfolio.core.ui.components.dialog.AdaptiveFormDialog(
    stringResource(R.string.library_export),
    close,
    properties =
      DialogProperties(dismissOnClickOutside = false, securePolicy = SecureFlagPolicy.SecureOn),
    actions = {
      HintIconButton(
        {
          val password = if (encrypted) value.toCharArray() else null
          value = ""
          repeat = ""
          export(password)
        },
        enabled =
          !busy &&
            (!encrypted ||
              com.karandaev.fieldfolio.core.library.LibraryPasswordPolicy.accepts(value) &&
                value == repeat),
        tooltip = stringResource(R.string.library_export),
      ) {
        Icon(
          com.karandaev.fieldfolio.core.ui.FileTransferIcons.Export,
          stringResource(R.string.library_export),
        )
      }
    },
    sharedScroll = true,
    left = {
      ListItem(
        headlineContent = { Text(stringResource(R.string.library_password_archive)) },
        trailingContent = { Switch(encrypted, { encrypted = it }, enabled = !busy) },
      )
      if (encrypted) {
        LibraryPasswordField(value, { value = it })
        LibraryPasswordField(repeat, { repeat = it }, R.string.library_password_repeat)
        if (repeat.isNotEmpty() && value != repeat)
          Text(
            stringResource(R.string.finish_password_mismatch),
            color = MaterialTheme.colorScheme.error,
          )
      }
      if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    },
    right = {
      OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(stringResource(R.string.library_export_help))
          if (encrypted) LibraryPasswordGuidance(value)
        }
      }
    },
  )
}
