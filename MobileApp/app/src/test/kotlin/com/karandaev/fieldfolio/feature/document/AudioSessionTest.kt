package com.karandaev.fieldfolio.feature.document

import android.media.MediaPlayer
import android.os.Looper
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioSessionTest {
  private val sessions = mutableListOf<DocumentAudioSession>()
  private val players = mutableListOf<MediaPlayer>()

  @Before
  fun media() {
    ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(120000, 0) }
    ShadowMediaPlayer.setCreateListener { native, _ -> players += native }
  }

  @After
  fun close() {
    sessions.forEach { it.close() }
    ShadowMediaPlayer.resetStaticState()
    ShadowMediaPlayer.setCreateListener(null)
  }

  private fun session() = DocumentAudioSession().also { sessions += it }

  private fun wave(): ByteArray {
    val n = 8000
    val b = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
    b.put("RIFF".toByteArray())
    b.putInt(36 + n * 2)
    b.put("WAVEfmt ".toByteArray())
    b.putInt(16)
    b.putShort(1)
    b.putShort(1)
    b.putInt(8000)
    b.putInt(16000)
    b.putShort(2)
    b.putShort(16)
    b.put("data".toByteArray())
    b.putInt(n * 2)
    repeat(n) { b.putShort(if (it == n / 2) 16000 else 0) }
    return b.array()
  }

  private fun idle(ms: Long = 1) {
    androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
  }

  private fun awaitReady(track: DocumentAudioTrack) = runBlocking {
    withTimeout(10000) {
      while (!track.ready) {
        idle()
        delay(2)
      }
    }
  }

  @Test
  fun waveAndTrackSurviveRepeatedVisibilityChangesWithoutNewDecoding() {
    val session = session()
    val bytes = wave()
    val track = session.track("asset", bytes)
    runBlocking {
      withTimeout(10000) {
        while (!track.waveformComplete) {
          idle()
          delay(5)
        }
      }
    }
    val decoded = track.waveform
    assertNotNull(decoded)
    repeat(8) {
      track.hide()
      assertSame(track, session.track("asset", bytes.copyOf()))
      assertSame(decoded, track.waveform)
      assertTrue(track.waveformComplete)
    }
  }

  @Test
  fun pendingNativeSeekKeepsTheFinalCursorUntilTheLatestCommandCompletes() {
    val track = session().track("asset", wave())
    track.prepare()
    awaitReady(track)
    assertTrue(track.ready)
    val player = players.single()
    Shadows.shadowOf(player).setSeekDelay(300)
    track.seek(.2f)
    track.seek(.8f)
    idle(100)
    assertEquals(96000, track.position)
    idle(250)
    assertEquals(96000, track.position)
    idle(400)
    assertFalse(track.seekState.pending)
    assertEquals(96000, track.position)
  }

  @Test
  fun playingTrackIsNotReleasedOffscreenAndAnotherSessionReplacesIt() {
    val first = session().track("first", wave())
    first.prepare()
    awaitReady(first)
    first.play()
    val native = players.single()
    assertTrue(native.isPlaying)
    first.hide()
    assertTrue(native.isPlaying)
    val second = session().track("second", wave())
    second.prepare()
    awaitReady(second)
    second.play()
    assertFalse(native.isPlaying)
    assertFalse(first.playing)
    assertTrue(second.playing)
  }
}
