package com.karandaev.fieldfolio.core.models

import org.junit.Assert.*
import org.junit.Test

class ModelAdaptersTest {
  private val vocab =
    listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "hello", "un", "##aff", "##able", ",", "中", "cafe")

  @Test
  fun wordpieceProducesIdsMaskAndSegmentsAndNormalizesAccents() {
    val result = WordPieceTokenizer(vocab, true).encode("Héllo, unaffable 中 café", 12)
    assertArrayEquals(intArrayOf(2, 4, 8, 5, 6, 7, 9, 10, 3, 0, 0, 0), result.ids)
    assertArrayEquals(intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0), result.mask)
    assertTrue(result.segments.all { it == 0 })
    assertFalse(result.truncated)
  }

  @Test
  fun unknownWordsAreNotPartiallyTokenizedAndTruncationKeepsSep() {
    assertArrayEquals(
      intArrayOf(2, 1, 3, 0),
      WordPieceTokenizer(vocab, true).encode("unaffZZ", 4).ids,
    )
    val short = WordPieceTokenizer(vocab, true).encode("hello hello hello hello", 4)
    assertTrue(short.truncated)
    assertArrayEquals(intArrayOf(2, 4, 4, 3), short.ids)
    assertThrows(IllegalArgumentException::class.java) {
      WordPieceTokenizer(listOf("[UNK]"), true).encode("x", 5)
    }
  }

  @Test
  fun tokenBindingsUseTensorNamesOrAnExplicitValidatedOrder() {
    assertEquals(
      listOf(TokenInputRole.MASK, TokenInputRole.IDS, TokenInputRole.SEGMENTS),
      tokenInputRoles(listOf("attention_mask", "input_ids", "token_type_ids"), emptyList()),
    )
    assertThrows(IllegalStateException::class.java) {
      tokenInputRoles(listOf("a", "b"), emptyList())
    }
    assertEquals(
      listOf(TokenInputRole.IDS, TokenInputRole.MASK),
      tokenInputRoles(listOf("a", "b"), listOf(TokenInputRole.IDS, TokenInputRole.MASK)),
    )
  }

  @Test
  fun logitsAreConvertedOnlyUsingTheConfiguredActivation() {
    val result = scoreValues(floatArrayOf(.2f, .3f), ScoreMode.SOFTMAX)
    assertEquals(1.0, result.sum(), .000001)
    assertEquals(.4750208, result[0], .000001)
    assertThrows(IllegalArgumentException::class.java) {
      scoreValues(floatArrayOf(2f), ScoreMode.PROBABILITIES)
    }
    assertEquals(.5, scoreValues(floatArrayOf(0f), ScoreMode.SIGMOID).single(), 0.0)
  }

  @Test
  fun nchwAndNhwcMasksHaveTheSamePixelsAndIndexedMasksKeepClasses() {
    val hwc = floatArrayOf(.9f, .1f, .2f, .8f, .1f, .9f, .7f, .3f)
    val chw = floatArrayOf(.9f, .2f, .1f, .7f, .1f, .8f, .9f, .3f)
    val expected = intArrayOf(0, 1, 1, 0)
    assertArrayEquals(
      expected,
      decodeSegmentation(hwc, intArrayOf(1, 2, 2, 2), TensorLayout.NHWC, ScoreMode.PROBABILITIES)
        .classes,
    )
    assertArrayEquals(
      expected,
      decodeSegmentation(chw, intArrayOf(1, 2, 2, 2), TensorLayout.NCHW, ScoreMode.PROBABILITIES)
        .classes,
    )
    assertArrayEquals(
      intArrayOf(0, 4),
      decodeSegmentation(
          floatArrayOf(0f, 4f),
          intArrayOf(1, 1, 2),
          TensorLayout.NHWC,
          ScoreMode.PROBABILITIES,
        )
        .classes,
    )
    assertArrayEquals(
      intArrayOf(0, 1),
      decodeSegmentation(
          floatArrayOf(-2f, 3f),
          intArrayOf(1, 1, 2, 1),
          TensorLayout.NHWC,
          ScoreMode.SIGMOID,
        )
        .classes,
    )
  }
}
