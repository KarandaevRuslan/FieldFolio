package com.karandaev.fieldfolio.core.document

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TextAndWaveformRegressionTest {
  @Test
  fun pagingPreservesEmojiAndAllSourceCharactersAtEveryBoundary() {
    val source = "a".repeat(499) + "🙂" + "b".repeat(499) + "🌦️" + "tail"
    val pages = (0..source.length / 500).map { textPage(source, it, 500) }
    assertEquals(source, pages.joinToString(""))
    assertEquals(1, textPageCount("a".repeat(499) + "🙂", 500))
    assertTrue(
      pages.all {
        it.firstOrNull()?.isLowSurrogate() != true && it.lastOrNull()?.isHighSurrogate() != true
      }
    )
  }

  @Test
  fun pcmWaveformIsDecodedEagerlyWithoutStartingAnyPlayer() = runBlocking {
    val frames = 16000
    val output = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN)
    output.put("RIFF".toByteArray())
    output.putInt(36 + frames * 2)
    output.put("WAVEfmt ".toByteArray())
    output.putInt(16)
    output.putShort(1)
    output.putShort(1)
    output.putInt(8000)
    output.putInt(16000)
    output.putShort(2)
    output.putShort(16)
    output.put("data".toByteArray())
    output.putInt(frames * 2)
    repeat(frames) { output.putShort(if (it == frames / 2) 16384 else 0) }
    var updates = 0
    val waveform = decodePcmWave(output.array()) { updates++ }
    assertNotNull(waveform)
    assertTrue(updates > 1)
    assertEquals(1f, waveform!![80], 0f)
    assertEquals(0f, waveform[0], 0f)
    assertEquals(0f, waveform[159], 0f)
  }

  @Test
  fun millisecondTimeAndSmallSeekFractionsPreserveFiveMillisecondSteps() {
    assertEquals("0:01.005", audioTime(1005, true))
    for (at in 5..5000 step 5) assertEquals(at, seekPosition(at / 10000f, 10000))
  }

  @Test
  fun resizingCropViewportKeepsTheSameSelectedImageArea() {
    val state =
      com.karandaev.fieldfolio.feature.detection.components.crop.cropper.DetectCropperState()
    val image = androidx.compose.ui.unit.IntSize(800, 400)
    val before = androidx.compose.ui.unit.IntSize(320, 240)
    val after = androidx.compose.ui.unit.IntSize(640, 480)
    state.applyGesture(image, before, androidx.compose.ui.geometry.Offset(37f, 21f), 2f)
    val selected =
      com.karandaev.fieldfolio.feature.detection.components.crop.cropper.calculateDetectCropBox(
        image,
        before,
        state.scale,
        state.offset,
      )
    state.restoreFromCropBox(selected, image, after)
    val restored =
      com.karandaev.fieldfolio.feature.detection.components.crop.cropper.calculateDetectCropBox(
        image,
        after,
        state.scale,
        state.offset,
      )
    assertEquals(selected.xMin, restored.xMin, .00001)
    assertEquals(selected.yMin, restored.yMin, .00001)
    assertEquals(selected.xMax, restored.xMax, .00001)
    assertEquals(selected.yMax, restored.yMax, .00001)
  }
}
