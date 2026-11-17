package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.ui.components.dialog.SafeAlertDialog
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr

@Composable
fun PageJumpDialog(current: Int, count: Int?, dismiss: () -> Unit, choose: (Int) -> Unit) {
  var value by rememberSession { mutableStateOf(current.toString()) }
  val page = value.toIntOrNull()
  SafeAlertDialog(
    dismiss,
    title = { Text(stringResource(R.string.polish_go_page)) },
    text = {
      OutlinedTextField(
        value,
        { value = it.filter(Char::isDigit).take(7) },
        Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        label = { Text(if (count == null) stringResource(R.string.polish_page) else "1–$count") },
      )
    },
    confirmButton = {
      TextButton(
        { choose(page!!) },
        enabled = page != null && page > 0 && (count == null || page <= count),
      ) {
        Text(tr("select"))
      }
    },
    dismissButton = { TextButton(dismiss) { Text(tr("cancel")) } },
  )
}
