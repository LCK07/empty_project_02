package com.lck07.lumen.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lck07.lumen.design.Glass
import com.lck07.lumen.design.Icon
import com.lck07.lumen.design.LocalReduceMotion
import kotlinx.coroutines.delay

/** The video picture (and subtitles). Compose keeps focus; the view never takes it. */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                keepScreenOn = true
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
    )
}

/** Player state mirrored into Compose. Positions are polled while [ticking]. */
@Stable
class PlayerState {
    var playing by mutableStateOf(false)
    var playWhenReady by mutableStateOf(true)
    var state by mutableIntStateOf(Player.STATE_IDLE)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(C.TIME_UNSET)
    var bufferedFraction by mutableFloatStateOf(0f)
    var tracks by mutableStateOf(Tracks.EMPTY)
    var error by mutableStateOf<PlaybackException?>(null)
    /** True once the first frame was shown for the current item. */
    var firstFrame by mutableStateOf(false)

    val buffering: Boolean get() = state == Player.STATE_BUFFERING || (state == Player.STATE_IDLE && error == null)
    val duration: Long get() = durationMs.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L

    fun sync(p: Player) {
        playing = p.isPlaying
        playWhenReady = p.playWhenReady
        state = p.playbackState
        positionMs = p.currentPosition.coerceAtLeast(0)
        durationMs = p.duration
        bufferedFraction = if (duration > 0) (p.bufferedPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
    }
}

@Composable
fun rememberPlayerState(player: ExoPlayer?, ticking: Boolean = true): PlayerState {
    val s = remember { PlayerState() }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        s.sync(player); s.tracks = player.currentTracks; s.error = player.playerError
        val l = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) { s.sync(p) }
            override fun onTracksChanged(tracks: Tracks) { s.tracks = tracks }
            override fun onPlayerError(error: PlaybackException) { s.error = error }
            override fun onRenderedFirstFrame() { s.firstFrame = true }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { s.firstFrame = false; s.error = null }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(player, ticking) {
        while (player != null && ticking) { s.sync(player); delay(500) }
    }
    return s
}

/** The round glass flash in the middle of the picture when playback pauses or resumes. */
@Composable
fun PlayPauseFlash(icon: ImageVector?, serial: Int, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(serial) { if (serial > 0) { shown = true; delay(650); shown = false } }
    val a by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, androidx.compose.animation.core.tween(if (reduce) 0 else 220), label = "flash")
    if (icon != null && a > 0.01f) Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Glass(CircleShape, Modifier.size(80.dp).graphicsLayer { alpha = a; val sc = 0.8f + 0.2f * a; scaleX = sc; scaleY = sc }, strong = true) {
            Icon(icon, Modifier.align(Alignment.Center), size = 34.dp)
        }
    }
}

/** "1:02:03" or "4:05". */
fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s / 60) % 60; val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}
