package com.karandaev.fieldfolio.core.document

import android.media.*
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Streaming PCM envelope; never retains decoded audio or writes a decrypted temporary file. */
class WaveformAccumulator(val bins: Int = 160) {
  private val peaks = FloatArray(bins)

  fun sample(position: Double, amplitude: Float) {
    if (!position.isFinite() || !amplitude.isFinite()) return
    val index = (position.coerceIn(0.0, .999999) * bins).toInt()
    peaks[index] = max(peaks[index], abs(amplitude).coerceIn(0f, 1f))
  }

  fun result(): FloatArray {
    val top = peaks.maxOrNull()?.coerceAtLeast(.001f) ?: 1f
    return FloatArray(bins) { (peaks[it] / top).coerceIn(0f, 1f) }
  }
}

fun seekPosition(fraction: Float, duration: Int): Int =
  if (!fraction.isFinite()) 0
  else kotlin.math.round(fraction.coerceIn(0f, 1f) * duration.coerceAtLeast(0)).toInt()

fun audioTime(millis: Int, withMillis: Boolean = false): String {
  val s = millis.coerceAtLeast(0) / 1000
  val value =
    if (s >= 3600)
      "${s/3600}:${(s/60%60).toString().padStart(2,'0')}:${(s%60).toString().padStart(2,'0')}"
    else "${s/60}:${(s%60).toString().padStart(2,'0')}"
  return value +
    if (withMillis) ".${(millis.coerceAtLeast(0)%1000).toString().padStart(3,'0')}" else ""
}

internal class ByteArrayMediaSource(bytes: ByteArray, private val ownsCopy: Boolean = true) :
  MediaDataSource() {
  private val data = if (ownsCopy) bytes.copyOf() else bytes

  override fun getSize() = data.size.toLong()

  override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
    if (size == 0) return 0
    if (position < 0 || position >= data.size) return -1
    val n = minOf(size, data.size - position.toInt())
    data.copyInto(buffer, offset, position.toInt(), position.toInt() + n)
    return n
  }

  override fun close() {
    if (ownsCopy) data.fill(0)
  }
}

suspend fun decodeAudioWaveform(
  bytes: ByteArray,
  onProgress: ((FloatArray) -> Unit)? = null,
): FloatArray =
  withContext(Dispatchers.IO) {
    decodePcmWave(bytes, onProgress)?.let {
      return@withContext it
    }
    val source = ByteArrayMediaSource(bytes)
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
      extractor.setDataSource(source)
      val index =
        (0 until extractor.trackCount).firstOrNull {
          extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("No audio track")
      extractor.selectTrack(index)
      val format = extractor.getTrackFormat(index)
      val duration = format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1)
      val decoder =
        MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
      codec = decoder
      decoder.configure(format, null, null, 0)
      decoder.start()
      val accumulator = WaveformAccumulator()
      val info = MediaCodec.BufferInfo()
      var lastPreview = 0L
      var inputDone = false
      var outputDone = false
      var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      var pcm = AudioFormat.ENCODING_PCM_16BIT
      while (!outputDone) {
        currentCoroutineContext().ensureActive()
        if (!inputDone) {
          val slot = decoder.dequeueInputBuffer(10000)
          if (slot >= 0) {
            val buffer = requireNotNull(decoder.getInputBuffer(slot))
            val n = extractor.readSampleData(buffer, 0)
            if (n < 0) {
              decoder.queueInputBuffer(slot, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              decoder.queueInputBuffer(slot, 0, n, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }
        val slot = decoder.dequeueOutputBuffer(info, 10000)
        when {
          slot == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val out = decoder.outputFormat
            sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            pcm =
              if (out.containsKey(MediaFormat.KEY_PCM_ENCODING))
                out.getInteger(MediaFormat.KEY_PCM_ENCODING)
              else AudioFormat.ENCODING_PCM_16BIT
          }
          slot >= 0 -> {
            val buffer = decoder.getOutputBuffer(slot)
            if (buffer != null && info.size > 0) {
              buffer.position(info.offset)
              buffer.limit(info.offset + info.size)
              buffer.order(ByteOrder.LITTLE_ENDIAN)
              var sample = 0
              while (buffer.remaining() >= (if (pcm == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2)) {
                val value =
                  if (pcm == AudioFormat.ENCODING_PCM_FLOAT) buffer.float else buffer.short / 32768f
                val time = info.presentationTimeUs + (sample / channels) * 1_000_000L / sampleRate
                accumulator.sample(time.toDouble() / duration, value)
                sample++
              }
            }
            val now = android.os.SystemClock.elapsedRealtime()
            if (
              onProgress != null && info.size > 0 && (lastPreview == 0L || now - lastPreview >= 80L)
            ) {
              onProgress(accumulator.result())
              lastPreview = now
            }
            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            decoder.releaseOutputBuffer(slot, false)
          }
        }
      }
      accumulator.result()
    } finally {
      runCatching { codec?.stop() }
      codec?.release()
      extractor.release()
      source.close()
    }
  }

/** PCM WAV can be inspected immediately, without waiting for a platform decoder or playback. */
internal suspend fun decodePcmWave(
  bytes: ByteArray,
  onProgress: ((FloatArray) -> Unit)?,
): FloatArray? {
  if (
    bytes.size < 44 ||
      String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" ||
      String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE"
  )
    return null
  val buffer = java.nio.ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
  var offset = 12
  var encoding = 0
  var channels = 0
  var bits = 0
  var stride = 0
  var data = -1
  var length = 0
  while (offset + 8 <= bytes.size) {
    val count = buffer.getInt(offset + 4).toLong() and 0xffffffffL
    if (count > bytes.size - offset - 8L) return null
    when (String(bytes, offset, 4, Charsets.US_ASCII)) {
      "fmt " -> {
        if (count < 16) return null
        encoding = buffer.getShort(offset + 8).toInt() and 65535
        channels = buffer.getShort(offset + 10).toInt() and 65535
        stride = buffer.getShort(offset + 20).toInt() and 65535
        bits = buffer.getShort(offset + 22).toInt() and 65535
      }
      "data" -> {
        data = offset + 8
        length = count.toInt()
      }
    }
    offset += (8 + count + (count % 2)).toInt()
  }
  if (
    data < 0 ||
      channels !in 1..32 ||
      bits !in listOf(8, 16, 24, 32) ||
      encoding !in listOf(1, 3) ||
      encoding == 3 && bits != 32
  )
    return null
  val sampleBytes = bits / 8
  if (stride < sampleBytes * channels || length < stride) return null
  val frames = length / stride
  val envelope = WaveformAccumulator()
  for (frame in 0 until frames) {
    if (frame % 4096 == 0) {
      currentCoroutineContext().ensureActive()
      if (frame > 0 && onProgress != null) onProgress(envelope.result())
    }
    for (channel in 0 until channels) {
      val at = data + frame * stride + channel * sampleBytes
      val amplitude =
        when {
          encoding == 3 -> buffer.getFloat(at)
          bits == 8 -> ((bytes[at].toInt() and 255) - 128) / 128f
          bits == 16 -> buffer.getShort(at) / 32768f
          bits == 24 -> {
            val value =
              (bytes[at].toInt() and 255) or
                ((bytes[at + 1].toInt() and 255) shl 8) or
                (bytes[at + 2].toInt() shl 16)
            value / 8388608f
          }
          else -> buffer.getInt(at) / 2147483648f
        }
      envelope.sample(frame.toDouble() / frames, amplitude)
    }
  }
  return envelope.result().also { onProgress?.invoke(it) }
}
