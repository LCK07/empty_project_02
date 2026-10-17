package com.lck07.lumen.ui.mylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lck07.lumen.R
import com.lck07.lumen.container
import androidx.compose.ui.layout.layout
import com.lck07.lumen.design.ChannelTile
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.TvRow
import com.lck07.lumen.ui.live.favouriteChannelTiles
import com.lck07.lumen.ui.live.rememberFavouriteToggle
import com.lck07.lumen.design.LumenSpacing
import com.lck07.lumen.design.MessageState
import com.lck07.lumen.design.PosterTile
import com.lck07.lumen.design.SegmentedControl
import com.lck07.lumen.ui.TabChrome
import com.lck07.lumen.ui.home.titlePosters
import com.lck07.lumen.ui.model.PosterItem
import com.lck07.lumen.ui.model.Target

private enum class Segment { Favourites, Watchlist, Continue }

/**
 * My List: the active profile's favourites, watchlist and Continue Watching as one poster grid,
 * movies and series labelled. Long-press on a poster opens the favourites/watchlist menu.
 */
@Composable
fun MyListScreen(chrome: TabChrome, onOpen: (Target) -> Unit) {
    val c = LocalContext.current.container
    val res = LocalContext.current.resources
    var segment by rememberSaveable { mutableStateOf(Segment.Favourites) }
    // Each source re-emits when the active profile changes.
    val items by remember(segment) {
        titlePosters(c, res, when (segment) {
            Segment.Favourites -> c.library.favouriteTitles()
            Segment.Watchlist -> c.library.watchlistTitles()
            Segment.Continue -> c.library.continueTitles()
        })
    }.collectAsState(initial = null)
    val channels by remember { favouriteChannelTiles(c) }.collectAsState(initial = emptyList())
    val toggleFavourite = rememberFavouriteToggle()
    val showChannels = segment == Segment.Favourites && channels.isNotEmpty()

    val grid = rememberLazyGridState()
    val atTop by remember { derivedStateOf { grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset < 60 } }
    LaunchedEffect(atTop) { chrome.contentAtTop = atTop }
    LaunchedEffect(chrome.scrollToTop) { if (chrome.scrollToTop > 0) grid.animateScrollToItem(0) }

    val segmentLabels = Segment.entries.associateWith {
        stringResource(when (it) { Segment.Favourites -> R.string.row_favourites; Segment.Watchlist -> R.string.row_watchlist; Segment.Continue -> R.string.row_continue_watching })
    }
    val movie = stringResource(R.string.kind_movie)
    val series = stringResource(R.string.kind_series)
    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize().testTag("mylist"),
        contentPadding = PaddingValues(start = LumenSpacing.SafeX, end = LumenSpacing.SafeX, top = 100.dp, bottom = 70.dp),
        horizontalArrangement = Arrangement.spacedBy(LumenSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "segments") {
            SegmentedControl(
                Segment.entries, segment,
                { segmentLabels.getValue(it) },
                { segment = it }, Modifier.testTag("mylist:segments"),
            )
        }
        // Favourite channels above the movies and series; Select watches, a long press removes.
        if (showChannels) item(span = { GridItemSpan(maxLineSpan) }, key = "channels") {
            TvRow(
                stringResource(R.string.tab_live), channels, { it.id },
                Modifier.bleed(LumenSpacing.SafeX), tag = "mylist:channels",
            ) { item, slot ->
                ChannelTile(item, { onOpen(Target.PlayChannel(item.id)) }, slot = slot, onLongClick = { toggleFavourite(item.id) })
            }
        }
        val shown = items
        if (shown != null && shown.isEmpty() && !showChannels) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                MessageState(
                    stringResource(when (segment) { Segment.Favourites -> R.string.row_favourites; Segment.Watchlist -> R.string.row_watchlist; Segment.Continue -> R.string.row_continue_watching }),
                    stringResource(when (segment) { Segment.Favourites -> R.string.mylist_empty_favourites; Segment.Watchlist -> R.string.mylist_empty_watchlist; Segment.Continue -> R.string.mylist_empty_continue }),
                    icon = LumenIcons.Heart,
                )
            }
        }
        items(shown.orEmpty(), key = { it.key }) { item: PosterItem ->
            PosterTile(item, { onOpen(item.target) }, badge = if (item.target is Target.MovieDetail) movie else series)
        }
    }
}

/** Draws [extra] past both sides of the grid's padding, so a row scrolls edge to edge like on the other tabs. */
private fun Modifier.bleed(extra: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val e = extra.roundToPx()
    val p = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + 2 * e, maxWidth = constraints.maxWidth + 2 * e))
    layout(constraints.maxWidth, p.height) { p.place(-e, 0) }
}
