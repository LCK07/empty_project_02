package com.lck07.lumen.ui.guide

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.lck07.lumen.R
import com.lck07.lumen.container
import com.lck07.lumen.data.CatalogRepository
import com.lck07.lumen.data.Show
import com.lck07.lumen.data.db.ChannelEntity
import com.lck07.lumen.data.toModel
import com.lck07.lumen.design.ChannelLogo
import com.lck07.lumen.design.Eyebrow
import com.lck07.lumen.design.Glass
import com.lck07.lumen.design.ListRow
import com.lck07.lumen.design.LiveBadge
import com.lck07.lumen.design.LocalReduceMotion
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.LumenMotion
import com.lck07.lumen.design.LumenRadii
import com.lck07.lumen.design.LumenSpacing
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.LumenType.tabular
import com.lck07.lumen.design.MessageState
import com.lck07.lumen.design.Txt
import com.lck07.lumen.design.generatedArt
import com.lck07.lumen.design.rememberTvState
import com.lck07.lumen.design.tvClickable
import com.lck07.lumen.design.controlFocusEffect
import com.lck07.lumen.player.VideoSurface
import com.lck07.lumen.ui.LocalScreenActive
import com.lck07.lumen.ui.TabChrome
import com.lck07.lumen.ui.browse.visibleCategories
import com.lck07.lumen.ui.home.channelItems
import com.lck07.lumen.ui.home.hm
import com.lck07.lumen.ui.live.FAVOURITES_GROUP
import com.lck07.lumen.ui.live.channelsOf
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val MIN = 60_000L
private const val SLOT = 30 * MIN
/** The grid shows three hours at a time. */
private const val WINDOW = 6 * SLOT
private const val VISIBLE_ROWS = 6
private val RowHeight = 46.dp
private val CellHeight = 40.dp
private val GridLeft = 200.dp
private val GridRight = 920.dp
private val GridTop = 235.dp
private val HeaderHeight = 23.dp
private val FirstRowTop = 28.dp

/** Where focus is in the grid: a channel row and a moment in time. */
private data class Cursor(val row: Int, val time: Long)

/**
 * The programme guide: time across the top in 30-minute steps, channels down the side,
 * programme cells sized by duration, a red "now" line. The grid is one focusable with a cursor,
 * so paging through thousands of channels stays fast. Selecting a programme that is on now tunes
 * to it; page keys (and Channel keys) page through channels, rewind/fast-forward through time.
 */
