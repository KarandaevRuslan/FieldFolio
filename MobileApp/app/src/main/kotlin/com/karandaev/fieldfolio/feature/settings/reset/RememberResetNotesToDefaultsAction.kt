package com.karandaev.fieldfolio.feature.settings.reset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.karandaev.fieldfolio.core.util.other.toast
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val RESET_SUCCESS_MESSAGE = "Reports cleared."
private const val RESET_FAILURE_MESSAGE_PREFIX = "Reset failed"

/**
 * Creates a reusable report-clearing action for the settings screen.
 *
 * The returned lambda starts reset operation, updates loading state, shows user-facing toast
 * messages, and notifies the caller after successful reset.
 *
 * @param resetNotesToDefaults Suspended operation that performs the actual reset.
 * @param onResettingChange Called when reset operation starts or finishes.
 * @param onResetSuccess Called after successful reset.
 * @return Function that starts the report-clearing operation.
 */
@Composable
internal fun rememberResetNotesToDefaultsAction(
  resetNotesToDefaults: suspend () -> Unit,
  onResettingChange: (Boolean) -> Unit,
  onResetSuccess: () -> Unit = {},
): () -> Unit {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val currentResetNotesToDefaults by rememberUpdatedState(resetNotesToDefaults)
  val currentOnResettingChange by rememberUpdatedState(onResettingChange)
  val currentOnResetSuccess by rememberUpdatedState(onResetSuccess)
  val inProgress = remember { AtomicBoolean(false) }

  return remember {
    {
      if (inProgress.compareAndSet(false, true)) {
        coroutineScope.launch {
          try {
            currentOnResettingChange(true)
            currentResetNotesToDefaults()
            context.toast(RESET_SUCCESS_MESSAGE)
            currentOnResetSuccess()
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Exception) {
            context.toast("$RESET_FAILURE_MESSAGE_PREFIX: ${error.message ?: "unknown error"}")
          } finally {
            currentOnResettingChange(false)
            inProgress.set(false)
          }
        }
      }
    }
  }
}
