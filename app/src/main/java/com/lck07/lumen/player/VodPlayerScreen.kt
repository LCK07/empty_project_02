package com.lck07.lumen.player

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lck07.lumen.R
import com.lck07.lumen.container
import com.lck07.lumen.data.explain
import com.lck07.lumen.design.ButtonRow
import com.lck07.lumen.design.Eyebrow
import com.lck07.lumen.design.Glass
import com.lck07.lumen.design.ListRow
import com.lck07.lumen.design.LocalReduceMotion
import com.lck07.lumen.design.LumenButton
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.LumenMotion
import com.lck07.lumen.design.LumenRadii
import com.lck07.lumen.design.LumenSpacing
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.LumenType.tabular
import com.lck07.lumen.design.MessageState
import com.lck07.lumen.design.Spinner
import com.lck07.lumen.design.Txt
import com.lck07.lumen.ui.VodRequest
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val SAVE_EVERY_MS = 5_000L
private const val UP_NEXT_BEFORE_END_MS = 30_000L
private const val EARLY_END_MS = 30_000L

private enum class Menu { None, Subtitles, Audio }

/** Keys the player leaves to the system (Back is handled by the screen stack). */
private val PassThroughKeys = setOf(
    AndroidKeyEvent.KEYCODE_BACK, AndroidKeyEvent.KEYCODE_HOME, AndroidKeyEvent.KEYCODE_VOLUME_UP, AndroidKeyEvent.KEYCODE_VOLUME_DOWN,
    AndroidKeyEvent.KEYCODE_VOLUME_MUTE, AndroidKeyEvent.KEYCODE_POWER, AndroidKeyEvent.KEYCODE_SEARCH, AndroidKeyEvent.KEYCODE_ESCAPE,
)

/**
 * Full-screen playback of a film or an episode. Resumes at the saved position without asking,
 * shows a black screen with a spinner only when loading takes longer than 2 s, and brings up
 * the transport overlay on any key. Back hides the overlay; a second Back leaves.
 */
