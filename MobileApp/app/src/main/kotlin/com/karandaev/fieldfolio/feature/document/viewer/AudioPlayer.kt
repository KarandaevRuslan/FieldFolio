package com.karandaev.fieldfolio.feature.document.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.models.decodeModelImage
import com.karandaev.fieldfolio.core.ui.components.actions.HintFilledIconButton
import com.karandaev.fieldfolio.core.ui.components.actions.HintIconButton
import com.karandaev.fieldfolio.core.ui.state.rememberViewerSession
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.core.ui.text.audioTagLabel
import com.karandaev.fieldfolio.core.ui.text.label
import com.karandaev.fieldfolio.core.ui.text.tr
import com.karandaev.fieldfolio.feature.document.state.DocumentAudioSession
import com.karandaev.fieldfolio.feature.document.state.action
import kotlinx.coroutines.*

@Composable
fun DocumentAudio(
  bytes: ByteArray?,
  session: DocumentAudioSession,
  key: String = "audio-${bytes?.contentHashCode()}",
  options: AudioPresentation = AudioPresentation(),
  resources: Map<String, ByteArray> = emptyMap(),
  name: String = "",
) {
  if (bytes == null) {
    Text(tr("missing"))
    return
  }
  val track = remember(session, key, bytes) { session.track(key, bytes) }
  DisposableEffect(track) {
    track.prepare()
    onDispose { track.hide() }
  }
  val failure = track.failed
  val ready = track.ready
  var lastFailure by rememberViewerSession("audio-failure-layout", key) { mutableStateOf(false) }
  SideEffect { if (failure) lastFailure = true else if (ready) lastFailure = false }
  val step by com.karandaev.fieldfolio.core.document.rememberAudioSeekSeconds()
  val cover = if (options.showCover) resources[options.coverAsset] ?: track.metadata.cover else null
  val tags = if (options.showTags) track.metadata.displayed(options) else emptyMap()
  var viewing by rememberViewerSession("audio-cover-open", key) { mutableStateOf(false) }
  if (viewing && cover != null) ImageResultDialog(cover, null, null) { viewing = false }
  AudioPlayerView(
    track.duration,
    track.position,
    track.playing,
    track.ready,
    failure || (lastFailure && !ready),
    track.waveform,
    track::seek,
    onToggle = { if (track.playing) track.pause() else track.play() },
    onStop = track::stop,
    stepMillis = step * 1000,
    header = {
      if (cover != null || tags.isNotEmpty())
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (cover != null) {
            val bitmap by
              produceState<android.graphics.Bitmap?>(null, cover) {
                value =
                  withContext(Dispatchers.Default) {
                    runCatching { decodeModelImage(cover, 256) }.getOrNull()
                  }
              }
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
              bitmap?.let {
                Image(
                  it.asImageBitmap(),
                  tr("view"),
                  Modifier.size(88.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { viewing = true }
                    .testTag("audio-player-cover"),
                  contentScale = ContentScale.Crop,
                )
              }
            }
          }
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val title = tags["title"] ?: name.takeIf { it.isNotBlank() }
            title?.let {
              Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
            tags["artist"]?.let {
              Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            tags
              .filterKeys { it !in setOf("title", "artist") }
              .entries
              .toList()
              .chunked(2)
              .forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                  pair.forEach { (tag, value) ->
                    Column(Modifier.weight(1f)) {
                      Text(
                        audioTagLabel(tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                  if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
              }
          }
        }
    },
  )
}

@Composable
internal fun AudioPlayerView(
  duration: Int,
  position: Int,
  playing: Boolean,
  ready: Boolean,
  failed: Boolean,
  waveform: FloatArray?,
  onSeek: (Float) -> Unit,
  onToggle: () -> Unit,
  onStop: () -> Unit,
  stepMillis: Int = 5000,
  header: @Composable () -> Unit = {},
) {
  var dragging by remember { mutableStateOf<Float?>(null) }
  val progress = dragging ?: if (duration > 0) position.toFloat() / duration else 0f
  val played = MaterialTheme.colorScheme.primary
  val unplayed = MaterialTheme.colorScheme.outlineVariant
  val currentSeek by rememberUpdatedState(onSeek)
  fun seek(value: Float) {
    if (ready) currentSeek(value.coerceIn(0f, 1f))
  }
  val waveformDescription = tr("waveform")
  Card(Modifier.fillMaxWidth().testTag("audio-player")) {
    Column(
      Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      header()
      val waveModifier =
        Modifier.fillMaxWidth()
          .height(72.dp)
          .testTag("audio-waveform")
          .semantics {
            contentDescription = waveformDescription
            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            setProgress {
              if (ready) {
                seek(it)
                true
              } else false
            }
          }
          .pointerInput(ready, duration) {
            if (ready)
              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                dragging = (down.position.x / size.width).coerceIn(0f, 1f)
                dragging?.let(::seek)
                try {
                  var pressed = true
                  var released = false
                  while (pressed) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id }
                    if (pointer == null) break
                    dragging = (pointer.position.x / size.width).coerceIn(0f, 1f)
                    dragging?.let(::seek)
                    pointer.consume()
                    pressed = pointer.pressed
                    released = !pointer.pressed
                  }
                  if (released) dragging?.let(::seek)
                } finally {
                  dragging = null
                }
              }
          }

      Canvas(waveModifier) {
        val data = waveform
        if (data != null) {
          val step = size.width / data.size
          data.forEachIndexed { i, value ->
            val h = maxOf(2.dp.toPx(), value * size.height * .9f)
            val x = (i + .5f) * step
            drawLine(
              if (i.toFloat() / data.size <= progress) played else unplayed,
              Offset(x, (size.height - h) / 2),
              Offset(x, (size.height + h) / 2),
              strokeWidth = maxOf(1f, step * .55f),
              cap = StrokeCap.Round,
            )
          }
        } else {
          drawLine(
            unplayed,
            Offset(0f, size.height / 2),
            Offset(size.width, size.height / 2),
            strokeWidth = 3.dp.toPx(),
          )
          drawLine(
            played,
            Offset(0f, size.height / 2),
            Offset(size.width * progress, size.height / 2),
            strokeWidth = 3.dp.toPx(),
          )
        }
      }
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        val currentPosition by rememberUpdatedState(position)
        RepeatingAudioButton(
          Icons.Default.FastRewind,
          androidx.compose.ui.res.pluralStringResource(
            com.karandaev.fieldfolio.R.plurals.doc_rewind_seconds,
            stepMillis / 1000,
            stepMillis / 1000,
          ),
          ready && !failed,
        ) {
          seek(
            (currentPosition - stepMillis).coerceAtLeast(0).toFloat() / duration.coerceAtLeast(1)
          )
        }
        HintFilledIconButton(
          onToggle,
          enabled = ready && !failed,
          tooltip = tr(if (playing) "pause" else "play"),
        ) {
          Icon(
            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            tr(if (playing) "pause" else "play"),
          )
        }
        HintIconButton(onStop, enabled = ready && !failed, tooltip = tr("stop")) {
          Icon(Icons.Default.Stop, tr("stop"))
        }
        RepeatingAudioButton(
          Icons.Default.FastForward,
          androidx.compose.ui.res.pluralStringResource(
            com.karandaev.fieldfolio.R.plurals.doc_forward_seconds,
            stepMillis / 1000,
            stepMillis / 1000,
          ),
          ready && !failed,
        ) {
          seek(
            (currentPosition + stepMillis).coerceAtMost(duration).toFloat() /
              duration.coerceAtLeast(1)
          )
        }
      }
      Text(
        "${audioTime(if(dragging!=null)seekPosition(progress,duration)else position,true)} / ${audioTime(duration,true)}",
        style = MaterialTheme.typography.labelMedium,
      )
      if (failed) Text(tr("error"))
      Box(Modifier.fillMaxWidth().height(4.dp)) {
        if (!failed && !ready) LinearProgressIndicator(Modifier.fillMaxWidth())
      }
    }
  }
}

