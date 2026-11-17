package com.karandaev.fieldfolio.feature.auth.state

import com.karandaev.fieldfolio.core.ui.error.AppErrorMapper

/** Converts authentication errors into user-friendly messages. */
internal object AuthErrorMapper {

  /** Returns a user-friendly message for the provided authentication error. */
  fun toMessage(error: Throwable): String {
    return AppErrorMapper.toMessage(
      error = error,
      fallbackMessage = "Authentication failed. Please try again.",
    )
  }
}
