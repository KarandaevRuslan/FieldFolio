package com.karandaev.fieldfolio.core.ui.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.verticalScroll as platformVerticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

fun Modifier.verticalScroll(
  state: ScrollState,
  enabled: Boolean = true,
  flingBehavior: FlingBehavior? = null,
  reverseScrolling: Boolean = false,
  reserveScrollbarGutter: Boolean = true,
): Modifier =
  edgeScrollbar(
      {
        val visible =
          state.viewportSize.toFloat() /
            (state.viewportSize.toLong() + state.maxValue).coerceAtLeast(1)
        ScrollWindow(
          if (state.maxValue > 0) state.value.toFloat() / state.maxValue else 0f,
          visible,
          state.canScrollBackward || state.canScrollForward,
          unitPixels = state.maxValue.toFloat(),
          scrollPixels = state.maxValue.toFloat(),
        )
      },
      seek = { fraction, geometry ->
        state.scrollTo((fraction * geometry.unitPixels).roundToInt())
      },
      delta = { state.scrollBy(it) },
    )
    .platformVerticalScroll(state, enabled, flingBehavior, reverseScrolling)
    .padding(
      end =
        if (reserveScrollbarGutter && (state.canScrollBackward || state.canScrollForward)) 14.dp
        else 0.dp
    )

@Composable
fun AppLazyColumn(
  modifier: Modifier = Modifier,
  state: LazyListState = rememberLazyListState(),
  contentPadding: PaddingValues = PaddingValues(0.dp),
  reverseLayout: Boolean = false,
  verticalArrangement: Arrangement.Vertical =
    if (reverseLayout) Arrangement.Bottom else Arrangement.Top,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  flingBehavior: FlingBehavior =
    androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior(),
  userScrollEnabled: Boolean = true,
  content: LazyListScope.() -> Unit,
) {
  val extents = remember(state) { LazyExtentCache() }
  LazyColumn(
    modifier.edgeScrollbar(
      { state.window(extents) },
      seek = { fraction, w ->
        val index = (fraction * (w.totalUnits - w.visibleUnits).coerceAtLeast(0f)).toInt()
        if (fraction == 0f) state.animateScrollToItem(0)
        else if (fraction == 1f)
          state.animateScrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        else
          state.scrollToItem(
            if (fraction == 1f) (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0) else index,
            if (fraction == 1f) 0
            else
              ((fraction * (w.totalUnits - w.visibleUnits).coerceAtLeast(0f) - index) *
                  w.unitPixels)
                .roundToInt(),
          )
        if (fraction == 1f) {
          val info = state.layoutInfo
          val bottom =
            info.visibleItemsInfo.maxOfOrNull { it.offset + it.size } ?: info.viewportEndOffset
          state.animateScrollBy(
            (bottom + info.afterContentPadding - info.viewportEndOffset).coerceAtLeast(0).toFloat(),
            androidx.compose.animation.core.tween(120),
          )
        }
      },
      delta = { state.scrollBy(it) },
    ),
    state,
    scrollbarPadding(contentPadding, state.canScrollBackward || state.canScrollForward),
    reverseLayout,
    verticalArrangement,
    horizontalAlignment,
    flingBehavior,
    userScrollEnabled,
    content,
  )
}

