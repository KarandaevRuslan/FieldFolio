package com.karandaev.fieldfolio.feature.document.state

import androidx.compose.runtime.*
import com.karandaev.fieldfolio.core.document.ModelResultData

/** Owned by the report, never by a recycled grid cell. Contains no Activity or View. */
class ViewerImageWindows {
  data class Image(
    val key: String,
    val bytes: ByteArray,
    val result: ModelResultData?,
    val mask: ByteArray?,
    val resources: Map<String, ByteArray>,
    val dismiss: () -> Unit,
  )

  data class Text(
    val key: String,
    val node: com.karandaev.fieldfolio.core.document.DocumentNode,
    val files: Map<String, ByteArray>,
    val dismiss: () -> Unit,
  )

  var text by mutableStateOf<Text?>(null)
    private set

  fun showText(value: Text) {
    text = value
  }

  fun closeText() {
    val old = text
    text = null
    old?.dismiss?.invoke()
  }

  var image by mutableStateOf<Image?>(null)
    private set

  fun show(value: Image) {
    image = value
  }

  fun close() {
    val old = image
    image = null
    old?.dismiss?.invoke()
  }

  fun clear() {
    text = null
    image = null
  }
}
