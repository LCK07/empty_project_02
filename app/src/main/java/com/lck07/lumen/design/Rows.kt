package com.lck07.lumen.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Horizontal rows scroll just enough to keep the focused tile inside the safe area, so the
 * next tile peeks in at the edge and shows that the row continues.
 */
@OptIn(ExperimentalFoundationApi::class)
class SafeAreaBringIntoView(private val marginPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leading = offset - marginPx
        val trailing = offset + size - (containerSize - marginPx)
        return when {
            leading < 0f -> leading
            trailing > 0f -> minOf(trailing, leading)
            else -> 0f
        }
    }
}

/** Pages scroll themselves when focus changes rows (see [revealOnFocus]), never on their own. */
@OptIn(ExperimentalFoundationApi::class)
object NoBringIntoView : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/** Where a focused row's top sits on screen (470 pt in the mockup). */
val RowAnchor: Dp = 235.dp

/**
 * When something inside gains focus, scrolls the page so this item's top sits at [anchor]
 * (or the page top for the hero), like the mockup's page slide.
 */
@Composable
fun Modifier.revealOnFocus(list: LazyListState, index: Int, anchor: Dp = RowAnchor): Modifier {
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val anchorPx = with(LocalDensity.current) { anchor.roundToPx() }
    return onFocusChanged { f ->
        if (f.hasFocus) scope.launch {
            val offset = if (index == 0 && anchor == 0.dp) 0 else -anchorPx
            if (reduce) list.scrollToItem(index, offset) else list.animateScrollToItem(index, offset)
        }
    }
}

/**
 * A titled row of tiles. It remembers its last focused tile, so focus entering the row again
 * returns there; the tiles bump at the ends instead of wrapping. [onNearEnd] asks for more
 * items when focus gets close to the end (paged rows).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> TvRow(
    title: String?,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    captionSpace: Dp = 22.dp,
    spacing: Dp = LumenSpacing.Gutter,
    tag: String? = null,
    onNearEnd: (() -> Unit)? = null,
    tile: @Composable (item: T, slot: RowSlot) -> Unit,
) {
    val density = LocalDensity.current
    val spec = remember(density) { SafeAreaBringIntoView(with(density) { LumenSpacing.SafeX.toPx() }) }
    Column(modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        if (title != null) Txt(title, LumenType.Headline, Modifier.padding(start = LumenSpacing.SafeX, bottom = 2.dp))
        CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            LazyRow(
                state = state,
                modifier = Modifier.fillMaxWidth().focusRestorer(),
                contentPadding = PaddingValues(
                    start = LumenSpacing.SafeX, end = LumenSpacing.SafeX,
                    top = LumenSpacing.RowPadTop, bottom = LumenSpacing.RowPadBottom + captionSpace,
                ),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                itemsIndexed(items, key = { _, it -> key(it) }) { i, item ->
                    tile(item, RowSlot(i, items.size))
                    if (onNearEnd != null && i == items.size - 1) LaunchedEffect(items.size) { onNearEnd() }
                }
            }
        }
    }
}

/** Vertical rhythm of a page of rows. */
val RowSpacing: Arrangement.Vertical = Arrangement.spacedBy(LumenSpacing.RowGap - 22.dp)
