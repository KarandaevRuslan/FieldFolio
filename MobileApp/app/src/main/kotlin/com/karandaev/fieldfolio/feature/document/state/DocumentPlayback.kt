package com.karandaev.fieldfolio.feature.document.state

import android.media.MediaPlayer
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import com.karandaev.fieldfolio.core.audio.*
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.ui.text.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

private val applicationPlaybackGate = PlaybackGate()

/** The session owns players and decoded waves; scrolling and Activity recreation own neither. */
class DocumentPlaybackViewModel(app: android.app.Application) : AndroidViewModel(app) {
  val session = DocumentAudioSession(app)

  override fun onCleared() {
    session.close()
  }
}

class DocumentAudioSession(private val context: android.content.Context? = null) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val tracks = mutableMapOf<String, DocumentAudioTrack>()

  fun track(key: String, bytes: ByteArray) =
    tracks.getOrPut(key) { DocumentAudioTrack(bytes, scope, applicationPlaybackGate, context) }

  fun pause() {
    tracks.values.forEach { it.pause() }
  }

  fun clear() {
    tracks.values.forEach { it.close() }
    tracks.clear()
  }

  fun close() {
    clear()
    scope.cancel()
  }
}

class DocumentAudioTrack
internal constructor(
  bytes: ByteArray,
  private val scope: CoroutineScope,
  private val gate: PlaybackGate,
  private val context: android.content.Context? = null,
) : PlaybackParticipant {
  private var data: ByteArray? = null
  private val sourceReady = CompletableDeferred<ByteArray>()
  private var player: MediaPlayer? = null
  private var source: ByteArrayMediaSource? = null
  private var reservation = 0L
  private var preparing = false
  private var requestedPlay = false
  var ready by mutableStateOf(false)
    private set

  var failed by mutableStateOf(false)
    private set

  var playing by mutableStateOf(false)
    private set

  var duration by mutableIntStateOf(0)
    private set

  var waveform by mutableStateOf<FloatArray?>(null)
    private set

  var metadata by mutableStateOf(AudioMetadata())
    private set

  var waveformComplete by mutableStateOf(false)
    private set

  val seekState = AudioSeekState()
  val position
    get() = seekState.position

  private val waveJob =
    scope.launch {
      try {
        val copy = withContext(Dispatchers.Default) { bytes.copyOf() }
        data = copy
        sourceReady.complete(copy)
        waveform = WaveformCache.get(copy, context)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {} finally {
        waveformComplete = true
      }
    }
  private val metadataJob =
    scope.launch {
      try {
        metadata = readAudioMetadata(sourceReady.await())
      } catch (e: CancellationException) {
        throw e
      }
    }
  private val clockJob =
    scope.launch {
      snapshotFlow { ready }
        .collectLatest { available ->
          if (available)
            while (isActive && ready) {
              val native = player
              val revision = seekState.revision
              val observed =
                withContext(Dispatchers.Default) {
                  runCatching { native?.currentPosition }.getOrNull()
                }
              if (player === native && seekState.revision == revision)
                observed?.let(seekState::observe)
              delay(if (playing) 33 else 120)
            }
        }
    }

  fun prepare() {
    if (player != null || preparing) return
    if (!sourceReady.isCompleted) {
      preparing = true
      scope.launch {
        try {
          sourceReady.await()
        } catch (_: CancellationException) {
          return@launch
        }
        preparing = false
        prepare()
      }
      return
    }
    preparing = true
    failed = false
    val native = MediaPlayer()
    player = native
    val media = ByteArrayMediaSource(requireNotNull(data), ownsCopy = false)
    source = media
    try {
      native.setDataSource(media)
      native.setOnPreparedListener {
        if (player !== it) return@setOnPreparedListener
        preparing = false
        ready = true
        duration = it.duration
        val restore = position.coerceAtMost(duration)
        if (restore > 0)
          seekState.request(restore)?.let { target ->
            it.seekTo(target.toLong(), MediaPlayer.SEEK_CLOSEST)
          }
        if (requestedPlay && gate.permits(this, reservation)) {
          it.start()
          playing = true
        }
      }
      native.setOnSeekCompleteListener { nativePlayer ->
        if (player === nativePlayer)
          seekState.complete()?.let { nativePlayer.seekTo(it.toLong(), MediaPlayer.SEEK_CLOSEST) }
      }
      native.setOnCompletionListener {
        pause()
        seekState.reset(duration)
      }
      native.setOnErrorListener { _, _, _ ->
        failed = true
        ready = false
        preparing = false
        pause()
        true
      }
      native.prepareAsync()
    } catch (_: Exception) {
      failed = true
      preparing = false
      ready = false
      releaseNative()
    }
  }

  fun play() {
    reservation = gate.request(this)
    requestedPlay = true
    if (player == null) prepare()
    if (ready && gate.permits(this, reservation))
      runCatching {
          player?.start()
          playing = true
        }
        .onFailure {
          failed = true
          pause()
        }
  }

  fun pause() {
    requestedPlay = false
    runCatching { if (playing) player?.pause() }
    playing = false
    gate.release(this)
  }

  override fun pauseForReplacement() {
    requestedPlay = false
    runCatching { if (playing) player?.pause() }
    playing = false
  }

  fun seek(fraction: Float) {
    if (!ready) return
    val target = seekPosition(fraction, duration)
    seekState.request(target)?.let { player?.seekTo(it.toLong(), MediaPlayer.SEEK_CLOSEST) }
  }

  fun stop() {
    pause()
    seek(0f)
  }

  fun hide() {
    if (!playing && !requestedPlay) releaseNative()
  }

  private fun releaseNative() {
    val old = player
    runCatching { old?.currentPosition?.let(seekState::observe) }
    player = null
    runCatching { old?.release() }
    source?.close()
    source = null
    ready = false
    preparing = false
    seekState.reset(position)
  }

  fun close() {
    pause()
    waveJob.cancel()
    clockJob.cancel()
    metadataJob.cancel()
    metadata.cover?.fill(0)
    metadata = AudioMetadata()
    releaseNative()
    waveform?.fill(0f)
    waveform = null
    sourceReady.cancel()
    data?.fill(0)
    data = null
  }
}
