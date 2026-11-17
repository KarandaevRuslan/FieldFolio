package com.karandaev.fieldfolio.core.util.other

import android.content.Context
import android.widget.Toast
import com.karandaev.fieldfolio.core.localization.localizeMessage

/**
 * Shows a short toast message.
 *
 * @param message Message that should be displayed to the user.
 */
internal fun Context.toast(message: String) {
  Toast.makeText(
      this,
      com.karandaev.fieldfolio.core.localization.AppLanguage.wrap(this).localizeMessage(message),
      Toast.LENGTH_SHORT,
    )
    .show()
}
