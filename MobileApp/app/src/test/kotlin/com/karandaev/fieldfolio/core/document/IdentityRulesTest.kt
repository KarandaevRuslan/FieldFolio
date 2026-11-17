package com.karandaev.fieldfolio.core.document

import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.names.ItemNames
import com.karandaev.fieldfolio.core.ui.scroll.ScrollCalibration
import org.junit.Assert.*
import org.junit.Test
import org.tensorflow.lite.DataType

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class IdentityRulesTest {
  @Test
  fun namesAreRequiredAndCaseAndUnicodeCollisionsGetSuffixes() {
    for (name in listOf("", "  ", ".", "..", "a\nb")) assertFalse(ItemNames.valid(name))
    assertEquals("photo (2).jpg", ItemNames.unique("photo.jpg", listOf("PHOTO.JPG")))
    assertEquals("e\u0301 (2)", ItemNames.unique("e\u0301", listOf("é")))
    assertEquals("file (3).txt", ItemNames.unique("file.txt", listOf("file.txt", "file (2).txt")))
  }

  @Test
  fun insertingAndRenamingDoNotCreateDuplicateLogicalNames() {
    var tree = DocumentTree(ReportDocument())
    var doc = tree.insert(DocumentNode(id = "a", name = "Test", kind = BlockKind.TEXT), null)
    doc =
      DocumentTree(doc).insert(DocumentNode(id = "b", name = "test", kind = BlockKind.FOLDER), null)
    assertEquals(listOf("Test", "test (2)"), doc.nodes.map { it.name })
    tree = DocumentTree(doc)
    val renamed = tree.update(tree.node("b").copy(name = "TEST"))
    assertEquals("TEST (2)", DocumentTree(renamed).node("b").name)
    assertTrue(runCatching { tree.update(tree.node("b").copy(name = " ")) }.isFailure)
  }

  @Test
  fun movingSeveralRootsWithSameNameAllocatesNamesSequentially() {
    val tree =
      DocumentTree(
        ReportDocument(
          nodes =
            listOf(
              DocumentNode(id = "f1", name = "One", kind = BlockKind.FOLDER),
              DocumentNode(id = "f2", name = "Two", kind = BlockKind.FOLDER),
              DocumentNode(
                id = "a",
                name = "file.txt",
                parentId = "f1",
                kind = BlockKind.TEXT_FILE,
              ),
              DocumentNode(id = "b", name = "file.txt", parentId = "f2", kind = BlockKind.TEXT_FILE),
            )
        )
      )
    val result = DocumentTree(tree.transfer(listOf("a", "b"), null))
    assertEquals("file.txt", result.node("a").name)
    assertEquals("file (2).txt", result.node("b").name)
  }

  @Test
  fun oldDuplicateDocumentsAreNormalizedWithoutChangingIdsOrOrder() {
    val doc =
      ReportDocument(
        nodes =
          listOf(
            DocumentNode(id = "a", name = "Same", kind = BlockKind.TEXT),
            DocumentNode(id = "b", name = "same", kind = BlockKind.TEXT, order = 1),
          )
      )
    val tree = DocumentTree(doc)
    assertEquals(listOf("a", "b"), tree.children(null).map { it.id })
    assertEquals("same (2)", tree.node("b").name)
  }

  @Test
  fun displayNameIsNotAnAlternatePathAndMayBeCleared() {
    val node =
      DocumentNode(
        id = "a",
        name = "logical",
        displayName = "Pretty label",
        kind = BlockKind.FOLDER,
      )
    val tree = DocumentTree(ReportDocument(nodes = listOf(node)))
    assertEquals("Pretty label", tree.node("a").displayLabel)
    assertEquals("a", tree.resolvePath("/logical"))
    assertTrue(runCatching { tree.resolvePath("/Pretty label") }.isFailure)
    assertEquals(
      "logical",
      DocumentTree(tree.update(node.copy(displayName = null))).node("a").displayLabel,
    )
  }

  @Test
  fun numericInputHasNoImageRequirementAndRejectsNonFiniteOrWrongShape() {
    val model = LocalModel("n", "Numbers", ModelAdapter.NUMERIC_REGRESSION)
    val choice = ModelChoice("n", model.name, model.task, local = model)
    assertTrue(choice.numericInput)
    assertTrue(choice.textInput)
    assertFalse(model.adapter.isImage)
    assertArrayEquals(doubleArrayOf(1.5, -2.0, 300.0), parseNumericInput("[1.5, -2, 3e2]", 3), 0.0)
    for (text in listOf("NaN,1", "Infinity,1", "one,2", "1")) assertTrue(
      runCatching { parseNumericInput(text, 2) }.isFailure
    )
  }

  @Test
  fun integerInputsRetainTheirFullInt64Precision() {
    val buffer = numericTextBuffer("[9007199254740993, -9223372036854775808]", 2, DataType.INT64)
    assertEquals(9007199254740993L, buffer.long)
    assertEquals(Long.MIN_VALUE, buffer.long)
    assertTrue(runCatching { numericTextBuffer("1.2", 1, DataType.INT64) }.isFailure)
    assertTrue(runCatching { numericTextBuffer("2147483648", 1, DataType.INT32) }.isFailure)
  }

  @Test
  fun quantizedNumbersAreEncodedUsingTheirScaleAndZeroPoint() {
    val buffer = numericBuffer(doubleArrayOf(0.0, 1.0), DataType.UINT8, .5f, 100)
    assertEquals(100, buffer.get().toInt() and 255)
    assertEquals(102, buffer.get().toInt() and 255)
  }

  @Test
  fun calibrationIsContinuousMonotoneAndKeepsTheReleasedThumbAnchored() {
    for (calibration in listOf(ScrollCalibration(.3f, .7f), ScrollCalibration(.7f, .3f))) {
      assertEquals(calibration.shown, calibration.map(calibration.actual), .00001f)
      var last = 0f
      for (i in 0..1000) {
        val p = calibration.map(i / 1000f)
        assertTrue(p >= last)
        last = p
      }
      assertEquals(0f, calibration.map(0f), 0f)
      assertEquals(1f, calibration.map(1f), 0f)
    }
  }
}
