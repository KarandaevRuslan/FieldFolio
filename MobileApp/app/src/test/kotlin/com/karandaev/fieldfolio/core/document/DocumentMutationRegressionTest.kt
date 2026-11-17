package com.karandaev.fieldfolio.core.document

import com.karandaev.fieldfolio.core.json.MoshiFactory
import com.karandaev.fieldfolio.core.names.ItemNames
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** Exercises the public tree/serialization contract independently of composable implementation. */
class DocumentMutationRegressionTest {
  private fun assertInvariants(document: ReportDocument) {
    val tree = DocumentTree(document)
    assertEquals(document.nodes.size, tree.byId.size)
    for (parent in
      listOf<String?>(null) + document.nodes.filter { it.kind == BlockKind.FOLDER }.map { it.id }) {
      val children = tree.children(parent)
      assertEquals(children.indices.toList(), children.map { it.order })
      assertEquals(children.size, children.map { it.name.lowercase() }.distinct().size)
      assertTrue(children.all { ItemNames.valid(it.name) })
    }
  }

  @Test
  fun repeatedCopyMoveRenameSequencesKeepSiblingNamesAndOrderValid() {
    var document =
      ReportDocument(
        nodes =
          listOf(
            DocumentNode(id = "folder-a", name = "A", kind = BlockKind.FOLDER),
            DocumentNode(id = "folder-b", name = "B", kind = BlockKind.FOLDER, order = 1),
            DocumentNode(
              id = "leaf",
              name = "same.txt",
              kind = BlockKind.TEXT_FILE,
              order = 2,
              asset = "resources/original.txt",
              displayName = "A visible title",
            ),
          )
      )
    val random = Random(937281)
    repeat(120) { index ->
      val tree = DocumentTree(document)
      val leaf = document.nodes.filter { it.kind == BlockKind.TEXT_FILE }.random(random)
      val target = listOf<String?>(null, "folder-a", "folder-b").random(random)
      document =
        if (index % 3 == 0) tree.update(leaf.copy(name = "same.txt"))
        else tree.transfer(listOf(leaf.id), target, copy = index % 2 == 0)
      assertInvariants(document)
    }
    assertEquals(setOf("resources/original.txt"), document.resourcePaths())
    assertTrue(
      document.nodes
        .filter { it.kind == BlockKind.TEXT_FILE }
        .all { it.displayName == "A visible title" }
    )
  }

  @Test
  fun rejectedStructuralChangesLeaveTheOriginalSnapshotIntact() {
    val original =
      ReportDocument(
        nodes =
          listOf(
            DocumentNode(id = "folder", name = "Folder", kind = BlockKind.FOLDER),
            DocumentNode(id = "child", parentId = "folder", name = "Child", kind = BlockKind.FOLDER),
          )
      )
    val tree = DocumentTree(original)
    assertThrows(IllegalArgumentException::class.java) { tree.transfer(listOf("folder"), "child") }
    assertThrows(IllegalArgumentException::class.java) {
      tree.update(tree.node("child").copy(name = " "))
    }
    assertEquals(original, tree.document)
    assertInvariants(original)
  }

  @Test
  fun everyElementKindAndItsPresentationFlagsSurviveGeneratedJsonAdapters() {
    val nodes =
      BlockKind.entries
        .filter { it != BlockKind.FOLDER }
        .mapIndexed { index, kind ->
          DocumentNode(
            id = kind.name,
            name = "Element $index",
            kind = kind,
            order = index,
            body = "source",
            displayName = "Caption $index",
            hideTitle = index % 2 == 0,
            visible = index % 3 != 0,
            result =
              if (kind == BlockKind.MODEL_RESULT)
                ModelResultData(
                  modelId = "local",
                  modelName = "Saved model",
                  provider = "local",
                  task = "segmentation",
                  inputAsset = "resources/input.png",
                  maskAsset = "resources/mask.png",
                  values = listOf(ResultValue("size", 2.5, "m")),
                )
              else null,
          )
        }
    val original = ReportDocument(nodes = nodes)
    val adapter = MoshiFactory.create().adapter(ReportDocument::class.java)
    val restored = adapter.fromJson(adapter.toJson(original))!!
    assertEquals(original, restored)
    assertEquals(original.resourcePaths(), restored.resourcePaths())
    assertInvariants(restored)
  }
}
