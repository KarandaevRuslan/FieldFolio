package com.karandaev.fieldfolio.core.audio

import org.junit.Assert.*
import org.junit.Test

class PlaybackStateTest {
  @Test
  fun oldNativePositionsCannotUndoTheLastDragDestination() {
    val state = AudioSeekState()
    assertEquals(2000, state.request(2000))
    assertNull(state.request(8000))
    state.observe(100)
    assertEquals(8000, state.position)
    assertEquals(8000, state.complete())
    state.observe(2000)
    assertEquals(8000, state.position)
    assertNull(state.complete())
    state.observe(8000)
    assertEquals(8000, state.position)
    assertFalse(state.pending)
  }

  @Test
  fun latestPlaybackReservationWinsEvenWhenAnEarlierPreparationFinishesLater() {
    class Track : PlaybackParticipant {
      var pauses = 0

      override fun pauseForReplacement() {
        pauses++
      }
    }
    val gate = PlaybackGate()
    val first = Track()
    val last = Track()
    val old = gate.request(first)
    val current = gate.request(last)
    assertEquals(1, first.pauses)
    assertFalse(gate.permits(first, old))
    assertTrue(gate.permits(last, current))
    gate.release(first)
    assertTrue(gate.permits(last, current))
  }
}
