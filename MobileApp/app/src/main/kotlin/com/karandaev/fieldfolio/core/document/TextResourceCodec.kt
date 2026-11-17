package com.karandaev.fieldfolio.core.document

import java.io.InputStreamReader
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Never guesses a lossy encoding or truncates a resource before saving it. */
object TextResourceCodec {
  const val EDIT_BYTES = 8 * 1024 * 1024
  const val EDIT_CHARS = 4 * 1024 * 1024
  const val PAGE_CHARS = 16384

  data class Encoding(val charset: Charset, val bom: ByteArray)

  data class Editable(val text: String, val encoding: Encoding)

  data class Page(val text: String, val hasNext: Boolean)

  fun encoding(bytes: ByteArray): Encoding =
    when {
      bytes.take(4) == listOf<Byte>(0, 0, -2, -1) || bytes.take(4) == listOf<Byte>(-1, -2, 0, 0) ->
        error("UTF-32 resources can be kept and exported, but are not editable")
      bytes.take(3) == listOf<Byte>(-17, -69, -65) ->
        Encoding(Charsets.UTF_8, byteArrayOf(-17, -69, -65))
      bytes.take(2) == listOf<Byte>(-1, -2) -> Encoding(Charsets.UTF_16LE, byteArrayOf(-1, -2))
      bytes.take(2) == listOf<Byte>(-2, -1) -> Encoding(Charsets.UTF_16BE, byteArrayOf(-2, -1))
      else -> {
        // BOM-less UTF-16 is accepted only when the byte pattern is unambiguous.
        val n = minOf(bytes.size / 2, 2048)
        val even = (0 until n).count { bytes[it * 2] == 0.toByte() }
        val odd = (0 until n).count { bytes[it * 2 + 1] == 0.toByte() }
        val cs =
          if (n >= 4 && odd > n * .6 && even < n * .1) Charsets.UTF_16LE
          else if (n >= 4 && even > n * .6 && odd < n * .1) Charsets.UTF_16BE else Charsets.UTF_8
        Encoding(cs, byteArrayOf())
      }
    }

  private fun reader(bytes: ByteArray, e: Encoding) =
    InputStreamReader(
      bytes.inputStream(e.bom.size, bytes.size - e.bom.size),
      e.charset
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT),
    )

  fun editable(bytes: ByteArray): Editable? =
    runCatching {
        require(bytes.size <= EDIT_BYTES)
        val e = encoding(bytes)
        val text = reader(bytes, e).use { it.readText() }
        require(text.length <= EDIT_CHARS && text.none { it.isISOControl() && it !in "\t\r\n" })
        Editable(text, e)
      }
      .getOrNull()

  fun page(bytes: ByteArray, index: Int, size: Int = PAGE_CHARS): Page {
    require(index >= 0 && size in 1..PAGE_CHARS)
    return reader(bytes, encoding(bytes)).use { r ->
      var remaining = index.toLong() * size
      val buffer = CharArray(4096)
      while (remaining > 0) {
        val n = r.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (n < 0) return@use Page("", false)
        remaining -= n
      }
      val out = StringBuilder(size + 2)
      while (out.length < size + 2) {
        val n = r.read(buffer, 0, minOf(buffer.size, size + 2 - out.length))
        if (n < 0) break
        out.append(buffer, 0, n)
      }
      var end = minOf(size, out.length)
      if (
        end > 0 && end < out.length && out[end - 1].isHighSurrogate() && out[end].isLowSurrogate()
      )
        end++
      val start = if (index > 0 && out.isNotEmpty() && out[0].isLowSurrogate()) 1 else 0
      val text = out.substring(start, end)
      require(text.none { it.isISOControl() && it !in "\t\r\n" }) { "Unsupported text encoding" }
      Page(text, out.length > end)
    }
  }

  /**
   * Streaming emptiness check; callers perform it off the UI thread. Unknown encodings stay
   * visible.
   */
  fun hasVisibleText(bytes: ByteArray): Boolean =
    runCatching {
        reader(bytes, encoding(bytes)).use { r ->
          val buffer = CharArray(4096)
          while (true) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val n = r.read(buffer)
            if (n < 0) return@use false
            if ((0 until n).any { !buffer[it].isWhitespace() }) return@use true
          }
          @Suppress("UNREACHABLE_CODE") false
        }
      }
      .getOrDefault(bytes.isNotEmpty())

  fun pageCount(bytes: ByteArray, size: Int): Int {
    require(size in 1..PAGE_CHARS)
    var chars = 0L
    reader(bytes, encoding(bytes)).use { r ->
      val buffer = CharArray(8192)
      while (true) {
        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
        val n = r.read(buffer)
        if (n < 0) break
        chars += n
      }
    }
    return ((chars + size - 1) / size).toInt().coerceAtLeast(1)
  }

  fun encode(text: String, e: Encoding): ByteArray {
    val data =
      e.charset
        .newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .encode(CharBuffer.wrap(text))
    return e.bom + ByteArray(data.remaining()).also { data.get(it) }
  }
}
