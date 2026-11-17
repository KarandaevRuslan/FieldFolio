package com.karandaev.fieldfolio.core.ui.lifecycle

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Finds an [Activity] from a [Context] when the current Compose context is wrapped. */
fun Context.findActivity(): Activity? {
  var currentContext = this

  while (currentContext is ContextWrapper) {
    if (currentContext is Activity) {
      return currentContext
    }

    currentContext = currentContext.baseContext
  }

  return null
}
