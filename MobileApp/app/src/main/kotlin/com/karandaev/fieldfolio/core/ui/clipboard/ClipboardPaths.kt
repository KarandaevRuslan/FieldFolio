package com.karandaev.fieldfolio.core.ui.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Copies one canonical logical path without changing the in-app transfer clipboard. */
internal fun copyPathToClipboard(context: Context, path: String) {
  (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
    ClipData.newPlainText("Fieldfolio", path)
  )
}
