package com.lck07.lumen.ui.live

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lck07.lumen.R
import com.lck07.lumen.container
import com.lck07.lumen.data.CatalogRepository
import com.lck07.lumen.data.db.ChannelEntity
import com.lck07.lumen.data.toModel
import com.lck07.lumen.design.ChannelLogo
import com.lck07.lumen.design.ChannelTile
import com.lck07.lumen.design.Glass
import com.lck07.lumen.design.LocalReduceMotion
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.LumenMotion
import com.lck07.lumen.design.LumenRadii
import com.lck07.lumen.design.LumenSpacing
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.LumenType.tabular
import com.lck07.lumen.design.MessageState
import com.lck07.lumen.design.ProgressBar
import com.lck07.lumen.design.RowSlot
import com.lck07.lumen.design.SegmentedControl
import com.lck07.lumen.design.Spinner
import com.lck07.lumen.design.Txt
import com.lck07.lumen.player.PlayPauseFlash
import com.lck07.lumen.player.VideoSurface
import com.lck07.lumen.player.rememberPlayerState
import com.lck07.lumen.ui.browse.visibleCategories
import com.lck07.lumen.ui.home.channelItems
import com.lck07.lumen.ui.home.hm
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay

/**
 * Full-screen live TV. Up/down (and Channel keys) change channel and show the info banner at
 * once while the stream loads; Select or left/right opens the channel strip; up or Back closes it.
 */
