package com.lck07.lumen.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lck07.lumen.R
import com.lck07.lumen.design.LumenType.tabular
import com.lck07.lumen.ui.model.ChannelItem
import com.lck07.lumen.ui.model.EpisodeTileItem
import com.lck07.lumen.ui.model.PosterItem

/** Position of a tile in its row: rows give a small bump at their ends instead of wrapping. */
data class RowSlot(val index: Int, val count: Int) {
    val isFirst get() = index == 0
    val isLast get() = index == count - 1
}

fun edgeBump(state: TvState, slot: RowSlot?): ((Key) -> Boolean)? = slot?.let {
    { k ->
        when {
            k == Key.DirectionLeft && it.isFirst -> { state.nudge(Travel.Left); true }
            k == Key.DirectionRight && it.isLast -> { state.nudge(Travel.Right); true }
            else -> false
        }
    }
}

/**
 * A 2:3 poster. The title under it appears only while it has focus (tvOS posters); when the
 * artwork is missing the title is drawn on the generated art instead.
 */
@Composable
fun PosterTile(
    item: PosterItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    slot: RowSlot? = null,
    onLongClick: (() -> Unit)? = null,
    width: Dp = LumenSizes.PosterW,
    height: Dp = LumenSizes.PosterH,
    focusRequester: FocusRequester? = null,
    state: TvState = rememberTvState(),
) {
    val captionAlpha by animateFloatAsState(if (state.focused) 1f else 0f, tween(250), label = "caption")
    Box(modifier.zIndex(if (state.focused) 1f else 0f)) {
        Box(
            Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .size(width, height)
                .tileFocusEffect(state)
                .background(LumenColors.TileBackground)
                .tvClickable(state, label = item.title, onLongClick = onLongClick, onKey = edgeBump(state, slot), onClick = onClick)
                .testTag("poster:${item.key}"),
        ) {
            Artwork(item.image, item.title, Modifier.fillMaxSize(), variant = item.key.length) { loaded ->
                if (!loaded) Txt(
                    item.title, LumenType.PosterTitle, maxLines = 3,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 11.dp),
                )
            }
            if (item.isNew) NewBadge(stringResource(R.string.badge_new), Modifier.align(Alignment.TopEnd).padding(7.dp))
        }
        Caption(item.caption, captionAlpha, height, width)
    }
}

/** A 16:9 tile for an episode or a film in progress, with a caption, progress and a watched tick. */
@Composable
fun EpisodeTile(
    item: EpisodeTileItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    slot: RowSlot? = null,
    small: Boolean = false,
    focusRequester: FocusRequester? = null,
    state: TvState = rememberTvState(),
) {
    val w = if (small) LumenSizes.EpisodeSmallW else LumenSizes.EpisodeW
    val h = if (small) LumenSizes.EpisodeSmallH else LumenSizes.EpisodeH
    val watchedLabel = stringResource(R.string.watched)
    Box(
        modifier
            .zIndex(if (state.focused) 1f else 0f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .size(w, h)
            .tileFocusEffect(state)
            .background(LumenColors.TileBackground)
            .tvClickable(state, onKey = edgeBump(state, slot), onClick = onClick)
            .semantics(mergeDescendants = true) { if (item.watched) stateDescription = watchedLabel }
            .testTag("episode:${item.key}"),
    ) {
        Artwork(item.image, item.seed, Modifier.fillMaxSize(), variant = item.key.length)
        if (item.watched) Box(
            Modifier.align(Alignment.TopEnd).padding(7.dp).size(17.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) { Icon(LumenIcons.Check, size = 10.dp) }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(start = 12.dp, end = 12.dp, top = 30.dp, bottom = 10.dp),
        ) {
            Txt(item.title, LumenType.Caption1Strong)
            Txt(item.subtitle, LumenType.Caption2.tabular(), color = LumenColors.Label2)
            if (item.progress != null && item.progress > 0f) ProgressBar(item.progress, Modifier.padding(top = 6.dp).fillMaxWidth())
        }
    }
}

/** A live channel: logo, red LIVE badge, what's on now and how far into it. */
@Composable
fun ChannelTile(
    item: ChannelItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    slot: RowSlot? = null,
    playing: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    state: TvState = rememberTvState(),
) {
    val captionAlpha by animateFloatAsState(if (state.focused) 1f else 0f, tween(250), label = "caption")
    val live = stringResource(R.string.badge_live)
    val noInfo = stringResource(R.string.no_programme)
    Box(modifier.zIndex(if (state.focused) 1f else 0f)) {
        Box(
            Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .size(LumenSizes.ChannelW, LumenSizes.ChannelH)
                .tileFocusEffect(state)
                .background(LumenColors.TileBackground)
                .then(if (playing) Modifier.border(2.dp, Color.White, LumenRadii.Tile) else Modifier)
                .tvClickable(state, onLongClick = onLongClick, onKey = edgeBump(state, slot), onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = "${item.number} ${item.name}, $live, ${item.nowTitle ?: noInfo}" }
                .testTag("channel:${item.id}"),
        ) {
            Box(Modifier.fillMaxSize().generatedArt(item.name, item.number))
            ChannelLogo(item, Modifier.align(Alignment.TopStart).padding(9.dp))
            LiveBadge(live, Modifier.align(Alignment.TopEnd).padding(9.dp))
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 9.dp, end = 9.dp, bottom = 8.dp)) {
                Txt(item.nowTitle ?: item.name, LumenType.Caption2Strong)
                ProgressBar(item.nowProgress ?: 0f, Modifier.padding(top = 5.dp).fillMaxWidth(), color = LumenColors.Live, height = 2.5.dp)
            }
        }
        Caption(listOfNotNull("${item.number}", item.name, item.nowTime).joinToString(" · "), captionAlpha, LumenSizes.ChannelH, LumenSizes.ChannelW)
    }
}

/** The caption under a focused tile. It takes no space in the row, so tiles keep the grid gutter. */
@Composable
private fun Caption(text: String, alpha: Float, tileHeight: Dp, tileWidth: Dp) {
    Txt(
        text, LumenType.Caption2.tabular(), color = LumenColors.Label2,
        modifier = Modifier
            .layout { measurable, _ ->
                val p = measurable.measure(Constraints(maxWidth = (tileWidth * 1.4f).roundToPx()))
                layout(0, 0) { p.placeRelative(0, (tileHeight + 9.dp).roundToPx()) }
            }
            .graphicsLayer { this.alpha = alpha },
    )
}

/** A channel logo on a small dark chip; falls back to the channel's initials. */
@Composable
fun ChannelLogo(item: ChannelItem, modifier: Modifier = Modifier, height: Dp = 24.dp) {
    Box(
        modifier
            .heightIn(min = height)
            .clip(LumenRadii.Logo)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!item.logo.isNullOrBlank()) {
            coil3.compose.AsyncImage(
                model = item.logo, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.sizeIn(maxWidth = height * 3, maxHeight = height - 8.dp).height(height - 8.dp),
            )
        } else {
            Txt(initials(item.name), LumenType.Logo)
        }
    }
}

fun initials(name: String): String {
    val words = name.split(' ', '-', '_').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "TV"
        words.size == 1 -> words[0].take(2).uppercase()
        words[1].first().isDigit() -> (words[0].take(1) + words[1].take(1)).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
