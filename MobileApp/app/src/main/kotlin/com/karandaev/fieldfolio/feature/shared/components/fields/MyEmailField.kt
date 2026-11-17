package com.karandaev.fieldfolio.feature.shared.components.fields

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.karandaev.fieldfolio.R
import com.karandaev.fieldfolio.core.ui.components.fields.MyTextField

/**
 * Email input field used by auth screens.
 *
 * @param email Current email value.
 * @param enabled Whether the field is enabled.
 * @param onEmailChange Called when email changes.
 * @param onEmailClear Called when clear button is clicked.
 * @param modifier Modifier applied to the field.
 */
@Composable
internal fun MyEmailField(
  email: String,
  enabled: Boolean,
  onEmailChange: (String) -> Unit,
  onEmailClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  MyTextField(
    value = email,
    onValueChange = onEmailChange,
    enabled = enabled,
    label = stringResource(R.string.ui_email),
    onClear = onEmailClear,
    modifier = modifier,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
  )
}