@Composable
fun LivePlayerScreen(channelId: Int, onGuide: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val res = context.resources
    val reduce = LocalReduceMotion.current
    val dao = c.db.catalog()
    val toggleFavourite = rememberFavouriteToggle()

    var channel by remember { mutableStateOf<ChannelEntity?>(null) }
    var group by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var bannerAt by remember { mutableLongStateOf(0L) }
    var strip by remember { mutableStateOf(false) }
    var flashSerial by remember { mutableIntStateOf(0) }
    var flashIcon by remember { mutableStateOf(LumenIcons.Pause) }
    var retries by remember { mutableIntStateOf(0) }

    // The first channel, and the list to zap through: its category.
    LaunchedEffect(channelId) {
        val ch = dao.channel(channelId) ?: return@LaunchedEffect
        channel = ch
        group = ch.categoryId ?: FAVOURITES_GROUP
    }
    LaunchedEffect(group) {
        val g = group ?: return@LaunchedEffect
        channels = channelsOf(c, g).ifEmpty { dao.channelsNow() }
    }

    // Tuning: the banner shows at once; the stream catches up behind it.
    LaunchedEffect(channel?.id) {
        val ch = channel ?: return@LaunchedEffect
        bannerAt = System.currentTimeMillis()
        retries = 0
        c.settings.setLastChannel(ch.id)
        val client = c.account.client ?: return@LaunchedEffect
        val format = c.settings.current().liveFormat
        player = c.playback.play("live:${ch.id}", client.liveUrl(ch.toModel(), format.extension), live = true)
    }
    DisposableEffect(Unit) { onDispose { c.playback.stop() } }

    val ps = rememberPlayerState(player, ticking = false)
    // Live streams drop now and then: reconnect a few times before showing an error.
    LaunchedEffect(ps.error) {
        val p = player ?: return@LaunchedEffect
        if (ps.error != null && retries < 3) {
            delay(1_500L * (retries + 1))
            retries++
            ps.error = null
            p.prepare(); p.playWhenReady = true
        }
    }

    fun zap(delta: Int) {
        val list = channels.ifEmpty { return }
        val i = list.indexOfFirst { it.id == channel?.id }.let { if (it < 0) 0 else it }
        channel = list[(i + delta + list.size) % list.size]
    }
    fun tune(id: Int) { if (id != channel?.id) channels.firstOrNull { it.id == id }?.let { channel = it } }

    var bannerVisible by remember { mutableStateOf(true) }
    LaunchedEffect(bannerAt, strip) {
        bannerVisible = true
        if (!strip) { delay(LumenMotion.OverlayTimeoutMs); bannerVisible = false }
    }

    BackHandler(enabled = strip) { strip = false }

    val root = remember { FocusRequester() }
    val stripFocus = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("live-player")
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_CHANNEL_UP, AndroidKeyEvent.KEYCODE_PAGE_UP -> { zap(1); true }
                    AndroidKeyEvent.KEYCODE_CHANNEL_DOWN, AndroidKeyEvent.KEYCODE_PAGE_DOWN -> { zap(-1); true }
                    AndroidKeyEvent.KEYCODE_GUIDE, AndroidKeyEvent.KEYCODE_TV_INPUT -> { onGuide(); true }
                    AndroidKeyEvent.KEYCODE_INFO -> { bannerAt = System.currentTimeMillis(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_MEDIA_PLAY, AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        player?.let { p ->
                            p.playWhenReady = !p.playWhenReady
                            flashIcon = if (p.playWhenReady) LumenIcons.Play else LumenIcons.Pause; flashSerial++
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> if (strip) { strip = false; true } else { zap(-1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> if (strip) false else { zap(1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (!strip) { strip = true; true } else false
                    else -> false
                }
            }
            .focusRequester(root)
            .focusable(),
    ) {
        VideoSurface(player)
        val ch = channel
        val loading = ps.state == Player.STATE_BUFFERING || (ps.state == Player.STATE_IDLE && ps.error == null)
        if (loading && ch != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(size = 34.dp) }
        if (ps.error != null && retries >= 3) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            MessageState(stringResource(R.string.playback_error_title), stringResource(R.string.playback_error), icon = LumenIcons.Warning)
        }
        PlayPauseFlash(flashIcon, flashSerial)

        if (ch != null) {
            val shown by animateFloatAsState(if (bannerVisible && !strip) 1f else 0f, tween(if (reduce) 0 else 300), label = "banner")
            if (shown > 0.01f) InfoBanner(ch, Modifier.align(Alignment.BottomStart).graphicsLayer {
                alpha = shown; if (!reduce) { translationY = (1f - shown) * 15.dp.toPx(); val sc = 0.98f + 0.02f * shown; scaleX = sc; scaleY = sc }
            })
            val stripShown by animateFloatAsState(if (strip) 1f else 0f, tween(if (reduce) 0 else 350), label = "strip")
            if (stripShown > 0.01f) ChannelStrip(
                current = ch, group = group, channels = channels, focus = stripFocus, visible = strip,
                onTune = ::tune, onFavourite = toggleFavourite, onGroup = { group = it },
                modifier = Modifier.align(Alignment.BottomStart).graphicsLayer { alpha = stripShown; if (!reduce) translationY = (1f - stripShown) * 120.dp.toPx() },
            )
        }
    }
    LaunchedEffect(strip) {
        awaitFrame()
        runCatching { if (strip) stripFocus.requestFocus() else root.requestFocus() }
    }
}

/** Number, logo, what's on now (with progress) and next. */
@Composable
private fun InfoBanner(channel: ChannelEntity, modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    val nn by produceState(NowNext(null, null), channel.id) { value = nowNext(c, channel) }
    val now = System.currentTimeMillis()
    val item = channelItems(listOf(channel), emptyMap(), emptySet()).first()
    Glass(LumenRadii.Banner, modifier.padding(start = LumenSpacing.SafeX, bottom = LumenSpacing.SafeY).width(440.dp).semantics { liveRegion = LiveRegionMode.Polite }, strong = true) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("${channel.number}", LumenType.Title2.tabular(), Modifier.width(45.dp))
            ChannelLogo(item, Modifier.size(75.dp, 42.dp), height = 42.dp)
            Column(Modifier.weight(1f)) {
                Txt(nn.now?.title ?: channel.name, LumenType.Callout)
                val line = listOfNotNull(
                    nn.now?.let { stringResource(R.string.now) + " " + hm(it.start) + "–" + hm(it.stop) },
                    nn.next?.let { stringResource(R.string.next) + ": " + it.title },
                ).joinToString(" · ").ifEmpty { channel.name }
                Txt(line, LumenType.Caption2.tabular(), color = LumenColors.Label2)
                ProgressBar(nn.now?.progress(now) ?: 0f, Modifier.padding(top = 6.dp).fillMaxWidth(), color = LumenColors.Live, height = 2.5.dp)
            }
        }
    }
}

/**
 * The channel strip over the darkened bottom of the picture: the channels of a group, the one
 * playing marked; below them the groups (Favourites and the guide's categories).
 */
@Composable
private fun ChannelStrip(
    current: ChannelEntity,
    group: String?,
    channels: List<ChannelEntity>,
    focus: FocusRequester,
    visible: Boolean,
    onTune: (Int) -> Unit,
    onFavourite: (Int) -> Unit,
    onGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalContext.current.container
    val categories by remember { visibleCategories(c, CatalogRepository.TYPE_LIVE) }.collectAsState(initial = emptyList())
    val onNow by c.guide.onNow.collectAsState(initial = emptyMap())
    val favs by c.library.favourites(com.lck07.lumen.data.FavouriteKind.Channel).collectAsState(initial = emptyList())
    val items = remember(channels, onNow, favs) { channelItems(channels, onNow, favs.toSet()) }
    val groups = listOf(FAVOURITES_GROUP to stringResource(R.string.row_favourites)) + categories.map { it.id to it.name }
    val title = groups.firstOrNull { it.first == group }?.second ?: stringResource(R.string.row_all_channels)
    val listState = rememberLazyListState()
    val playingIndex = items.indexOfFirst { it.id == current.id }
    LaunchedEffect(channels) { if (playingIndex >= 0) listState.scrollToItem((playingIndex - 1).coerceAtLeast(0)) }

    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color.Black.copy(alpha = 0.9f)))
            .padding(top = 20.dp, bottom = LumenSpacing.SafeY)
            .focusProperties { canFocus = visible },
    ) {
        Txt(title, LumenType.Caption1Strong, Modifier.padding(start = LumenSpacing.SafeX, bottom = 3.dp), color = LumenColors.Label2)
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().focusRestorer(focus),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = LumenSpacing.SafeX, end = LumenSpacing.SafeX, top = 9.dp, bottom = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(LumenSpacing.Gutter),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                ChannelTile(
                    item, { onTune(item.id) }, slot = RowSlot(i, items.size), playing = item.id == current.id,
                    onLongClick = { onFavourite(item.id) },
                    focusRequester = if (i == playingIndex.coerceAtLeast(0)) focus else null,
                )
            }
        }
        if (groups.size > 1) SegmentedControl(
            groups.map { it.first }, group ?: "", { k -> groups.first { it.first == k }.second }, onGroup,
            Modifier.padding(start = LumenSpacing.SafeX).height(34.dp),
        )
    }
}
