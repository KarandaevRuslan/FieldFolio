package com.karandaev.fieldfolio.core.document

import android.app.Application
import com.karandaev.fieldfolio.core.json.MoshiFactory
import com.karandaev.fieldfolio.core.models.*
import java.io.*
import java.util.zip.ZipInputStream
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class TextPresentationTest {
  @Test
  fun modelSnapshotsArePortableAndUnencryptedForEveryTask() {
    val moshi = MoshiFactory.create()
    for (task in listOf("DETECTION", "SEGMENTATION", "CLASSIFICATION", "REGRESSION", "TEXT")) {
      val result =
        ModelResultData(
          modelId = "test",
          modelName = "Model",
          provider = "LOCAL",
          task = task,
          inputText = "1 2 3",
          text = "Output",
          boxes = listOf(ResultBox("A", .8, .1, .2, .3, .4)),
          maskAsset = "untrusted/../mask",
          layers = listOf(SegmentationLayer(label = "Road", maskAsset = "mask-two")),
          values = listOf(ResultValue("Estimate", 2.5, "m")),
          scores = listOf(ResultScore("Class", .8)),
        )
      val input = byteArrayOf(1, 2, 3)
      val mask = byteArrayOf(4, 5)
      val out = ByteArrayOutputStream()
      ModelResultArchive.write(
        ModelExecution(result, mapOf("untrusted/../mask" to mask, "mask-two" to byteArrayOf(6))),
        input,
        moshi,
        out,
      )
      val zip = out.toByteArray()
      assertEquals(0, zip[6].toInt() and 1)
      val entries = linkedMapOf<String, ByteArray>()
      ZipInputStream(zip.inputStream()).use { z ->
        while (true) {
          val e = z.nextEntry ?: break
          entries[e.name] = z.readBytes()
        }
      }
      assertTrue(entries.keys.none { it.contains("..") || it.startsWith("/") })
      val saved =
        moshi
          .adapter(ModelResultData::class.java)
          .fromJson(entries.getValue("result.json").decodeToString())!!
      assertEquals(result.boxes, saved.boxes)
      assertEquals(task, saved.task)
      assertEquals(result.text, saved.text)
      assertArrayEquals(input, entries[saved.inputAsset])
      assertArrayEquals(mask, entries[saved.maskAsset])
      assertTrue(entries.containsKey(saved.layers.single().maskAsset))
      assertArrayEquals(byteArrayOf(1, 2, 3), input)
    }
  }

  @Test
  fun textReadingAndHeadingSettingsRoundTripWithoutChangingLegacyDocuments() {
    val adapter = MoshiFactory.create().adapter(ReportDocument::class.java)
    val old =
      DocumentNode(
        name = "Logical",
        displayName = "Title",
        kind = BlockKind.HEADING,
        level = 2,
        body = "Subtitle",
      )
    val current =
      old.copy(kind = BlockKind.TEXT, headingLevel = 2, textOpenMode = TextOpenMode.ACODE)
    val doc = ReportDocument(nodes = listOf(current))
    val restored = adapter.fromJson(adapter.toJson(doc))!!
    assertEquals(doc, restored)
    assertEquals(2, restored.nodes.single().effectiveHeadingLevel)
    assertEquals(2, old.effectiveHeadingLevel)
    assertEquals("Title", old.visualTitle)
  }

  @Test
  fun emptyHiddenTitlesAndBodiesDoNotMakeViewerCellsForAnyTextVariant() {
    for (kind in listOf(BlockKind.TEXT, BlockKind.TEXT_FILE, BlockKind.HEADING)) {
      val n =
        DocumentNode(
          name = "Stable",
          kind = kind,
          hideTitle = true,
          body = "",
          asset = if (kind == BlockKind.TEXT_FILE) "resources/blank.txt" else null,
          headingLevel = 1,
        )
      val doc = ReportDocument(nodes = listOf(n))
      val view =
        DocumentPresentation(
          DocumentTree(doc),
          mapOf("resources/blank.txt" to " \n\t".toByteArray()),
        )
      assertFalse(view.hasVisual(n))
      assertTrue(view.children(null).isEmpty())
      assertEquals(1, doc.nodes.size)
    }
  }

  @Test
  fun headingLevelsAreValidatedByTheDocumentNotJustFields() {
    assertThrows(IllegalArgumentException::class.java) {
      DocumentTree(
        ReportDocument(
          nodes = listOf(DocumentNode(name = "Title", kind = BlockKind.TEXT, headingLevel = 7))
        )
      )
    }
  }
}
