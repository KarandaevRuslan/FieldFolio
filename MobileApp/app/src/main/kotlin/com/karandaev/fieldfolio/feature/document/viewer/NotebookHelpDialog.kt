package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.document.runtime.*
import com.karandaev.fieldfolio.core.ui.state.rememberSession
import com.karandaev.fieldfolio.core.ui.text.*
import kotlinx.coroutines.*

/** Documentation uses the same isolated reader, theme, viewport and search as Document. */
@Composable
fun NotebookHelpDialog(dismiss: () -> Unit) {
  val context = LocalContext.current
  val language =
    LocalConfiguration.current.locales[0].language.let { if (it == "ru") "ru" else "en" }
  val body by
    produceState<String?>(null, language) {
      value =
        withContext(Dispatchers.IO) {
          context.assets.open("help/document-$language.md").bufferedReader().use { it.readText() }
        }
    }
  val source = body ?: return
  val node =
    remember(source) {
      DocumentNode(
        id = "help-$language",
        name = if (language == "ru") "Справка" else "Help",
        kind = BlockKind.DOCUMENT,
        body =
          "<notebook><script id=\"1\" type=\"text/markdown\">" +
            source.replace("</script", "<\\/script", ignoreCase = true) +
            "</script></notebook>",
      )
    }
  val access =
    rememberSession(node) {
      DocumentAccessContext("help-$language", ReportDocument(nodes = listOf(node)), emptyMap())
    }
  CompositionLocalProvider(LocalDocumentAccess provides access) {
    FullNotebookDialog(node, access, true, dismiss)
  }
}
