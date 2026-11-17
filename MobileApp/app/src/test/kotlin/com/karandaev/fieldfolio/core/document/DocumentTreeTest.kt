package com.karandaev.fieldfolio.core.document

import com.karandaev.fieldfolio.core.image.preprocessing.LetterboxTransform
import com.karandaev.fieldfolio.core.models.decodeNmsRows
import org.junit.Assert.*
import org.junit.Test

class DocumentTreeTest {
  private fun node(id: String, parent: String? = null, order: Int = 0, folder: Boolean = false) =
    DocumentNode(id, parent, order, id, if (folder) BlockKind.FOLDER else BlockKind.TEXT)

  private fun tree(vararg nodes: DocumentNode) =
    DocumentTree(ReportDocument(nodes = nodes.toList()))

  @Test
  fun reorderAndMoveKeepContiguousSiblingOrder() {
    val original =
      tree(
        node("a"),
        node("b", order = 1),
        node("c", order = 2),
        node("f", order = 3, folder = true),
      )
    val reordered = DocumentTree(original.transfer(listOf("a"), null, 2))
    assertEquals(listOf("b", "c", "a", "f"), reordered.children(null).map { it.id })
    val moved = DocumentTree(reordered.transfer(listOf("a"), "f"))
    assertEquals(listOf("b", "c", "f"), moved.children(null).map { it.id })
    assertEquals(listOf(0, 1, 2), moved.children(null).map { it.order })
    assertEquals("a", moved.children("f").single().id)
    assertEquals(4, original.document.nodes.size)
  }

  @Test
  fun copyRebindsEveryDescendantAndSharesImmutableResources() {
    val original =
      tree(
        node("f", folder = true),
        node("nested", "f", folder = true),
        node("leaf", "nested").copy(asset = "resources/a.txt"),
      )
    val copied = DocumentTree(original.transfer(listOf("f", "leaf"), null, copy = true))
    assertEquals(6, copied.document.nodes.size)
    val root = copied.children(null).single { it.id != "f" }
    val nested = copied.children(root.id).single()
    val leaf = copied.children(nested.id).single()
    assertEquals("resources/a.txt", leaf.asset)
    assertTrue(listOf(root.id, nested.id, leaf.id).none { it in original.byId })
    val deleted = DocumentTree(copied.delete(listOf(root.id)))
    assertEquals(original.byId.keys, deleted.byId.keys)
  }

  @Test
  fun invalidCyclesAndNonFolderDestinationsDoNotMutateSource() {
    val original =
      tree(node("f", folder = true), node("nested", "f", folder = true), node("leaf", "nested"))
    assertThrows(IllegalArgumentException::class.java) { original.transfer(listOf("f"), "nested") }
    assertThrows(IllegalArgumentException::class.java) {
      original.transfer(listOf("nested"), "leaf")
    }
    assertThrows(IllegalArgumentException::class.java) {
      tree(node("f", "nested", folder = true), node("nested", "f", folder = true))
    }
    assertEquals(3, original.document.nodes.size)
  }

  @Test
  fun locationsDoNotInventOriginAndIncludeNestedWeatherAndFixedClocks() {
    val document =
      ReportDocument(
        nodes =
          listOf(
            node("text"),
            node("own-clock").copy(kind = BlockKind.CLOCK, deviceLocation = true),
            node("weather").copy(kind = BlockKind.WEATHER, deviceLocation = true),
            node("clock")
              .copy(
                kind = BlockKind.CLOCK,
                latitude = 55.0,
                longitude = 37.0,
                zoneId = "Europe/Moscow",
              ),
          )
      )
    assertEquals(listOf("weather", "clock"), document.locations().map { it.nodeId })
    assertTrue(ReportDocument().locations().isEmpty())
  }

  @Test
  fun embeddedNmsIgnoresPaddingRowsAndUnletterboxesCoordinates() {
    val transform = LetterboxTransform(1000, 500, 640, 640, .64f)
    val rows =
      floatArrayOf(
        .1f,
        .3f,
        .9f,
        .7f,
        .9f,
        2f,
        .1f,
        .2f,
        .9f,
        .8f,
        0f,
        0f,
        0f,
        0f,
        1f,
        1f,
        Float.NaN,
        1f,
      )
    val boxes = decodeNmsRows(rows, listOf("A", "B", "C"), transform)
    assertEquals(1, boxes.size)
    assertEquals("C", boxes[0].label)
    assertEquals(.1, boxes[0].xMin, .0001)
    assertEquals(.1, boxes[0].yMin, .0001)
    assertEquals(.9, boxes[0].yMax, .0001)
  }
}
