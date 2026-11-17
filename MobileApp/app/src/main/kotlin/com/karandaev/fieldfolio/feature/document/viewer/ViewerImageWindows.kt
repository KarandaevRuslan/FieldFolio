package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.runtime.*
import com.karandaev.fieldfolio.core.ui.state.ViewerSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.state.ViewerImageWindows

val LocalViewerImages = staticCompositionLocalOf<ViewerImageWindows?> { null }

@Composable
fun ViewerImageWindowHost(windows: ViewerImageWindows) {
  windows.text?.let { text ->
    CompositionLocalProvider(LocalViewerImages provides null) {
      ViewerSession(text.key) { TextReaderWindow(text.node, text.files, windows::closeText) }
    }
  }
  windows.image?.let { image ->
    CompositionLocalProvider(LocalViewerImages provides null) {
      ViewerSession(image.key) {
        ImageResultDialog(
          image.bytes,
          image.result,
          image.mask,
          image.resources,
          dismiss = windows::close,
        )
      }
    }
  }
}
