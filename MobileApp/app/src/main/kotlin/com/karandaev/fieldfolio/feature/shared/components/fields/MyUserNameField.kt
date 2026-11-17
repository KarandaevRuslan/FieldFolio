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
 * User name input field used by register screen.
 *
 * @param userName Current user name value.
 * @param enabled Whether the field is enabled.
 * @param onUserNameChange Called when user name changes.
 * @param onUserNameClear Called when clear button is clicked.
 * @param modifier Modifier applied to the field.
 */
@Composable
internal fun MyUserNameField(
  userName: String,
  enabled: Boolean,
  onUserNameChange: (String) -> Unit,
  onUserNameClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  MyTextField(
    value = userName,
    onValueChange = onUserNameChange,
    enabled = enabled,
    label = stringResource(R.string.ui_name),
    onClear = onUserNameClear,
    modifier = modifier,
    keyboardOptions =
      KeyboardOptions(
        capitalization = KeyboardCapitalization.Words,
        keyboardType = KeyboardType.Text,
      ),
  )
}
