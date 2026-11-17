package com.karandaev.fieldfolio.feature.models.legacy

import com.karandaev.fieldfolio.core.ui.error.AppErrorMapper

/** Converts detection errors into user-friendly messages. */
internal object DetectErrorMapper {

  /** Returns a user-friendly message for the provided detection error. */
  fun toMessage(error: Throwable): String {
    return AppErrorMapper.toMessage(
      error = error,
      fallbackMessage = "Detection failed. Please try again.",
    )
  }
}
