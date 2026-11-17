package com.karandaev.fieldfolio.core.ui.scroll

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

internal class ThumbSession(
  val generation: Int,
  val initial: ScrollWindow,
  val jump: Boolean,
  var applied: Float,
  var started: Boolean = false,
)

internal data class ThumbRequest(
  val session: ThumbSession,
  val target: Float,
  val complete: kotlinx.coroutines.CompletableDeferred<Unit>? = null,
)

internal fun Modifier.edgeScrollbar(
  window: () -> ScrollWindow,
  seek: suspend (Float, ScrollWindow) -> Unit,
  delta: suspend (Float) -> Unit,
): Modifier = composed {
  val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = .7f)
  val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
  val scope = rememberCoroutineScope()
  val currentWindow by rememberUpdatedState(window)
  val currentSeek by rememberUpdatedState(seek)
  val currentDelta by rememberUpdatedState(delta)
  var calibration by remember { mutableStateOf(ScrollCalibration()) }
  var gestureGeneration by remember { mutableIntStateOf(0) }
  var dragFraction by remember { mutableStateOf<Float?>(null) }
  var dragWindow by remember { mutableStateOf<ScrollWindow?>(null) }
  val requests = remember {
    kotlinx.coroutines.channels.Channel<ThumbRequest>(
      kotlinx.coroutines.channels.Channel.CONFLATED,
      onUndeliveredElement = { it.complete?.complete(Unit) },
    )
  }
  LaunchedEffect(Unit) {
    var movement: kotlinx.coroutines.Job? = null
    for (request in requests) {
      // At most one scroll mutation per rendered frame. New finger positions replace queued ones.
      withFrameNanos {}
      var latest = request
      while (true) {
        val next = requests.tryReceive().getOrNull() ?: break
        latest.complete?.complete(Unit)
        latest = next
      }
      // A new finger sample cancels an unfinished boundary animation instead of waiting behind it.
      movement?.cancelAndJoin()
      movement = launch {
        val session = latest.session
        try {
          if (session.generation != gestureGeneration) return@launch
          val target = latest.target
          val previous = session.applied
          val jump = !session.started && session.jump
          session.started = true
          session.applied = target
          if (jump || target == 0f || target == 1f) {
            if (jump || currentWindow().progress != target) currentSeek(target, session.initial)
          } else currentDelta((target - previous) * session.initial.scrollPixels)
          val actual = currentWindow().progress
          val displayed = if (actual == 0f || actual == 1f) actual else target
          calibration = ScrollCalibration(actual, displayed)
          if (session.generation == gestureGeneration && dragFraction != null)
            dragFraction = displayed
        } finally {
          latest.complete?.complete(Unit)
        }
      }
    }
  }
  var height by remember { mutableIntStateOf(0) }
  this.onSizeChanged { height = it.height }
    .semantics {
      val w = currentWindow()
      this[ScrollIndicatorVisible] = w.scrollable
      this[ScrollIndicatorProgress] = dragFraction ?: calibration.map(w.progress)
      if (w.scrollable)
        setProgress { value ->
          scope.launch {
            val target = value.coerceIn(0f, 1f)
            currentSeek(target, currentWindow())
            calibration = ScrollCalibration(currentWindow().progress, target)
          }
          true
        }
    }
    .pointerInput(Unit) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val current = currentWindow()
        if (
          current.scrollable &&
            down.position.x >= size.width - minOf(20.dp.toPx(), size.width * .45f)
        ) {
          gestureGeneration++
          val generation = gestureGeneration
          dragWindow = current
          val track = (height - 8.dp.toPx()).coerceAtLeast(1f)
          val thumb = (track * current.visibleFraction).coerceIn(minOf(48.dp.toPx(), track), track)
          val travel = (track - thumb).coerceAtLeast(1f)
          val shown = calibration.map(current.progress)
          val top = 4.dp.toPx() + travel * shown
          val session =
            ThumbSession(generation, current, down.position.y !in top..top + thumb, shown)
          val anchor = if (down.position.y in top..top + thumb) down.position.y - top else thumb / 2
          fun move(y: Float) {
            val fraction = ((y - 4.dp.toPx() - anchor) / travel).coerceIn(0f, 1f)
            dragFraction = fraction
            requests.trySend(ThumbRequest(session, fraction))
          }
          down.consume()
          move(down.position.y)
          var pressed = true
          try {
            while (pressed) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              val pointer = event.changes.firstOrNull { it.id == down.id }
              if (pointer != null) {
                move(pointer.position.y)
                pointer.consume()
                pressed = pointer.pressed
              } else pressed = false
            }
          } finally {
            try {
              val finalFraction = dragFraction
              if (finalFraction != null) {
                val complete = kotlinx.coroutines.CompletableDeferred<Unit>()
                requests.trySend(ThumbRequest(session, finalFraction, complete))
                scope.launch {
                  complete.await()
                  if (gestureGeneration == generation) {
                    dragFraction = null
                    dragWindow = null
                  }
                }
              }
            } finally {
              if (dragFraction == null) dragWindow = null
            }
          }
        }
      }
    }
    .drawWithContent {
      drawContent()
      val w = dragWindow ?: currentWindow()
      if (w.scrollable) {
        val progress = dragFraction ?: calibration.map(w.progress)
        val margin = 4.dp.toPx()
        val track = (size.height - 2 * margin).coerceAtLeast(1f)
        val length = (track * w.visibleFraction).coerceIn(minOf(48.dp.toPx(), track), track)
        val x = size.width - 10.dp.toPx()
        drawRoundRect(
          trackColor,
          Offset(x + 2.dp.toPx(), margin),
          Size(4.dp.toPx(), track),
          CornerRadius(4.dp.toPx()),
        )
        drawRoundRect(
          thumbColor,
          Offset(x, margin + (track - length) * progress),
          Size(8.dp.toPx(), length),
          CornerRadius(5.dp.toPx()),
        )
      }
    }
}

/** Same scroll semantics as Foundation, with a persistent draggable edge thumb only on overflow. */
