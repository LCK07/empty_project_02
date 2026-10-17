package com.lck07.lumen.ui.live

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.lck07.lumen.AppContainer
import com.lck07.lumen.R
import com.lck07.lumen.container
import com.lck07.lumen.data.CatalogRepository
import com.lck07.lumen.data.FavouriteKind
import com.lck07.lumen.data.db.ChannelEntity
import com.lck07.lumen.data.toModel
import androidx.compose.ui.layout.layout
import com.lck07.lumen.design.ButtonRow
import com.lck07.lumen.design.ChannelTile
import com.lck07.lumen.design.Eyebrow
import com.lck07.lumen.design.LumenButton
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.LumenSpacing
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.LumenType.tabular
import com.lck07.lumen.design.MessageState
import com.lck07.lumen.design.NoBringIntoView
import com.lck07.lumen.design.ProgressBar
import com.lck07.lumen.design.RowSpacing
import com.lck07.lumen.design.TvRow
import com.lck07.lumen.design.Txt
import com.lck07.lumen.design.generatedArt
import com.lck07.lumen.design.heroScrim
import com.lck07.lumen.design.revealOnFocus
import com.lck07.lumen.player.VideoSurface
import com.lck07.lumen.ui.LocalScreenActive
import com.lck07.lumen.ui.LocalToaster
import com.lck07.lumen.ui.TabChrome
import com.lck07.lumen.ui.browse.PAGE
import com.lck07.lumen.ui.browse.visibleCategories
import com.lck07.lumen.ui.home.channelItems
import com.lck07.lumen.ui.home.hm
import com.lck07.lumen.ui.model.ChannelItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Long-press on a channel: add it to or remove it from the favourites, with a confirmation. */
@Composable
fun rememberFavouriteToggle(): (Int) -> Unit {
    val context = LocalContext.current
    val c = context.container
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    return remember {
        { id ->
            scope.launch {
                val on = c.library.toggleFavourite(FavouriteKind.Channel, id.toString())
                toaster.show(context.getString(if (on) R.string.added_to_favourites else R.string.removed_from_favourites))
            }
        }
    }
}

/** Channel tiles for a query, with what's on now from the guide. */
@OptIn(ExperimentalCoroutinesApi::class)
fun channelTiles(c: AppContainer, channels: Flow<List<ChannelEntity>>): Flow<List<ChannelItem>> =
    combine(channels, c.guide.onNow, c.library.favourites(FavouriteKind.Channel)) { ch, now, fav -> channelItems(ch, now, fav.toSet()) }

/** The active profile's favourite channels as tiles, in the order they were added. */
@OptIn(ExperimentalCoroutinesApi::class)
fun favouriteChannelTiles(c: AppContainer): Flow<List<ChannelItem>> =
    c.library.favourites(FavouriteKind.Channel).flatMapLatest { ids ->
        val intIds = ids.mapNotNull { it.toIntOrNull() }
        if (intIds.isEmpty()) flowOf(emptyList())
        else combine(channelTiles(c, c.db.catalog().channelsById(intIds)), flowOf(intIds)) { items, order -> items.sortedBy { order.indexOf(it.id) } }
    }

/**
 * Live TV: a hero with the last channel (a quiet live preview plays behind it) and Watch Live
 * and Guide buttons; then Favourites and a row per category.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalCoroutinesApi::class)
@Composable
fun LiveTab(chrome: TabChrome, onWatch: (Int) -> Unit, onGuide: () -> Unit, list: LazyListState = rememberLazyListState()) {
    val c = LocalContext.current.container
    val dao = c.db.catalog()
    val toggleFavourite = rememberFavouriteToggle()
    val count by remember { dao.channelCount() }.collectAsState(initial = -1)
    val categories by remember { visibleCategories(c, CatalogRepository.TYPE_LIVE) }.collectAsState(initial = null)
    val settings by c.settings.settings.collectAsState(initial = null)
    val favourites by remember { favouriteChannelTiles(c) }.collectAsState(initial = emptyList())
    val favouriteIds by remember { c.library.favourites(FavouriteKind.Channel) }.collectAsState(initial = emptyList())

    if (count == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(stringResource(R.string.tab_live), stringResource(R.string.empty_library), icon = LumenIcons.Warning)
        }
        return
    }
    val s = settings ?: return
    val cats = categories ?: return

    // The hero shows the last channel watched, else the first favourite, else the first channel.
    val heroChannel by produceState<ChannelEntity?>(null, s.lastChannelId, favourites.firstOrNull()?.id) {
        value = s.lastChannelId?.let { dao.channel(it) } ?: favourites.firstOrNull()?.let { dao.channel(it.id) } ?: dao.firstChannel()
    }

    val atTop by remember { derivedStateOf { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < 60 } }
    LaunchedEffect(atTop) { chrome.contentAtTop = atTop }
    LaunchedEffect(chrome.scrollToTop) { if (chrome.scrollToTop > 0) list.animateScrollToItem(0) }

    CompositionLocalProvider(LocalBringIntoViewSpec provides NoBringIntoView) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().testTag("live"),
            verticalArrangement = RowSpacing,
            contentPadding = PaddingValues(bottom = 70.dp),
        ) {
            item(key = "hero") {
                val ch = heroChannel
                if (ch != null) LiveHero(
                    ch, previewing = atTop, favourite = favouriteIds.contains(ch.id.toString()),
                    onWatch = { onWatch(ch.id) }, onGuide = onGuide, onFavourite = { toggleFavourite(ch.id) }, modifier = Modifier.revealOnFocus(list, 0, 0.dp))
                else Box(Modifier.fillMaxWidth().height(110.dp))
            }
            if (favourites.isNotEmpty()) item(key = "fav") {
                TvRow(stringResource(R.string.row_favourites), favourites, { it.id }, Modifier.revealOnFocus(list, 1), tag = "row:livefav") { item, slot ->
                    ChannelTile(item, { onWatch(item.id) }, slot = slot, onLongClick = { toggleFavourite(item.id) })
                }
            }
            cats.forEachIndexed { i, cat ->
                item(key = "cat:${cat.id}") {
                    var limit by rememberSaveable { mutableIntStateOf(PAGE) }
                    val tiles by remember(limit) { channelTiles(c, dao.channelsIn(cat.id, limit)) }.collectAsState(initial = null)
                    val shown = tiles ?: return@item
                    if (shown.isNotEmpty()) TvRow(
                        cat.name, shown, { it.id }, Modifier.revealOnFocus(list, i + 2), tag = "row:live:${cat.id}",
                        onNearEnd = { if (shown.size >= limit) limit += PAGE },
                    ) { item, slot ->
                        ChannelTile(item, { onWatch(item.id) }, slot = slot, onLongClick = { toggleFavourite(item.id) })
                    }
                }
            }
        }
    }
}

/** Rows start this far above the bottom of the hero, as on Home. */
private val HeroOverlap = 115.dp