@Composable
fun AppLazyGrid(
  columns: Int,
  modifier: Modifier = Modifier,
  state: LazyGridState = rememberLazyGridState(),
  contentPadding: PaddingValues = PaddingValues(0.dp),
  verticalArrangement: Arrangement.Vertical = Arrangement.Top,
  horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
  content: LazyGridScope.() -> Unit,
) {
  val extents = remember(state, columns) { LazyExtentCache() }
  LazyVerticalGrid(
    GridCells.Fixed(columns),
    modifier.edgeScrollbar(
      { state.window(columns, extents) },
      seek = { fraction, w ->
        val row = (fraction * (w.totalUnits - w.visibleUnits).coerceAtLeast(0f)).toInt()
        if (fraction == 0f) state.animateScrollToItem(0)
        else if (fraction == 1f)
          state.animateScrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        else
          state.scrollToItem(
            if (fraction == 1f) (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            else row * columns,
            if (fraction == 1f) 0
            else
              ((fraction * (w.totalUnits - w.visibleUnits).coerceAtLeast(0f) - row) * w.unitPixels)
                .roundToInt(),
          )
        if (fraction == 1f) {
          val info = state.layoutInfo
          val bottom =
            info.visibleItemsInfo.maxOfOrNull { it.offset.y + it.size.height }
              ?: info.viewportEndOffset
          state.animateScrollBy(
            (bottom + info.afterContentPadding - info.viewportEndOffset).coerceAtLeast(0).toFloat(),
            androidx.compose.animation.core.tween(120),
          )
        }
      },
      delta = { state.scrollBy(it) },
    ),
    state,
    contentPadding =
      scrollbarPadding(contentPadding, state.canScrollBackward || state.canScrollForward),
    verticalArrangement = verticalArrangement,
    horizontalArrangement = horizontalArrangement,
    content = content,
  )
}

/** Masonry keeps unequal-height report cells packed with one shared scroll container. */
@Composable
fun AppLazyStaggeredGrid(
  columns: Int,
  modifier: Modifier = Modifier,
  state: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState =
    androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState(),
  content: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope.() -> Unit,
) {
  val extents = remember(state, columns) { LazyExtentCache() }
  val itemGap = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }
  androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid(
    columns = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Fixed(columns),
    modifier =
      modifier.edgeScrollbar(
        {
          val info = state.layoutInfo
          val rects =
            info.visibleItemsInfo.map {
              ScrollItemRect(it.index, it.offset.y.toFloat(), it.size.height.toFloat(), it.key)
            }
          coverageWindow(
              rects,
              info.totalItemsCount,
              info.viewportStartOffset.toFloat(),
              info.viewportEndOffset.toFloat(),
              itemGap,
              state.canScrollBackward,
              state.canScrollForward,
              columns,
            )
            .copy(
              scrollPixels =
                extents.range(
                  rects,
                  info.totalItemsCount,
                  (info.viewportEndOffset - info.viewportStartOffset).toFloat(),
                  itemGap,
                  itemGap * 2,
                  columns,
                )
            )
        },
        seek = { fraction, w ->
          val count = state.layoutInfo.totalItemsCount
          if (count > 0 && fraction == 0f) state.animateScrollToItem(0)
          else if (count > 0 && fraction == 1f) state.animateScrollToItem(count - 1)
          else if (count > 0)
            state.scrollToItem(
              if (fraction == 1f) count - 1
              else (fraction * (count - w.visibleUnits)).toInt().coerceIn(0, count - 1)
            )
          if (fraction == 1f && count > 0) {
            val info = state.layoutInfo
            val bottom =
              info.visibleItemsInfo.maxOfOrNull { it.offset.y + it.size.height }
                ?: info.viewportEndOffset
            state.animateScrollBy(
              (bottom + itemGap - info.viewportEndOffset).coerceAtLeast(0f),
              androidx.compose.animation.core.tween(120),
            )
            // Staggered layout may keep a tall cell in another lane beyond the last index.
            while (state.canScrollForward) {
              val geometry = state.layoutInfo
              val excess =
                (geometry.visibleItemsInfo.maxOfOrNull { it.offset.y + it.size.height }
                  ?: geometry.viewportEndOffset) + itemGap - geometry.viewportEndOffset
              val consumed =
                state.animateScrollBy(
                  excess.coerceAtLeast(1f),
                  androidx.compose.animation.core.tween(120),
                )
              if (consumed < .5f) break
            }
          }
        },
        delta = { state.scrollBy(it) },
      ),
    state = state,
    contentPadding = PaddingValues(12.dp),
    verticalItemSpacing = 12.dp,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    content = content,
  )
}

@Composable
internal fun scrollbarPadding(padding: PaddingValues, overflow: Boolean): PaddingValues {
  val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
  return PaddingValues(
    start = padding.calculateStartPadding(direction),
    top = padding.calculateTopPadding(),
    end = padding.calculateEndPadding(direction) + (if (overflow) 14.dp else 0.dp),
    bottom = padding.calculateBottomPadding(),
  )
}
