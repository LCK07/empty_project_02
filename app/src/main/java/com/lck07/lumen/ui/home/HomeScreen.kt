package com.lck07.lumen.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lck07.lumen.R
import com.lck07.lumen.design.Artwork
import com.lck07.lumen.design.Backdrop
import com.lck07.lumen.design.ButtonRow
import com.lck07.lumen.design.ChannelTile
import com.lck07.lumen.design.EpisodeTile
import com.lck07.lumen.design.Eyebrow
import com.lck07.lumen.design.LocalBackdrop
import com.lck07.lumen.design.LocalReduceMotion
import com.lck07.lumen.design.LumenButton
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.LumenSpacing
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.LumenType.tabular
import com.lck07.lumen.design.MetaLine
import com.lck07.lumen.design.NoBringIntoView
import com.lck07.lumen.design.PosterTile
import com.lck07.lumen.design.ProgressBar
import com.lck07.lumen.design.RowSpacing
import com.lck07.lumen.design.TvRow
import com.lck07.lumen.design.Txt
import com.lck07.lumen.design.backdropSource
import com.lck07.lumen.design.heroScrim
import com.lck07.lumen.design.revealOnFocus
import com.lck07.lumen.ui.LocalScreenActive
import com.lck07.lumen.ui.model.ChannelItem
import com.lck07.lumen.ui.model.EpisodeTileItem
import com.lck07.lumen.ui.model.PosterItem
import com.lck07.lumen.ui.model.Target

/** What the Home hero offers. */
@Immutable
data class HomeHero(
    val eyebrow: String,
    val title: String,
    val backdrop: String?,
    /** "Season 2, Episode 5: Tide Line". */
    val episodeLine: String?,
    val genre: String?,
    val rating: String?,
    val progress: Float?,
    val minutesLeft: Int?,
    /** Resume when a half-watched episode waits, otherwise Play (next episode, or a featured series). */
    val resumes: Boolean,
    val play: Target,
    val startOver: Target?,
    val episodes: Target?,
)

@Immutable
data class HomeState(
    val hero: HomeHero? = null,
    val continueWatching: List<EpisodeTileItem> = emptyList(),
    val upNextTitle: String? = null,
    val upNext: List<EpisodeTileItem> = emptyList(),
    val favouriteChannels: List<ChannelItem> = emptyList(),
    val newEpisodes: List<PosterItem> = emptyList(),
    val recentMovies: List<PosterItem> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * Home: a full-bleed hero for the series watched last (Resume has focus, one press plays),
 * then rows. The page slides up as focus moves into the rows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onOpen: (Target) -> Unit,
    onToggleFavouriteChannel: (ChannelItem) -> Unit,
    onAtTop: (Boolean) -> Unit,
    resumeRequester: FocusRequester,
    contentRequester: FocusRequester,
    scrollToTop: Int,
    modifier: Modifier = Modifier,
    list: LazyListState = rememberLazyListState(),
) {
    val atTop by remember { derivedStateOf { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < 60 } }
    LaunchedEffect(atTop) { onAtTop(atTop) }
    LaunchedEffect(scrollToTop) { if (scrollToTop > 0) list.animateScrollToItem(0) }
    val backdrop = remember { Backdrop() }

    CompositionLocalProvider(LocalBringIntoViewSpec provides NoBringIntoView, LocalBackdrop provides backdrop) {
        LazyColumn(
            state = list,
            modifier = modifier.fillMaxSize().testTag("home"),
            verticalArrangement = RowSpacing,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 70.dp),
        ) {
            val hero = state.hero
            item(key = "hero") {
                if (hero != null) Hero(hero, onOpen, resumeRequester, backdrop, Modifier.overlapBelow(HeroOverlap).revealOnFocus(list, 0, 0.dp))
                else Box(Modifier.fillMaxWidth().height(110.dp))
            }
            var index = 1
            fun rowIndex() = index++
            if (state.continueWatching.isNotEmpty()) {
                val i = rowIndex()
                item(key = "cw") {
                    TvRow(
                        stringResource(R.string.row_continue_watching), state.continueWatching, { it.key },
                        Modifier.revealOnFocus(list, i), tag = "row:cw",
                    ) { item, slot -> EpisodeTile(item, { onOpen(item.target) }, slot = slot) }
                }
            }
            if (state.upNext.isNotEmpty() && state.upNextTitle != null) {
                val i = rowIndex()
                item(key = "upnext") {
                    TvRow(
                        stringResource(R.string.row_up_next, state.upNextTitle), state.upNext, { it.key },
                        Modifier.revealOnFocus(list, i), tag = "row:upnext",
                    ) { item, slot -> EpisodeTile(item, { onOpen(item.target) }, slot = slot) }
                }
            }
            if (state.favouriteChannels.isNotEmpty()) {
                val i = rowIndex()
                item(key = "fav") {
                    TvRow(
                        stringResource(R.string.row_favourite_channels), state.favouriteChannels, { it.id },
                        Modifier.revealOnFocus(list, i), tag = "row:fav",
                    ) { item, slot ->
                        ChannelTile(item, { onOpen(Target.PlayChannel(item.id)) }, slot = slot, onLongClick = { onToggleFavouriteChannel(item) })
                    }
                }
            }
            if (state.newEpisodes.isNotEmpty()) {
                val i = rowIndex()
                item(key = "new") {
                    TvRow(
                        stringResource(R.string.row_new_episodes), state.newEpisodes, { it.key },
                        Modifier.revealOnFocus(list, i), tag = "row:new",
                    ) { item, slot -> PosterTile(item, { onOpen(item.target) }, slot = slot) }
                }
            }
            if (state.recentMovies.isNotEmpty()) {
                val i = rowIndex()
                item(key = "movies") {
                    TvRow(
                        stringResource(R.string.row_recently_added_movies), state.recentMovies, { it.key },
                        Modifier.revealOnFocus(list, i), tag = "row:movies",
                    ) { item, slot -> PosterTile(item, { onOpen(item.target) }, slot = slot) }
                }
            }
        }
    }
}

