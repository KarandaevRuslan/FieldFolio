package com.karandaev.fieldfolio.core.document

import com.karandaev.fieldfolio.core.json.MoshiFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DocumentPresentationTest {
  @Test
  fun emptyHiddenTitleIsDifferentFromAnUnsetAlias() {
    val empty =
      DocumentNode(id = "empty", name = "Logical", kind = BlockKind.TEXT, hideTitle = true)
    val named = empty.copy(id = "named", order = 1, hideTitle = false)
    val body = empty.copy(id = "body", order = 2, body = "Content")
    val p =
      DocumentPresentation(
        DocumentTree(ReportDocument(nodes = listOf(empty, named, body))),
        emptyMap(),
      )
    assertEquals("Logical", empty.displayLabel)
    assertEquals("", empty.visualTitle)
    assertEquals(listOf("named", "body"), p.children(null).map { it.id })
    assertFalse(p.hasVisual(empty))
  }

  @Test
  fun hiddenFolderSuppressesItsSubtreeEvenWhenOpeningADescendantDirectly() {
    val folder =
      DocumentNode(id = "folder", name = "Folder", kind = BlockKind.FOLDER, visible = false)
    val nested = folder.copy(id = "nested", parentId = "folder", name = "Nested", visible = true)
    val text =
      DocumentNode(
        id = "text",
        parentId = "nested",
        name = "Text",
        kind = BlockKind.TEXT,
        body = "Keep me",
      )
    val p =
      DocumentPresentation(
        DocumentTree(ReportDocument(nodes = listOf(folder, nested, text))),
        emptyMap(),
      )
    assertTrue(p.children(null).isEmpty())
    assertTrue(p.children("folder").isEmpty())
    assertTrue(p.children("nested").isEmpty())
    assertEquals("Keep me", p.tree.node("text").body)
  }

  @Test
  fun emptyResourcesAndNestedEmptyFoldersDoNotCreateBlankCells() {
    val folder = DocumentNode(id = "f", name = "Folder", kind = BlockKind.FOLDER, hideTitle = true)
    val file =
      DocumentNode(
        id = "t",
        parentId = "f",
        name = "Blank.txt",
        kind = BlockKind.TEXT_FILE,
        asset = "blank",
        hideTitle = true,
      )
    val files = mapOf("blank" to (byteArrayOf(-1, -2) + " \n\t".toByteArray(Charsets.UTF_16LE)))
    val tree = DocumentTree(ReportDocument(nodes = listOf(folder, file)))
    assertTrue(DocumentPresentation(tree, files).children(null).isEmpty())
    val changed = files + ("blank" to (" ".repeat(20000) + "X").toByteArray())
    assertEquals(listOf("f"), DocumentPresentation(tree, changed).children(null).map { it.id })
  }

  @Test
  fun visibleMediaAndHeadingBodySurviveAnEmptyDisplayTitle() {
    val heading =
      DocumentNode(
        id = "h",
        name = "Heading",
        kind = BlockKind.HEADING,
        body = "Heading content",
        hideTitle = true,
      )
    val image =
      DocumentNode(
        id = "i",
        name = "Image",
        kind = BlockKind.IMAGE,
        asset = "image",
        hideTitle = true,
      )
    val p =
      DocumentPresentation(
        DocumentTree(ReportDocument(nodes = listOf(heading, image))),
        mapOf("image" to byteArrayOf(1)),
      )
    assertEquals(2, p.children(null).size)
  }

  @Test
  fun aliasesVisibilityAndEmptyTitlesSurviveSerializationAndSubtreeCopy() {
    val original =
      DocumentNode(
        id = "f",
        name = "Folder",
        kind = BlockKind.FOLDER,
        displayName = "Alias",
        hideTitle = true,
        visible = false,
      )
    val tree = DocumentTree(ReportDocument(nodes = listOf(original)))
    val copied = tree.transfer(listOf("f"), null, copy = true)
    val adapter = MoshiFactory.create().adapter(ReportDocument::class.java)
    val loaded = adapter.fromJson(adapter.toJson(copied))!!
    assertEquals(listOf("Folder", "Folder (2)"), loaded.nodes.map { it.name })
    assertTrue(loaded.nodes.all { it.hideTitle && !it.visible && it.displayName == "Alias" })
    assertEquals("Folder", loaded.nodes.first().identity.name)
  }

  @Test
  fun malformedTextIsNotSilentlyDiscardedByTheEmptyContentFilter() {
    assertTrue(TextResourceCodec.hasVisibleText(byteArrayOf(0xff.toByte(), 0x33)))
    assertFalse(TextResourceCodec.hasVisibleText(byteArrayOf()))
  }

  @Test
  fun measuredScrollRangeKeepsTallEarlierLanesAndSurvivesViewportChanges() {
    val cache = com.karandaev.fieldfolio.core.ui.scroll.LazyExtentCache()
    val items =
      listOf(2200f, 40f, 40f, 40f).mapIndexed { i, h ->
        com.karandaev.fieldfolio.core.ui.scroll.ScrollItemRect(i, 0f, h)
      }
    assertEquals(1924f, cache.range(items, 4, 300f, 12f, 24f, 2), .1f)
    assertEquals(
      1924f,
      cache.range(items.map { it.copy(top = it.top - 100) }, 4, 300f, 12f, 24f, 2),
      .1f,
    )
    assertEquals(1524f, cache.range(items, 4, 700f, 12f, 24f, 2), .1f)
    assertTrue(cache.range(listOf(items.first()), 100, 300f, 12f, 24f, 2) < 12000f)
  }
}
