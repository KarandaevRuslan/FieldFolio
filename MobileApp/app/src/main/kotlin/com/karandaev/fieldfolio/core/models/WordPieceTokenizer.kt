package com.karandaev.fieldfolio.core.models

import java.text.Normalizer
import java.util.Locale

/** BERT basic tokenization followed by greedy longest-match WordPiece. */
class WordPieceTokenizer(vocabulary: List<String>, private val lowercase: Boolean) {
  private val ids = vocabulary.withIndex().associate { it.value to it.index }

  private fun special(value: String) = requireNotNull(ids[value]) { "Vocabulary is missing $value" }

  data class Encoded(
    val ids: IntArray,
    val mask: IntArray,
    val segments: IntArray,
    val truncated: Boolean,
  )

  fun encode(text: String, length: Int): Encoded {
    require(length in 2..4096 && text.length <= 1_000_000)
    val unknown = special("[UNK]")
    val pad = special("[PAD]")
    val words = basic(text)
    val pieces = ArrayList<Int>()
    var truncated = false
    for (word in words) {
      val parts = ArrayList<Int>()
      if (word.codePointCount(0, word.length) > 100) parts += unknown
      else {
        val points = word.codePoints().toArray()
        var start = 0
        while (start < points.size) {
          var end = points.size
          var found: Int? = null
          while (end > start) {
            val piece = (if (start > 0) "##" else "") + String(points, start, end - start)
            found = ids[piece]
            if (found != null) break
            end--
          }
          if (found == null) {
            parts.clear()
            parts += unknown
            break
          }
          parts += found
          start = end
        }
      }
      for (part in parts) {
        if (pieces.size >= length - 2) {
          truncated = true
          break
        }
        pieces += part
      }
      if (truncated) break
    }
    val tokenIds = IntArray(length) { pad }
    val mask = IntArray(length)
    val sequence = listOf(special("[CLS]")) + pieces + special("[SEP]")
    sequence.forEachIndexed { i, id ->
      tokenIds[i] = id
      mask[i] = 1
    }
    return Encoded(tokenIds, mask, IntArray(length), truncated)
  }

  private fun basic(text: String): List<String> {
    val cleaned = StringBuilder()
    text.codePoints().forEach { cp ->
      when {
        cp == 0 || cp == 0xFFFD -> Unit
        Character.isWhitespace(cp) || Character.getType(cp) == Character.SPACE_SEPARATOR.toInt() ->
          cleaned.append(' ')
        Character.getType(cp) in setOf(Character.CONTROL.toInt(), Character.FORMAT.toInt()) -> Unit
        isChinese(cp) -> {
          cleaned.append(' ')
          cleaned.appendCodePoint(cp)
          cleaned.append(' ')
        }
        else -> cleaned.appendCodePoint(cp)
      }
    }
    val out = ArrayList<String>()
    for (raw in cleaned.toString().trim().split(Regex("\\s+"))) {
      val word =
        if (lowercase)
          Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD).filter {
            Character.getType(it) != Character.NON_SPACING_MARK.toInt()
          }
        else raw
      val current = StringBuilder()
      word.codePoints().forEach { cp ->
        val type = Character.getType(cp)
        if (
          cp in 33..47 ||
            cp in 58..64 ||
            cp in 91..96 ||
            cp in 123..126 ||
            type in
              setOf(
                Character.CONNECTOR_PUNCTUATION.toInt(),
                Character.DASH_PUNCTUATION.toInt(),
                Character.START_PUNCTUATION.toInt(),
                Character.END_PUNCTUATION.toInt(),
                Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
                Character.FINAL_QUOTE_PUNCTUATION.toInt(),
                Character.OTHER_PUNCTUATION.toInt(),
              )
        ) {
          if (current.isNotEmpty()) {
            out += current.toString()
            current.clear()
          }
          out += String(Character.toChars(cp))
        } else current.appendCodePoint(cp)
      }
      if (current.isNotEmpty()) out += current.toString()
    }
    return out
  }

  private fun isChinese(cp: Int) =
    cp in 0x4E00..0x9FFF ||
      cp in 0x3400..0x4DBF ||
      cp in 0x20000..0x2A6DF ||
      cp in 0x2A700..0x2B73F ||
      cp in 0x2B740..0x2B81F ||
      cp in 0x2B820..0x2CEAF ||
      cp in 0xF900..0xFAFF ||
      cp in 0x2F800..0x2FA1F
}

fun tokenInputRoles(names: List<String>, configured: List<TokenInputRole>): List<TokenInputRole> {
  require(names.size in 1..3)
  if (configured.isNotEmpty()) {
    require(
      configured.size == names.size &&
        configured.distinct().size == configured.size &&
        TokenInputRole.IDS in configured
    )
    return configured
  }
  val guessed =
    names.map { name ->
      when {
        "mask" in name.lowercase() -> TokenInputRole.MASK
        "segment" in name.lowercase() || "type_ids" in name.lowercase() -> TokenInputRole.SEGMENTS
        "ids" in name.lowercase() || "tokens" in name.lowercase() -> TokenInputRole.IDS
        else -> null
      }
    }
  if (
    guessed.all { it != null } &&
      guessed.distinct().size == guessed.size &&
      TokenInputRole.IDS in guessed
  )
    return guessed.filterNotNull()
  if (names.size == 1) return listOf(TokenInputRole.IDS)
  error("Assign IDS / MASK / SEGMENTS to model inputs in model settings")
}
