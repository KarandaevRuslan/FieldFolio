package com.karandaev.fieldfolio.feature.shared.components.fields

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.ui.components.fields.MyTextField

/**
 * Server base URL input field.
 *
 * @param serverBaseUrl Current server base URL value.
 * @param enabled Whether the field is enabled.
 * @param onServerBaseUrlChange Called when server base URL changes.
 * @param onServerBaseUrlClear Called when clear button is clicked.
 * @param modifier Modifier applied to the field.
 */
@Composable
internal fun MyServerBaseUrlField(
  serverBaseUrl: String,
  enabled: Boolean,
  onServerBaseUrlChange: (String) -> Unit,
  onServerBaseUrlClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  MyTextField(
    value = serverBaseUrl,
    onValueChange = onServerBaseUrlChange,
    enabled = enabled,
    label = stringResource(R.string.ui_server_base_url),
    onClear = onServerBaseUrlClear,
    modifier = modifier,
    keyboardOptions =
      KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri),
  )
}
