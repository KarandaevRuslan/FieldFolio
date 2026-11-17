package com.karandaev.fieldfolio.core.document

import org.junit.Assert.*
import org.junit.Test

class TextResourceCodecTest {
  @Test
  fun utf8AndUtf16RoundTripPreserveBomAndLineEndings() {
    for (e in
      listOf(
        TextResourceCodec.Encoding(Charsets.UTF_8, byteArrayOf()),
        TextResourceCodec.Encoding(Charsets.UTF_8, byteArrayOf(-17, -69, -65)),
        TextResourceCodec.Encoding(Charsets.UTF_16LE, byteArrayOf(-1, -2)),
        TextResourceCodec.Encoding(Charsets.UTF_16BE, byteArrayOf(-2, -1)),
      )) {
      val text = "Привет, world! 😀\r\nSecond line\n"
      val bytes = TextResourceCodec.encode(text, e)
      val decoded = TextResourceCodec.editable(bytes)!!
      assertEquals(text, decoded.text)
      assertArrayEquals(bytes, TextResourceCodec.encode(decoded.text, decoded.encoding))
      assertEquals(
        text + "Edit",
        TextResourceCodec.editable(TextResourceCodec.encode(text + "Edit", decoded.encoding))!!.text,
      )
    }
  }

  @Test
  fun acodeCanEditLargeTextAndOversizedFilesRemainPageReadable() {
    val text = "а".repeat(1_000_000) + "KEEP_TAIL"
    val bytes = text.toByteArray()
    assertEquals(text, TextResourceCodec.editable(bytes)!!.text)
    assertNull(TextResourceCodec.editable(ByteArray(TextResourceCodec.EDIT_BYTES + 1) { 65 }))
    val page = TextResourceCodec.page(bytes, text.length / TextResourceCodec.PAGE_CHARS)
    assertTrue(page.text.endsWith("KEEP_TAIL"))
    assertFalse(page.hasNext)
    assertEquals(text.take(2048), TextResourceCodec.page(bytes, 0, 2048).text)
  }

  @Test
  fun invalidEncodingIsRejectedInsteadOfReplacingBytes() {
    assertNull(TextResourceCodec.editable(byteArrayOf(-1, 20, -61)))
    assertThrows(Exception::class.java) { TextResourceCodec.page(byteArrayOf(-1, 20, -61), 0) }
  }

  @Test
  fun pagingDoesNotSplitUnicodeSurrogatePairs() {
    val bytes = "aaa😀bbb".toByteArray()
    val first = TextResourceCodec.page(bytes, 0, 4)
    val second = TextResourceCodec.page(bytes, 1, 4)
    assertEquals("aaa😀", first.text)
    assertTrue(first.hasNext)
    assertEquals("bbb", second.text)
    assertFalse(second.hasNext)
  }
}
