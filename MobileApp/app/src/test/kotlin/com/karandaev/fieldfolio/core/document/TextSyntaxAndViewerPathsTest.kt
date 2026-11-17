package com.karandaev.fieldfolio.core.document

import android.app.Application
import com.karandaev.fieldfolio.core.json.MoshiFactory
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class TextSyntaxAndViewerPathsTest {
  private fun presentation(): DocumentPresentation {
    val nodes =
      listOf(
        DocumentNode(id = "shown", name = "Visible", kind = BlockKind.FOLDER),
        DocumentNode(
          id = "text",
          parentId = "shown",
          name = "Text",
          kind = BlockKind.TEXT,
          body = "Content",
        ),
        DocumentNode(id = "hidden", name = "Hidden", kind = BlockKind.FOLDER, visible = false),
        DocumentNode(id = "secret", parentId = "hidden", name = "Child", kind = BlockKind.FOLDER),
        DocumentNode(id = "empty", name = "Empty", kind = BlockKind.FOLDER, hideTitle = true),
        DocumentNode(id = "ghost", name = "Ghost", kind = BlockKind.FOLDER, hideTitle = true),
        DocumentNode(
          id = "blank",
          parentId = "ghost",
          name = "Blank",
          kind = BlockKind.TEXT,
          hideTitle = true,
          body = " ",
        ),
        DocumentNode(id = "untitled", name = "Untitled", kind = BlockKind.FOLDER, hideTitle = true),
        DocumentNode(
          id = "real",
          parentId = "untitled",
          name = "Real",
          kind = BlockKind.TEXT,
          body = "Actual content",
        ),
      )
    return DocumentPresentation(DocumentTree(ReportDocument(nodes = nodes)), emptyMap())
  }

  @Test
  fun viewerPathsAcceptOnlyDisplayedFoldersAndNeverOpenLeaves() {
    val p = presentation()
    assertEquals("shown", p.resolveViewerPath("/Visible"))
    assertEquals("untitled", p.resolveViewerPath("/Untitled"))
    assertNull(p.resolveViewerPath("..", "shown"))
    assertNull(p.resolveViewerPath("/"))
    for (path in
      listOf("/Hidden", "/Hidden/Child", "/Empty", "/Ghost", "/Visible/Text")) assertThrows(
      IllegalArgumentException::class.java
    ) {
      p.resolveViewerPath(path)
    }
  }

  @Test
  fun hiddenFolderCannotBeTraversedEvenIfTheFinalDestinationWouldBeVisible() {
    val p = presentation()
    assertThrows(IllegalArgumentException::class.java) { p.resolveViewerPath("/Hidden/../Visible") }
    assertThrows(IllegalArgumentException::class.java) { p.resolveViewerPath(".", "hidden") }
    assertEquals(
      "shown",
      p.tree.resolvePath("/Hidden/../Visible"),
    ) // editor policy remains unchanged
  }

  @Test
  fun syntaxHintRoundTripsIndependentlyOfBothNames() {
    val n =
      DocumentNode(
        name = "Logical",
        displayName = "Visual",
        kind = BlockKind.TEXT,
        syntaxExtension = "js",
        body = "const a=1",
      )
    val adapter = MoshiFactory.create().adapter(ReportDocument::class.java)
    val next = adapter.fromJson(adapter.toJson(ReportDocument(nodes = listOf(n))))!!.nodes.single()
    assertEquals(n, next)
    assertEquals("document.js", textSyntaxName(next))
    assertEquals("Logical", next.name)
  }

  @Test
  fun unsafeSyntaxHintsAreRejectedByDocumentValidation() {
    for (ext in listOf("../js", "a/b", "JS", "")) assertThrows(
      IllegalArgumentException::class.java
    ) {
      DocumentTree(
        ReportDocument(
          nodes = listOf(DocumentNode(name = "Text", kind = BlockKind.TEXT, syntaxExtension = ext))
        )
      )
    }
  }
}
