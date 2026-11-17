package com.karandaev.fieldfolio.core.ui.components.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R

@Composable
fun AppBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  HintIconButton(
    onClick = onClick,
    modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface),
    tooltip = stringResource(R.string.ui_back),
  ) {
    Icon(
      imageVector = Icons.AutoMirrored.Filled.ArrowBack,
      contentDescription = stringResource(R.string.ui_back),
      tint = MaterialTheme.colorScheme.onSurface,
    )
  }
}
