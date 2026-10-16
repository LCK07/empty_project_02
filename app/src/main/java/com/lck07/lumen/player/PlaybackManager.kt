package com.lck07.lumen.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.lck07.lumen.data.BufferSize
import com.lck07.lumen.data.Settings
import com.lck07.lumen.data.SettingsStore
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import okhttp3.OkHttpClient

/**
 * The one player of the app. Xtream accounts usually allow a single stream at a time, so the
 * live preview and full-screen playback share this player: going full screen keeps the stream,
 * and starting anything else stops what was playing.
 */
@OptIn(UnstableApi::class)
class PlaybackManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val settingsStore: SettingsStore,
) {
    private var exo: ExoPlayer? = null
    private var builtWith: BufferSize? = null

    /** What is loaded now ("live:12", "movie:3", "episode:1:44"), so screens can tell if it's theirs. */
    var currentKey: String? = null
        private set

    fun player(settings: Settings): ExoPlayer {
        exo?.let { if (builtWith == settings.buffer) { applyTrackPreferences(it, settings); return it } else release() }
        return build(settings).also { exo = it; builtWith = settings.buffer }
    }

    private fun build(settings: Settings): ExoPlayer {
        // Hardware decoders first; FFmpeg (AC-3, E-AC-3, DTS, TrueHD...) only where the device has none.
        val renderers = NextRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val dataSource = OkHttpDataSource.Factory(http)
        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
            .setConstantBitrateSeekingEnabled(true)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(settings.buffer.minMs, settings.buffer.maxMs, 1_000, 2_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource, extractors))
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .also { applyTrackPreferences(it, settings) }
    }

    private fun applyTrackPreferences(p: ExoPlayer, s: Settings) {
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(s.audioLanguage)
            .setPreferredTextLanguage(s.subtitleLanguage)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, s.subtitleLanguage == null)
            .build()
    }

    /** Starts a stream unless it is already the one playing (e.g. preview to full screen). */
    suspend fun play(key: String, url: String, startMs: Long = 0, live: Boolean = false, volume: Float = 1f): ExoPlayer {
        val settings = settingsStore.current()
        val p = player(settings)
        p.volume = volume
        if (currentKey == key && p.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            p.playWhenReady = true
            return p
        }
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaId(key)
            .apply {
                when {
                    url.endsWith(".m3u8", ignoreCase = true) -> setMimeType(MimeTypes.APPLICATION_M3U8)
                    url.endsWith(".ts", ignoreCase = true) -> setMimeType(MimeTypes.VIDEO_MP2T)
                }
                if (live) setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(4_000).build())
            }
            .build()
        currentKey = key
        p.setMediaItem(item, if (live) C.TIME_UNSET else startMs)
        p.prepare()
        p.playWhenReady = true
        return p
    }

    /** Stops the stream (freeing the account's connection) but keeps the player for a fast restart. */
    fun stop() {
        currentKey = null
        exo?.run { stop(); clearMediaItems() }
    }

    fun release() {
        currentKey = null
        exo?.release()
        exo = null
    }
}