/** Rows start 230 pt above the bottom of the hero, overlapping its darkened lower part. */
private val HeroOverlap = 115.dp

/** The hero is drawn a full screen tall but takes less space, so the first row overlaps it. */
private fun Modifier.overlapBelow(overlap: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val h = (p.height - overlap.roundToPx()).coerceAtLeast(0)
    layout(p.width, h) { p.place(0, 0) }
}

@Composable
private fun Hero(hero: HomeHero, onOpen: (Target) -> Unit, resumeRequester: FocusRequester, backdrop: Backdrop, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(LumenSpacing.ScreenHeight)) {
        DriftingArtwork(hero.backdrop, hero.title, backdrop)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = LumenSpacing.SafeX, bottom = 150.dp)
                .width(500.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Eyebrow(hero.eyebrow)
            Txt(hero.title, LumenType.Hero, maxLines = 2)
            MetaLine(listOfNotNull(hero.episodeLine, hero.genre), pill = hero.rating)
            if (hero.progress != null && hero.minutesLeft != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ProgressBar(hero.progress, Modifier.width(110.dp))
                    Txt(stringResource(R.string.minutes_left, hero.minutesLeft), LumenType.Caption1.tabular(), color = LumenColors.Label2)
                }
            }
            ButtonRow(Modifier.padding(top = 7.dp)) {
                LumenButton(
                    stringResource(if (hero.resumes) R.string.action_resume else R.string.action_play),
                    { onOpen(hero.play) }, icon = LumenIcons.Play, focusRequester = resumeRequester,
                    modifier = Modifier.testTag("hero:play"),
                )
                if (hero.startOver != null) LumenButton(stringResource(R.string.action_start_over), { onOpen(hero.startOver) }, icon = LumenIcons.Restart)
                if (hero.episodes != null) LumenButton(stringResource(R.string.action_episodes), { onOpen(hero.episodes) }, icon = LumenIcons.List)
            }
        }
    }
}

/** Full-bleed artwork that drifts slowly (still when animations are off or the screen is hidden). */
@Composable
fun DriftingArtwork(url: String?, seed: String, backdrop: Backdrop? = null, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val active = LocalScreenActive.current
    val drift = if (reduce || !active) null else rememberInfiniteTransition(label = "drift").animateFloat(
        0f, 1f, infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift",
    )
    val px = with(LocalDensity.current) { 10.dp.toPx() }
    Box(modifier.fillMaxSize().clipToBounds().heroScrim()) {
        Artwork(
            url, seed,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = drift?.value ?: 0f
                    val s = 1f + 0.07f * smooth(t)
                    scaleX = s; scaleY = s
                    translationX = -px * smooth(t)
                    transformOrigin = TransformOrigin(0.7f, 0.4f)
                }
                .backdropSource(backdrop, null),
            variant = 1,
            onPainter = { p -> backdrop?.painter = p },
        )
    }
}

private fun smooth(t: Float) = t * t * (3 - 2 * t)
