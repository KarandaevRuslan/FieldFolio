package com.karandaev.fieldfolio.feature.auth.components.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Auth screen header. */
@Composable
internal fun AuthHeader(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.headlineSmall,
    color = MaterialTheme.colorScheme.onSurface,
  )
}
