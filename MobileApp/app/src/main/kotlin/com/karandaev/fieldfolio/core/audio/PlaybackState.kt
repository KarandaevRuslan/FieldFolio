package com.karandaev.fieldfolio.core.audio

import androidx.compose.runtime.*

/** Native position callbacks cannot overwrite a requested seek until its last command completes. */
class AudioSeekState(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
  private var settling: Int? = null
  private var settlingUntil = 0L
  var position by mutableIntStateOf(0)
    private set

  var revision: Long = 0
    private set

  private var requested: Int? = null
  private var inFlight: Int? = null
  val pending
    get() = requested != null

  fun request(value: Int): Int? {
    revision++
    settling = null
    position = value.coerceAtLeast(0)
    requested = position
    if (inFlight != null) return null
    inFlight = position
    return position
  }

  fun complete(): Int? {
    revision++
    val next = requested
    if (next != null && next != inFlight) {
      inFlight = next
      return next
    }
    settling = requested
    settlingUntil = now() + 1500
    inFlight = null
    requested = null
    return null
  }

  fun observe(value: Int) {
    if (pending) return
    val target = settling
    if (target != null && kotlin.math.abs(value - target) > 750 && now() < settlingUntil) return
    settling = null
    position = value.coerceAtLeast(0)
  }

  fun reset(value: Int = 0) {
    revision++
    settling = null
    requested = null
    inFlight = null
    position = value.coerceAtLeast(0)
  }
}

interface PlaybackParticipant {
  fun pauseForReplacement()
}

/** Reserve on Play, not on preparation completion: a slow earlier player never steals playback. */
class PlaybackGate {
  private var generation = 0L
  var owner: PlaybackParticipant? = null
    private set

  fun request(next: PlaybackParticipant): Long {
    val previous = owner
    owner = next
    generation++
    if (previous !== next) previous?.pauseForReplacement()
    return generation
  }

  fun permits(track: PlaybackParticipant, token: Long) = owner === track && generation == token

  fun release(track: PlaybackParticipant) {
    if (owner === track) {
      owner = null
      generation++
    }
  }
}
