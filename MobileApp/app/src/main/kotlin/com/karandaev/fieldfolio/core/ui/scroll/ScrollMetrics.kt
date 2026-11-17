package com.karandaev.fieldfolio.core.ui.scroll

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import kotlin.math.*

val ScrollIndicatorProgress = SemanticsPropertyKey<Float>("ScrollIndicatorProgress")
val ScrollIndicatorVisible = SemanticsPropertyKey<Boolean>("ScrollIndicatorVisible")

data class ScrollWindow(
  val progress: Float,
  val visibleFraction: Float,
  val scrollable: Boolean,
  val unitPixels: Float = 1f,
  val totalUnits: Int = 1,
  val visibleUnits: Float = 1f,
  val scrollPixels: Float = 1f,
)

internal fun LazyListState.window(cache: LazyExtentCache): ScrollWindow {
  val info = layoutInfo
  val rects =
    info.visibleItemsInfo
      .filter { it.index >= firstVisibleItemIndex }
      .map {
        ScrollItemRect(
          it.index,
          if (it.index == firstVisibleItemIndex) -firstVisibleItemScrollOffset.toFloat()
          else it.offset.toFloat(),
          it.size.toFloat(),
          it.key,
        )
      }
  return coverageWindow(
      rects,
      info.totalItemsCount,
      info.viewportStartOffset.toFloat(),
      info.viewportEndOffset.toFloat(),
      info.mainAxisItemSpacing.toFloat(),
      canScrollBackward,
      canScrollForward,
    )
    .copy(
      scrollPixels =
        cache.range(
          rects,
          info.totalItemsCount,
          (info.viewportEndOffset - info.viewportStartOffset).toFloat(),
          info.mainAxisItemSpacing.toFloat(),
          (info.beforeContentPadding + info.afterContentPadding).toFloat(),
        )
    )
}

internal fun LazyGridState.window(columns: Int, cache: LazyExtentCache): ScrollWindow {
  val info = layoutInfo
  val rows =
    info.visibleItemsInfo
      .filter { it.row >= 0 }
      .groupBy { it.row }
      .map { (row, items) ->
        ScrollItemRect(
          row,
          items.minOf { it.offset.y }.toFloat(),
          items.maxOf { it.size.height }.toFloat(),
          items.map { it.key },
        )
      }
  return coverageWindow(
      rows,
      (info.totalItemsCount + columns - 1) / columns,
      info.viewportStartOffset.toFloat(),
      info.viewportEndOffset.toFloat(),
      info.mainAxisItemSpacing.toFloat(),
      canScrollBackward,
      canScrollForward,
    )
    .copy(
      scrollPixels =
        cache.range(
          rows,
          (info.totalItemsCount + columns - 1) / columns,
          (info.viewportEndOffset - info.viewportStartOffset).toFloat(),
          info.mainAxisItemSpacing.toFloat(),
          (info.beforeContentPadding + info.afterContentPadding).toFloat(),
        )
    )
}