/** Accessible single click plus cancellable repeat while the finger remains down. */
@Composable
internal fun RepeatingAudioButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  enabled: Boolean,
  action: () -> Unit,
) {
  val currentAction by rememberUpdatedState(action)
  val color =
    if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = .38f)
  val interactions = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  com.karandaev.fieldfolio.core.ui.components.actions.IconHint(label, observeHold = true) {
    Surface(
      shape = androidx.compose.foundation.shape.CircleShape,
      color = androidx.compose.ui.graphics.Color.Transparent,
      modifier =
        Modifier.size(48.dp)
          .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = label
            if (!enabled) disabled()
            onClick {
              if (enabled) {
                currentAction()
                true
              } else false
            }
          }
          .then(Modifier.indication(interactions, androidx.compose.material3.ripple()))
          .pointerInput(enabled) {
            if (enabled) {
              var repeated = false
              detectTapGestures(
                onTap = { if (!repeated) currentAction() },
                onLongPress = {},
                onPress = { point ->
                  repeated = false
                  val press = androidx.compose.foundation.interaction.PressInteraction.Press(point)
                  interactions.emit(press)
                  coroutineScope {
                    val repeating = launch {
                      delay(350)
                      while (isActive) {
                        repeated = true
                        currentAction()
                        delay(200)
                      }
                    }
                    try {
                      if (tryAwaitRelease())
                        interactions.emit(
                          androidx.compose.foundation.interaction.PressInteraction.Release(press)
                        )
                      else
                        interactions.emit(
                          androidx.compose.foundation.interaction.PressInteraction.Cancel(press)
                        )
                    } finally {
                      repeating.cancelAndJoin()
                    }
                  }
                },
              )
            }
          },
    ) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color)
      }
    }
  }
}

internal class AudioWaveformState {
  var values by mutableStateOf<FloatArray?>(null)
  var complete = false
}
