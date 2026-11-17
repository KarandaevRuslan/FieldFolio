package com.karandaev.fieldfolio.core.document

import com.karandaev.fieldfolio.core.ui.components.fields.withoutFileExtension
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class PolishRegressionTest {
  @Test
  fun pathsResolveRelativeAbsoluteAndEscapedNamesAndRejectLeaves() {
    val tree =
      DocumentTree(
        ReportDocument(
          nodes =
            listOf(
              DocumentNode(id = "a", name = "Folder", kind = BlockKind.FOLDER),
              DocumentNode(id = "b", parentId = "a", name = "A/B", kind = BlockKind.FOLDER),
              DocumentNode(id = "t", parentId = "b", name = "note.txt", kind = BlockKind.TEXT_FILE),
            )
        )
      )
    assertEquals("/Folder/A%2FB", tree.path("b"))
    assertEquals("b", tree.resolvePath(tree.path("b")))
    assertEquals("a", tree.resolvePath("..", "b"))
    assertNull(tree.resolvePath("/"))
    assertEquals("b", tree.resolvePath("A%2FB", "a"))
    assertTrue(runCatching { tree.resolvePath("/Folder/A%2FB/note.txt") }.isFailure)
    assertTrue(runCatching { tree.resolvePath("/missing") }.isFailure)
    assertEquals("t", tree.resolvePath("/Folder/A%2FB/note.txt", foldersOnly = false))
    assertTrue(
      runCatching { tree.resolvePath("/Folder/A%2FB/note.txt/..", foldersOnly = false) }.isFailure
    )
  }

  @Test
  fun strippingExtensionDoesNotChangeHiddenOrExtensionlessNames() {
    assertEquals("photo", withoutFileExtension("photo.jpg"))
    assertEquals("archive.tar", withoutFileExtension("archive.tar.gz"))
    assertEquals(".env", withoutFileExtension(".env"))
    assertEquals("readme", withoutFileExtension("readme"))
  }

  @Test
  fun pageCountAndJumpRespectMultibyteAndUtf16Files() {
    val text = "строка🙂".repeat(900)
    for (bytes in
      listOf(text.toByteArray(), byteArrayOf(-1, -2) + text.toByteArray(Charsets.UTF_16LE))) {
      val count = TextResourceCodec.pageCount(bytes, 500)
      assertEquals((text.length + 499) / 500, count)
      assertTrue(TextResourceCodec.page(bytes, count - 1, 500).text.isNotEmpty())
      assertFalse(TextResourceCodec.page(bytes, count - 1, 500).hasNext)
    }
  }

  @Test
  fun completedWaveformCacheReusesContentAcrossNewArraysAndProtectsCachedValues() = runBlocking {
    val source = "unique-cache-regression-2026".toByteArray()
    var decodes = 0
    val first =
      WaveformCache.get(source) {
        decodes++
        FloatArray(160) { .25f }
      }
    first.fill(0f)
    val next =
      WaveformCache.get(source.copyOf()) {
        decodes++
        error("must be cached")
      }
    assertEquals(1, decodes)
    assertEquals(.25f, next[0], 0f)
  }

  @Test
  fun waveformDecodeDoesNotRunOnTheCallingThreadAndDeduplicatesConcurrentRequests() = runBlocking {
    val originalThread = Thread.currentThread()
    var decodes = 0
    coroutineScope {
      val source = "concurrent-waveform-cache-regression".toByteArray()
      val jobs =
        (0..4).map {
          async {
            WaveformCache.get(source) {
              assertNotSame(originalThread, Thread.currentThread())
              decodes++
              delay(20)
              FloatArray(160) { .5f }
            }
          }
        }
      jobs.awaitAll().forEach { assertEquals(.5f, it[159], 0f) }
    }
    assertEquals(1, decodes)
  }
}
