package com.karandaev.fieldfolio.core.ui.components.bars

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal const val PasswordStrengthMaxScore = 100
internal const val PasswordStrengthSegmentCount = 5

/** UI-ready password strength state. */
internal data class PasswordStrengthState(
  val normalizedStrength: Int,
  val filledSegments: Int,
  val level: PasswordStrengthLevel,
)

/** Password strength visual level. */
internal data class PasswordStrengthLevel(
  val label: String,
  val color: Color,
  val contentColor: Color,
)

/** Resolves password strength UI state from raw strength value. */
@Composable
internal fun resolvePasswordStrengthState(strength: Int): PasswordStrengthState {
  val normalizedStrength =
    strength.coerceIn(minimumValue = 0, maximumValue = PasswordStrengthMaxScore)

  return PasswordStrengthState(
    normalizedStrength = normalizedStrength,
    filledSegments = normalizedStrength.toFilledSegments(),
    level = normalizedStrength.toPasswordStrengthLevel(),
  )
}

private fun Int.toFilledSegments(): Int {
  if (this <= 0) return 0

  return ((this * PasswordStrengthSegmentCount) + PasswordStrengthMaxScore - 1) /
    PasswordStrengthMaxScore
}

@Composable
private fun Int.toPasswordStrengthLevel(): PasswordStrengthLevel {
  return when {
    this <= 0 ->
      PasswordStrengthLevel(
        label =
          androidx.compose.ui.res.stringResource(
            com.karandaev.fieldfolio.R.string.ui_strength_empty
          ),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
      )

    this < 40 ->
      PasswordStrengthLevel(
        label =
          androidx.compose.ui.res.stringResource(
            com.karandaev.fieldfolio.R.string.ui_strength_weak
          ),
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
      )

    this < 70 ->
      PasswordStrengthLevel(
        label =
          androidx.compose.ui.res.stringResource(
            com.karandaev.fieldfolio.R.string.ui_strength_medium
          ),
        color = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
      )

    this < PasswordStrengthMaxScore ->
      PasswordStrengthLevel(
        label =
          androidx.compose.ui.res.stringResource(
            com.karandaev.fieldfolio.R.string.ui_strength_strong
          ),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
      )

    else ->
      PasswordStrengthLevel(
        label =
          androidx.compose.ui.res.stringResource(
            com.karandaev.fieldfolio.R.string.ui_strength_perfect
          ),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
      )
  }
}