@Composable
fun VodPlayerScreen(request: VodRequest, onPlayNext: (VodRequest) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val res = context.resources
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current

    var source by remember(request) { mutableStateOf<VodSource?>(null) }
    var loadError by remember(request) { mutableStateOf<Throwable?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(request, attempt) {
        loadError = null
        try {
            val s = resolveVod(c, res, request)
            source = s
            player = c.playback.play(s.key, s.url, s.startMs)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            loadError = e
        }
    }

    var overlay by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(Menu.None) }
    var upNextDismissed by remember(request) { mutableStateOf(false) }
    var earlyEnds by remember(request) { mutableIntStateOf(0) }
    var lastKeyAt by remember { mutableLongStateOf(0L) }
    var flashSerial by remember { mutableIntStateOf(0) }
    var flashIcon by remember { mutableStateOf(LumenIcons.Pause) }
    /** While left/right are held the scrubber moves to a pending position, applied when released. */
    var pendingSeek by remember { mutableStateOf<Long?>(null) }

    val ps = rememberPlayerState(player)
    val src = source

    fun showOverlay() { overlay = true; lastKeyAt = System.currentTimeMillis() }
    fun togglePlay() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
        p.playWhenReady = !p.playWhenReady
        flashIcon = if (p.playWhenReady) LumenIcons.Play else LumenIcons.Pause
        flashSerial++
        showOverlay()
    }
    fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val base = pendingSeek ?: p.currentPosition
        val max = ps.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        pendingSeek = (base + deltaMs).coerceIn(0, max)
        showOverlay()
    }

    // Apply a pending seek once the key has been released for a moment.
    LaunchedEffect(pendingSeek) {
        val target = pendingSeek ?: return@LaunchedEffect
        delay(450)
        player?.seekTo(target)
        pendingSeek = null
    }

    // The overlay hides after about 4 s without a key (it stays while paused or a menu is open).
    LaunchedEffect(overlay, lastKeyAt, ps.playWhenReady, menu) {
        if (overlay && ps.playWhenReady && menu == Menu.None) {
            delay(LumenMotion.OverlayTimeoutMs)
            overlay = false
        }
    }

    // Save progress every few seconds while playing, and when leaving.
    val latestSource by rememberUpdatedState(src)
    fun save() {
        val s = latestSource ?: return
        val p = player ?: return
        if (c.playback.currentKey != s.key) return
        val pos = p.currentPosition
        val dur = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        // A stream that stops early (dropped connection) also "ends": only a real end counts as watched.
        val ended = p.playbackState == Player.STATE_ENDED && pos >= dur - EARLY_END_MS
        c.scope.launch {
            val at = if (ended) dur else pos
            if (s.episode != null && s.seriesId != null) c.library.saveEpisodeProgress(s.seriesId, s.episode.id, at, dur)
            else if (s.movieId != null) c.library.saveMovieProgress(s.movieId, at, dur)
        }
    }
    LaunchedEffect(player, src) {
        while (true) { delay(SAVE_EVERY_MS); if (ps.playing) save() }
    }
    DisposableEffect(Unit) {
        onDispose { save(); c.playback.stop() }
    }

    // At the end: the next episode plays; a film (or the last episode) returns to the detail.
    LaunchedEffect(ps.state, src) {
        if (ps.state == Player.STATE_ENDED && src != null) {
            val p = player
            if (p != null && ps.duration > 0 && p.currentPosition < ps.duration - EARLY_END_MS && earlyEnds < 3) {
                earlyEnds++
                // The server closed the stream early: pick up where it stopped.
                val at = p.currentPosition
                p.seekTo(at); p.prepare(); p.playWhenReady = true
                return@LaunchedEffect
            }
            save()
            val next = src.next
            if (next != null && src.seriesId != null && !upNextDismissed) onPlayNext(VodRequest.Episode(src.seriesId, next.id))
            else onExit()
        }
    }

    val remaining = if (ps.duration > 0) ps.duration - ps.positionMs else Long.MAX_VALUE
    val upNextVisible = src?.next != null && !upNextDismissed && ps.duration > 60_000 && remaining in 1..UP_NEXT_BEFORE_END_MS && ps.state != Player.STATE_ENDED

    BackHandler(enabled = menu != Menu.None || overlay || upNextVisible) {
        when {
            menu != Menu.None -> menu = Menu.None
            upNextVisible -> upNextDismissed = true
            else -> overlay = false
        }
    }

    // A slow start shows a spinner on black; quick starts show nothing at all.
    var slow by remember(request) { mutableStateOf(false) }
    LaunchedEffect(request, ps.firstFrame) { slow = false; if (!ps.firstFrame) { delay(2_000); slow = true } }

    val scrubber = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val upNextPlay = remember { FocusRequester() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("vod-player")
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = e.key.nativeKeyCode
                when (code) {
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_SPACE -> { togglePlay(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { if (player?.playWhenReady == false) togglePlay(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { if (player?.playWhenReady == true) togglePlay(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-10_000L * (1 + e.nativeKeyEvent.repeatCount / 4)); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(10_000L * (1 + e.nativeKeyEvent.repeatCount / 4)); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> { src?.next?.let { n -> src.seriesId?.let { onPlayNext(VodRequest.Episode(it, n.id)) } }; true }
                    in PassThroughKeys -> false
                    else -> if (loadError != null || ps.error != null) false else {
                        if (!overlay && !upNextVisible && menu == Menu.None) {
                            // The first key only brings up the overlay (Select included); the
                            // overlay puts focus on the scrubber when it appears.
                            showOverlay()
                            true
                        } else { lastKeyAt = System.currentTimeMillis(); false }
                    }
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        VideoSurface(player)

        // Loading: a spinner only, after 2 s.
        val showSpinner = loadError == null && ps.error == null && ((!ps.firstFrame && slow) || (ps.firstFrame && ps.state == Player.STATE_BUFFERING))
        if (showSpinner) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(size = 34.dp) }

        val err = loadError ?: ps.error
        if (err != null) {
            val retry = remember { FocusRequester() }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                MessageState(
                    stringResource(R.string.playback_error_title), if (loadError != null) res.explain(loadError!!) else stringResource(R.string.playback_error),
                    icon = LumenIcons.Warning, action = stringResource(R.string.action_retry),
                    onAction = {
                        if (loadError != null) attempt++
                        else player?.let { p -> ps.error = null; p.prepare(); p.playWhenReady = true }
                    },
                    actionRequester = retry,
                )
            }
            LaunchedEffect(err) { awaitFrame(); runCatching { retry.requestFocus() } }
        }

        PlayPauseFlash(flashIcon, flashSerial)

        if (src != null && err == null) {
            val shown by animateFloatAsState(if (overlay) 1f else 0f, tween(if (reduce) 0 else 380), label = "osd")
            if (shown > 0.01f) TransportOverlay(
                source = src, ps = ps, pendingSeek = pendingSeek, visibility = shown, reduce = reduce,
                scrubber = scrubber,
                onToggle = ::togglePlay, onSeek = ::seekBy,
                onSubtitles = { menu = Menu.Subtitles }, onAudio = { menu = Menu.Audio },
                onNext = { val n = src.next; val sid = src.seriesId; if (n != null && sid != null) onPlayNext(VodRequest.Episode(sid, n.id)) },
                subtitleValue = selectedLabel(trackChoices(res, ps.tracks, C.TRACK_TYPE_TEXT, player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true)),
                audioValue = selectedLabel(trackChoices(res, ps.tracks, C.TRACK_TYPE_AUDIO, false)),
                enabled = overlay && menu == Menu.None,
            )
            if (upNextVisible) UpNextCard(
                src, remaining, upNextPlay,
                onPlay = { val sid = src.seriesId; val n = src.next; if (sid != null && n != null) onPlayNext(VodRequest.Episode(sid, n.id)) },
                onDismiss = { upNextDismissed = true },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        if (menu != Menu.None && player != null) {
            val type = if (menu == Menu.Audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
            val disabled = type == C.TRACK_TYPE_TEXT && player!!.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            TrackMenu(
                title = stringResource(if (menu == Menu.Audio) R.string.audio else R.string.subtitles),
                choices = trackChoices(res, ps.tracks, type, disabled),
                onChoose = { choice -> selectTrack(player!!, type, choice); menu = Menu.None; showOverlay() },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }

    // Focus goes to the player itself (keys reach it even while the overlay is hidden).
    LaunchedEffect(Unit) { awaitFrame(); runCatching { rootFocus.requestFocus() } }
    // When the overlay hides, focus returns to the root so the next key brings it back.
    LaunchedEffect(Unit) {
        snapshotFlow { (overlay || menu != Menu.None) to upNextVisible }.distinctUntilChanged().collect { (visible, upNext) ->
            if (!visible) { awaitFrame(); runCatching { (if (upNext) upNextPlay else rootFocus).requestFocus() } }
        }
    }
}

@Composable
private fun TransportOverlay(
    source: VodSource,
    ps: PlayerState,
    pendingSeek: Long?,
    visibility: Float,
    reduce: Boolean,
    scrubber: FocusRequester,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onNext: () -> Unit,
    subtitleValue: String?,
    audioValue: String?,
    enabled: Boolean,
) {
    LaunchedEffect(enabled) { if (enabled) { awaitFrame(); runCatching { scrubber.requestFocus() } } }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = visibility; if (!reduce) translationY = (1f - visibility) * 20.dp.toPx() }
            .focusProperties { canFocus = enabled },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .padding(start = LumenSpacing.SafeX, end = LumenSpacing.SafeX, top = 70.dp, bottom = LumenSpacing.SafeY),
        ) {
            Txt(source.title, LumenType.Title3)
            if (source.subtitle.isNotBlank()) Txt(source.subtitle, LumenType.Caption1, color = LumenColors.Label2, modifier = Modifier.padding(top = 4.dp))
            val pos = pendingSeek ?: ps.positionMs
            Scrubber(
                position = pos, duration = ps.duration, buffered = ps.bufferedFraction, playing = ps.playWhenReady,
                requester = scrubber, onToggle = onToggle, onSeek = onSeek,
                modifier = Modifier.padding(top = 17.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Txt(clock(pos), LumenType.Caption2Strong.tabular(), color = LumenColors.Label2)
                Txt("−" + clock((ps.duration - pos).coerceAtLeast(0)), LumenType.Caption2Strong.tabular(), color = LumenColors.Label2)
            }
            ButtonRow(Modifier.padding(top = 15.dp)) {
                LumenButton(
                    subtitleValue?.let { stringResource(R.string.subtitles_value, it) } ?: stringResource(R.string.subtitles),
                    onSubtitles, icon = LumenIcons.Subtitles, small = true, modifier = Modifier.testTag("osd:subtitles"),
                )
                LumenButton(
                    audioValue?.let { stringResource(R.string.audio_value, it) } ?: stringResource(R.string.audio),
                    onAudio, icon = LumenIcons.Audio, small = true, modifier = Modifier.testTag("osd:audio"),
                )
                if (source.next != null) LumenButton(stringResource(R.string.action_next_episode), onNext, icon = LumenIcons.Next, small = true, modifier = Modifier.testTag("osd:next"))
            }
        }
    }
}

/**
 * The scrubber: left/right seek 10 s (faster while held), Select plays and pauses. It grows
 * a little while focused.
 */
@Composable
private fun Scrubber(
    position: Long,
    duration: Long,
    buffered: Float,
    playing: Boolean,
    requester: FocusRequester,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (focused) 1.6f else 1f, LumenMotion.focus(), label = "scrub")
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val desc = "${clock(position)} / ${clock(duration)}"
    val playingDesc = stringResource(if (playing) R.string.action_play else R.string.paused)
    Box(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                val code = e.key.nativeKeyCode
                val repeat = e.nativeKeyEvent.repeatCount
                when {
                    e.type != KeyEventType.KeyDown -> code == AndroidKeyEvent.KEYCODE_DPAD_CENTER || code == AndroidKeyEvent.KEYCODE_ENTER
                    code == AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { onSeek(-10_000L * (1 + repeat / 4)); true }
                    code == AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { onSeek(10_000L * (1 + repeat / 4)); true }
                    code == AndroidKeyEvent.KEYCODE_DPAD_CENTER || code == AndroidKeyEvent.KEYCODE_ENTER -> { if (repeat == 0) onToggle(); true }
                    else -> false
                }
            }
            .semantics { contentDescription = desc; stateDescription = playingDesc }
            .focusable()
            .testTag("osd:scrubber"),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.fillMaxWidth().height(5.dp).graphicsLayer { scaleY = grow }.clip(LumenRadii.Capsule).background(Color.White.copy(alpha = 0.28f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(buffered.coerceAtLeast(fraction)).background(Color.White.copy(alpha = 0.18f)))
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(LumenRadii.Capsule).background(Color.White))
        }
        // The playhead shows while the scrubber has focus.
        if (focused) Box(
            Modifier
                .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                .wrapContentWidth(Alignment.End)
                .offset(x = 7.dp)
                .size(14.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White),
        )
    }
}

/** Near the end of an episode: the next one, with a countdown. */
@Composable
private fun UpNextCard(source: VodSource, remainingMs: Long, play: FocusRequester, onPlay: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Glass(LumenRadii.Banner, modifier.padding(end = LumenSpacing.SafeX, bottom = LumenSpacing.SafeY).width(320.dp), strong = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow(stringResource(R.string.eyebrow_up_next))
            Txt(source.nextLabel.orEmpty(), LumenType.Callout, maxLines = 2)
            Txt(stringResource(R.string.next_episode_in, ((remainingMs + 999) / 1000).toInt()), LumenType.Caption1.tabular(), color = LumenColors.Label2)
            ButtonRow(Modifier.padding(top = 6.dp)) {
                LumenButton(stringResource(R.string.action_play_next), onPlay, icon = LumenIcons.Play, small = true, focusRequester = play, modifier = Modifier.testTag("upnext:play"))
                LumenButton(stringResource(R.string.action_watch_credits), onDismiss, small = true)
            }
        }
    }
    // The card appearing is a change to what's playing, so it may take focus.
    LaunchedEffect(Unit) { awaitFrame(); runCatching { play.requestFocus() } }
}

/** The Audio or Subtitles choices in a glass panel at the right. */
@Composable
private fun TrackMenu(title: String, choices: List<TrackChoice>, onChoose: (TrackChoice) -> Unit, modifier: Modifier = Modifier) {
    val first = remember { FocusRequester() }
    Glass(LumenRadii.Banner, modifier.padding(end = LumenSpacing.SafeX).widthIn(min = 260.dp, max = 320.dp), strong = true) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(title, LumenType.Headline, Modifier.padding(start = 4.dp, bottom = 4.dp))
            val sel = choices.indexOfFirst { it.selected }.coerceAtLeast(0)
            choices.forEachIndexed { i, ch ->
                ListRow(
                    ch.label, null, { onChoose(ch) },
                    trailingIcon = if (ch.selected) LumenIcons.Check else null, selected = ch.selected,
                    focusRequester = if (i == sel) first else null,
                )
            }
            if (choices.isEmpty()) Txt(stringResource(R.string.off), LumenType.Body, color = LumenColors.Label2)
        }
    }
    LaunchedEffect(Unit) { awaitFrame(); runCatching { first.requestFocus() } }
}
