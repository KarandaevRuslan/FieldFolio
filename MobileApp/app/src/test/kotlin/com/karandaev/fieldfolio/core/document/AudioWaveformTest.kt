package com.karandaev.fieldfolio.core.document

import org.junit.Assert.*
import org.junit.Test

class AudioWaveformTest {
  @Test
  fun envelopeReflectsDecodedSamplesRatherThanASyntheticWave() {
    val envelope = WaveformAccumulator(4)
    envelope.sample(.0, 0f)
    envelope.sample(.3, -.25f)
    envelope.sample(.3, .1f)
    envelope.sample(.6, 1f)
    envelope.sample(1.0, .5f)
    assertArrayEquals(floatArrayOf(0f, .25f, 1f, .5f), envelope.result(), .0001f)
  }

  @Test
  fun seekAndTimeHandleBoundariesAndHours() {
    assertEquals(0, seekPosition(-1f, 100000))
    assertEquals(75000, seekPosition(.75f, 100000))
    assertEquals(100000, seekPosition(2f, 100000))
    assertEquals("1:01:01", audioTime(3661000))
    assertEquals("0:00", audioTime(-3))
  }
}