@Composable
private fun LiveHero(
    channel: ChannelEntity, previewing: Boolean, favourite: Boolean,
    onWatch: () -> Unit, onGuide: () -> Unit, onFavourite: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = LocalContext.current.container
    val active = LocalScreenActive.current
    val nn by produceState(NowNext(null, null), channel.id) {
        while (true) { value = nowNext(c, channel); delay(60_000) }
    }
    val watch = remember { androidx.compose.ui.focus.FocusRequester() }

    // A muted live preview after a short pause, only while the hero is on screen. Accounts
    // allow one stream at a time, so it stops whenever anything else plays.
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val previewKey = "live:${channel.id}"
    LaunchedEffect(channel.id, previewing && active) {
        if (!(previewing && active)) {
            if (c.playback.currentKey == previewKey) c.playback.stop()
            player = null
            return@LaunchedEffect
        }
        delay(1_200)
        val client = c.account.client ?: return@LaunchedEffect
        val format = c.settings.current().liveFormat
        player = c.playback.play(previewKey, client.liveUrl(channel.toModel(), format.extension), live = true, volume = 0f)
    }
    DisposableEffect(channel.id) { onDispose { if (c.playback.currentKey == previewKey) c.playback.stop() } }

    Box(modifier.overlapBelow(HeroOverlap).fillMaxWidth().height(LumenSpacing.ScreenHeight)) {
        Box(Modifier.fillMaxSize().clipToBounds().generatedArt(channel.name, channel.number)) {
            if (player != null) VideoSurface(player, Modifier.graphicsLayer { alpha = 0.9f })
        }
        Box(Modifier.fillMaxSize().heroScrim())
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = LumenSpacing.SafeX, bottom = 150.dp).width(560.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Eyebrow("${channel.number} · ${channel.name}")
            Txt(nn.now?.title ?: channel.name, LumenType.HeroSmall, maxLines = 2)
            val now = nn.now
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("● " + stringResource(R.string.badge_live), LumenType.Caption1.copy(fontWeight = FontWeight.Bold), color = LumenColors.Live)
                if (now != null) Txt("${hm(now.start)}–${hm(now.stop)}", LumenType.Caption1.tabular(), color = LumenColors.Label2)
                nn.next?.let { Txt("· " + stringResource(R.string.next_programme, it.title, hm(it.start)), LumenType.Caption1.tabular(), color = LumenColors.Label2) }
            }
            if (now != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                val t = System.currentTimeMillis()
                ProgressBar(now.progress(t), Modifier.width(110.dp), color = LumenColors.Live)
                Txt(stringResource(R.string.minutes_left, (((now.stop - t) + 59_999) / 60_000).toInt().coerceAtLeast(1)), LumenType.Caption1.tabular(), color = LumenColors.Label2)
            }
            ButtonRow(Modifier.padding(top = 7.dp)) {
                LumenButton(stringResource(R.string.action_watch_live), onWatch, icon = LumenIcons.Play, focusRequester = watch, modifier = Modifier.testTag("live:watch"))
                LumenButton(stringResource(R.string.action_guide), onGuide, icon = LumenIcons.Guide)
                LumenButton(
                    null, onFavourite, icon = if (favourite) LumenIcons.HeartFilled else LumenIcons.Heart,
                    label = stringResource(if (favourite) R.string.favourite_on else R.string.favourite_off), selected = favourite,
                    modifier = Modifier.testTag("live:favourite"),
                )
            }
        }
    }
}

/** The hero is drawn a full screen tall but takes less space, so the first row overlaps it. */
private fun Modifier.overlapBelow(overlap: androidx.compose.ui.unit.Dp) = this.then(
    Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        layout(p.width, (p.height - overlap.roundToPx()).coerceAtLeast(0)) { p.place(0, 0) }
    },
)