@Composable
fun GuideScreen(chrome: TabChrome, onWatch: (Int) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val dao = c.db.catalog()
    val res = context.resources
    val reduce = LocalReduceMotion.current
    val guideState by c.guide.state.collectAsState(initial = null)
    val settings by c.settings.settings.collectAsState(initial = null)
    val categories by remember { visibleCategories(c, CatalogRepository.TYPE_LIVE) }.collectAsState(initial = null)
    val s = settings ?: return
    val cats = categories ?: return

    // The group shown: favourites or a category (the last channel's by default).
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (group == null) {
            val last = s.lastChannelId?.let { dao.channel(it) }
            val favs = channelsOf(c, FAVOURITES_GROUP)
            group = when {
                last != null && favs.any { it.id == last.id } -> FAVOURITES_GROUP
                last?.categoryId != null -> last.categoryId
                favs.isNotEmpty() -> FAVOURITES_GROUP
                else -> cats.firstOrNull()?.id
            }
        }
    }
    val g = group ?: return
    val channels by produceState<List<ChannelEntity>?>(null, g) { value = channelsOf(c, g) }
    val list = channels ?: return
    val groups = listOf(FAVOURITES_GROUP to stringResource(R.string.row_favourites)) + cats.map { it.id to it.name }
    val groupTitle = groups.firstOrNull { it.first == g }?.second ?: ""

    // Opens on "now" for the current channel.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    var cursor by remember(g) { mutableStateOf(Cursor(list.indexOfFirst { it.id == s.lastChannelId }.coerceAtLeast(0), now)) }
    var windowStart by remember(g) { mutableLongStateOf(initialWindow(now)) }
    var topRow by remember(g) { mutableIntStateOf((cursor.row - 1).coerceIn(0, (list.size - VISIBLE_ROWS).coerceAtLeast(0))) }
    var groupMenu by remember { mutableStateOf(false) }

    // Programmes for the rows on screen and a little around them.
    val from = windowStart - 2 * SLOT
    val to = windowStart + WINDOW + 2 * SLOT
    val rowsFrom = (topRow - VISIBLE_ROWS).coerceAtLeast(0)
    val rowsTo = (topRow + 2 * VISIBLE_ROWS).coerceAtMost(list.size)
    val shows by produceState<Map<String, List<Show>>>(emptyMap(), g, rowsFrom, rowsTo, from / SLOT, guideState?.activeGen) {
        val keys = list.subList(rowsFrom, rowsTo).mapNotNull { it.epgId }.distinct()
        value = c.guide.range(keys, from - SLOT, to + SLOT)
    }

    fun showsOf(row: Int): List<Show> = list.getOrNull(row)?.epgId?.let { shows[it] }.orEmpty()
    /** The programme at [time] on [row], or null when the guide has nothing then. */
    fun at(row: Int, time: Long): Show? = showsOf(row).firstOrNull { time >= it.start && time < it.stop }
    val focused = at(cursor.row, cursor.time)
    val focusedChannel = list.getOrNull(cursor.row)

    fun moveRow(to: Int) {
        val r = to.coerceIn(0, list.lastIndex)
        cursor = cursor.copy(row = r, time = cursor.time.coerceAtLeast(windowStart))
        if (r < topRow) topRow = r
        if (r >= topRow + VISIBLE_ROWS) topRow = r - VISIBLE_ROWS + 1
    }
    fun moveTime(forward: Boolean) {
        val minTime = floorSlot(now) - 4 * SLOT
        val cur = focused
        val target = if (forward) (cur?.stop ?: (floorSlot(cursor.time) + SLOT)) else ((cur?.start ?: floorSlot(cursor.time)) - 1)
        if (target < minTime) return
        // The next programme, or the half hour itself where the guide has a gap.
        val start = (at(cursor.row, target)?.start ?: floorSlot(target)).coerceAtLeast(minTime)
        cursor = cursor.copy(time = start)
        // Keep the programme's start on screen, with half an hour of context before it.
        if (start < windowStart) windowStart = (floorSlot(start)).coerceAtLeast(minTime)
        else if (start >= windowStart + WINDOW - SLOT) windowStart = floorSlot(start) - SLOT
    }

    val gridFocus = remember { FocusRequester() }
    val groupChip = remember { FocusRequester() }
    var gridFocused by remember { mutableStateOf(false) }
    LaunchedEffect(gridFocused) { if (gridFocused) chrome.contentAtTop = false }

    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(stringResource(R.string.tab_guide), stringResource(R.string.empty_favourites), icon = LumenIcons.Guide)
        }
        GroupChip(groupTitle, groupChip, onClick = { groupMenu = true }, modifier = Modifier.padding(start = LumenSpacing.SafeX, top = 210.dp))
        if (groupMenu) GroupMenu(groups, g, { group = it; groupMenu = false }, { groupMenu = false })
        return
    }

    Box(Modifier.fillMaxSize().testTag("guide")) {
        // Top: a small live preview of the current channel, and the focused programme.
        Row(
            Modifier.padding(start = LumenSpacing.SafeX, end = LumenSpacing.SafeX, top = 80.dp).height(135.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            GuidePreview(s.lastChannelId ?: list.first().id)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                if (focusedChannel != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("${focusedChannel.number} · ${focusedChannel.name}")
                    if (focused?.isOn(now) == true) Txt("● " + stringResource(R.string.badge_live), LumenType.Eyebrow, color = LumenColors.Live)
                }
                Txt(focused?.title ?: stringResource(R.string.no_programme), LumenType.Title3, maxLines = 1)
                if (focused != null) Txt(
                    "${dayLabel(res, focused.start)} · ${hm(focused.start)}–${hm(focused.stop)} · ${stringResource(R.string.minutes, focused.minutes)}",
                    LumenType.Caption1.tabular(), color = LumenColors.Label2,
                )
                focused?.description?.let { Txt(it, LumenType.Caption1.copy(fontWeight = FontWeight.Normal), color = LumenColors.Label2, maxLines = 3) }
            }
        }

        // The grid.
        val minuteDp = (GridRight - GridLeft) / (WINDOW / MIN).toFloat()
        val base = remember(g) { floorSlot(now) - 12 * SLOT }
        val animStart by animateFloatAsState(((windowStart - base) / MIN).toFloat(), if (reduce) tween(0) else tween(320), label = "time")
        val animTop by animateFloatAsState(topRow.toFloat(), if (reduce) tween(0) else tween(260), label = "rows")
        fun x(time: Long): Dp = GridLeft + minuteDp * (((time - base) / MIN).toFloat() - animStart)

        Box(
            Modifier
                .padding(top = GridTop)
                .fillMaxSize()
                .clipToBounds()
                .focusRequester(gridFocus)
                .onFocusChanged { gridFocused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key.nativeKeyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { moveTime(true); true }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { moveTime(false); true }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { moveRow(cursor.row + 1); true }
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> if (cursor.row == 0) { runCatching { groupChip.requestFocus() }; true } else { moveRow(cursor.row - 1); true }
                        AndroidKeyEvent.KEYCODE_PAGE_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { moveRow(cursor.row + VISIBLE_ROWS); true }
                        AndroidKeyEvent.KEYCODE_PAGE_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { moveRow(cursor.row - VISIBLE_ROWS); true }
                        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { cursor = cursor.copy(time = cursor.time + WINDOW); windowStart += WINDOW; true }
                        AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                            val min = floorSlot(now) - 4 * SLOT
                            windowStart = (windowStart - WINDOW).coerceAtLeast(min); cursor = cursor.copy(time = (cursor.time - WINDOW).coerceAtLeast(min)); true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                            // Tune when the programme is on now (or the guide knows nothing about it).
                            val ch = list.getOrNull(cursor.row)
                            if (ch != null && (focused == null || focused.isOn(now))) onWatch(ch.id)
                            true
                        }
                        else -> false
                    }
                }
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = listOfNotNull(focusedChannel?.let { "${it.number} ${it.name}" }, focused?.let { "${it.title}, ${hm(it.start)}–${hm(it.stop)}" }).joinToString(", ")
                }
                .focusable(),
        ) {
            // Time across the top.
            Box(Modifier.offset(x = GridLeft).width(GridRight - GridLeft + 40.dp).height(HeaderHeight).clipToBounds()) {
                var t = floorSlot(windowStart) - SLOT
                while (t < windowStart + WINDOW + SLOT) {
                    if (kotlin.math.abs(t - now) > 12 * MIN) Txt(
                        hm(t), LumenType.Caption2Strong.tabular(), color = LumenColors.Label2,
                        modifier = Modifier.offset(x = x(t) - GridLeft, y = 5.dp),
                    )
                    t += SLOT
                }
            }
            GroupChip(groupTitle, groupChip, onClick = { groupMenu = true }, onDown = { runCatching { gridFocus.requestFocus() } }, modifier = Modifier.padding(start = LumenSpacing.SafeX))

            // Rows: channel on the left, programmes to the right.
            val first = animTop.toInt().coerceAtLeast(0)
            for (row in (first - 1).coerceAtLeast(0) until (first + VISIBLE_ROWS + 1).coerceAtMost(list.size)) {
                val ch = list[row]
                val y = FirstRowTop + RowHeight * (row - animTop)
                key(ch.id) {
                    Box(Modifier.offset(y = y).fillMaxWidth().height(RowHeight)) {
                        ChannelHeader(ch, Modifier.padding(start = LumenSpacing.SafeX).width(150.dp).height(CellHeight))
                        Box(Modifier.offset(x = GridLeft).width(GridRight - GridLeft + 40.dp).height(CellHeight).clipToBounds()) {
                            val rowShows = showsOf(row).filter { it.stop > windowStart - SLOT && it.start < windowStart + WINDOW + SLOT }
                            if (rowShows.isEmpty()) {
                                GuideCell(
                                    stringResource(R.string.no_programme), null, isNow = true,
                                    focused = gridFocused && cursor.row == row, start = 0.dp, width = GridRight - GridLeft - 4.dp,
                                )
                            } else rowShows.forEach { sh ->
                                val l = x(sh.start).coerceAtLeast(GridLeft) - GridLeft
                                val r = minOf(x(sh.stop), GridRight + 40.dp) - GridLeft - 4.dp
                                if (r > l) GuideCell(
                                    sh.title, "${hm(sh.start)}–${hm(sh.stop)}", isNow = sh.isOn(now),
                                    focused = gridFocused && cursor.row == row && cursor.time >= sh.start && cursor.time < sh.stop,
                                    start = l, width = (r - l).coerceAtLeast(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            // The red "now" line with the time.
            if (now in windowStart until windowStart + WINDOW) {
                val nx = x(now)
                Box(Modifier.offset(x = nx - 0.75.dp, y = 20.dp).width(1.5.dp).fillMaxHeight().background(LumenColors.Live))
                Box(Modifier.offset(x = nx - 18.dp, y = 1.dp).width(36.dp).clip(LumenRadii.Badge).background(LumenColors.Live), contentAlignment = Alignment.Center) {
                    Txt(hm(now), LumenType.Caption2Strong.tabular().copy(fontSize = LumenType.Caption2.fontSize), modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
        if (groupMenu) GroupMenu(groups, g, { group = it; groupMenu = false; }, { groupMenu = false })
    }

    // The guide opens with focus on now for the current channel.
    val active = LocalScreenActive.current
    LaunchedEffect(g, active) { if (active) { awaitFrame(); runCatching { gridFocus.requestFocus() } } }
}

@Composable
private fun GuideCell(title: String, time: String?, isNow: Boolean, focused: Boolean, start: Dp, width: Dp) {
    val bg by animateColorAsState(
        when { focused -> LumenColors.Focused; isNow -> LumenColors.FillNow; else -> LumenColors.FillSubtle }, tween(150), label = "cell",
    )
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, LumenMotion.focus(), label = "cellScale")
    Column(
        Modifier
            .offset(x = start)
            .width(width)
            .fillMaxHeight()
            .graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = if (focused) 16f else 0f; shape = LumenRadii.Cell; clip = true }
            .background(bg)
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Txt(title, LumenType.Caption2Strong, color = if (focused) LumenColors.OnFocused else LumenColors.Label)
        if (time != null && width > 70.dp) Txt(time, LumenType.Caption2.tabular(), color = if (focused) LumenColors.OnFocused2 else LumenColors.Label3)
    }
}

@Composable
private fun ChannelHeader(ch: ChannelEntity, modifier: Modifier) {
    val item = remember(ch) { channelItems(listOf(ch), emptyMap(), emptySet()).first() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt("${ch.number}", LumenType.Caption1Strong.tabular(), Modifier.width(22.dp), color = LumenColors.Label3)
        ChannelLogo(item, Modifier.size(46.dp, 26.dp), height = 26.dp)
        Txt(ch.name, LumenType.Caption1Strong, Modifier.weight(1f))
    }
}

/** The group picker above the channel names (Favourites, categories). */
@Composable
private fun GroupChip(title: String, requester: FocusRequester, onClick: () -> Unit, modifier: Modifier = Modifier, onDown: (() -> Unit)? = null) {
    val state = rememberTvState()
    val bg by animateColorAsState(if (state.focused) LumenColors.Focused else LumenColors.Fill, tween(150), label = "chip")
    Row(
        modifier
            .focusRequester(requester)
            .controlFocusEffect(state, LumenRadii.Capsule)
            .background(bg, LumenRadii.Capsule)
            .tvClickable(state, onKey = { k -> if (k == androidx.compose.ui.input.key.Key.DirectionDown && onDown != null) { onDown(); true } else false }, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("guide:group"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Txt(title, LumenType.Caption2Strong, Modifier.widthIn(max = 130.dp), color = if (state.focused) LumenColors.OnFocused else LumenColors.Label)
        com.lck07.lumen.design.Icon(LumenIcons.Chevron, Modifier.graphicsLayer { rotationZ = 90f }, tint = if (state.focused) LumenColors.OnFocused else LumenColors.Label2, size = 9.dp)
    }
}

@Composable
private fun GroupMenu(groups: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.CenterStart) {
        Glass(LumenRadii.Banner, Modifier.padding(start = LumenSpacing.SafeX).width(300.dp), strong = true) {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(12.dp).height(400.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(groups.size) { i ->
                    val (k, title) = groups[i]
                    ListRow(title, null, { onPick(k) }, trailingIcon = if (k == selected) LumenIcons.Check else null, selected = k == selected,
                        focusRequester = if (k == selected) first else null)
                }
            }
        }
    }
    LaunchedEffect(Unit) { awaitFrame(); runCatching { first.requestFocus() } }
}

/** A small muted live picture of the current channel (shares the one player). */
@Composable
private fun GuidePreview(channelId: Int) {
    val c = LocalContext.current.container
    val active = LocalScreenActive.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val channel by produceState<ChannelEntity?>(null, channelId) { value = c.db.catalog().channel(channelId) }
    val key = "live:$channelId"
    LaunchedEffect(channelId, active) {
        if (!active) { if (c.playback.currentKey == key) c.playback.stop(); player = null; return@LaunchedEffect }
        delay(800)
        val ch = channel ?: c.db.catalog().channel(channelId) ?: return@LaunchedEffect
        val client = c.account.client ?: return@LaunchedEffect
        player = c.playback.play(key, client.liveUrl(ch.toModel(), c.settings.current().liveFormat.extension), live = true, volume = 0f)
    }
    DisposableEffect(channelId) { onDispose { if (c.playback.currentKey == key) c.playback.stop() } }
    Box(Modifier.size(240.dp, 135.dp).clip(LumenRadii.Tile).generatedArt(channel?.name ?: "guide", channel?.number ?: 0)) {
        if (player != null) VideoSurface(player)
        LiveBadge(stringResource(R.string.badge_live), Modifier.align(Alignment.TopEnd).padding(9.dp))
        channel?.let { ChannelLogo(remember(it) { channelItems(listOf(it), emptyMap(), emptySet()).first() }, Modifier.align(Alignment.TopStart).padding(9.dp)) }
    }
}

private fun floorSlot(t: Long) = t - Math.floorMod(t, SLOT)

/** The window starts at the half hour before now, earlier when now is just past it. */
private fun initialWindow(now: Long): Long {
    val f = floorSlot(now)
    return if (now - f < 10 * MIN) f - SLOT else f
}

private fun dayLabel(res: android.content.res.Resources, t: Long): String {
    val day = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (day) {
        today -> res.getString(R.string.today)
        today.plusDays(1) -> res.getString(R.string.tomorrow)
        else -> java.time.format.DateTimeFormatter.ofPattern("EEE d MMM").format(day)
    }
}
