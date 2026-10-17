package com.lck07.lumen.tv

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.lck07.lumen.core.library.WatchProgress
import com.lck07.lumen.data.ProgressListener
import com.lck07.lumen.data.db.LumenDatabase
import com.lck07.lumen.data.db.ProgressEntity
import com.lck07.lumen.data.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mirrors Continue Watching into the Google TV home screen's "Watch Next" row. Devices without
 * the TV provider (Fire TV) are skipped quietly. Updates for one title are throttled.
 */
class WatchNextPublisher(private val context: Context, private val db: LumenDatabase) : ProgressListener {
    private val helper by lazy { PreviewChannelHelper(context) }
    private val lastPublished = HashMap<String, Long>()

    private val supported: Boolean by lazy {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
            runCatching { context.contentResolver.getType(TvContractCompat.WatchNextPrograms.CONTENT_URI) }.isSuccess &&
            !context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
    }

    /** Shows only [profileId]'s Continue Watching on the home screen: clear everything, republish. */
    suspend fun switchTo(profileId: Int) {
        if (!supported) return
        withContext(Dispatchers.IO) {
            val dao = db.library()
            dao.publishedWatchNextIds().forEach { remove(it) }
            dao.clearWatchNextIds()
            lastPublished.clear()
            dao.progressNow(profileId).forEach { onProgress(it.copy(watchNextId = null)) }
        }
    }

    override suspend fun onProgress(p: ProgressEntity) {
        if (!supported) return
        val wp = p.toModel()
        val now = System.currentTimeMillis()
        val last = lastPublished[p.key] ?: 0
        if (!wp.finished && p.watchNextId != null && now - last < 60_000) return
        lastPublished[p.key] = now
        withContext(Dispatchers.IO) {
            try {
                if (wp.finished || !wp.inProgress) {
                    p.watchNextId?.let { remove(it); db.library().setWatchNextId(p.profileId, p.key, null) }
                    return@withContext
                }
                val program = build(p, wp) ?: return@withContext
                val id = p.watchNextId
                if (id != null && helper.getWatchNextProgram(id) != null) helper.updateWatchNextProgram(program, id)
                else db.library().setWatchNextId(p.profileId, p.key, helper.publishWatchNextProgram(program))
                if (com.lck07.lumen.BuildConfig.DEBUG) Log.i(TAG, "published ${p.key}")
            } catch (e: Exception) {
                Log.w(TAG, "Watch Next update failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun build(p: ProgressEntity, wp: WatchProgress): WatchNextProgram? {
        val catalog = db.catalog()
        val b = WatchNextProgram.Builder()
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(p.updatedAt)
            .setLastPlaybackPositionMillis(p.positionMs.toInt())
            .setDurationMillis(p.durationMs.toInt())
            .setInternalProviderId(p.key)
            .setIntentUri(Uri.parse("lumen://play?key=${Uri.encode(p.key)}"))
        val seriesId = wp.seriesId
        val movieId = wp.movieId
        when {
            seriesId != null -> {
                val s = catalog.series(seriesId) ?: return null
                val e = wp.episodeId?.let { catalog.episode(it) } ?: return null
                b.setType(TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE)
                    .setTitle(s.name)
                    .setEpisodeTitle(e.title)
                    .setSeasonNumber(e.season)
                    .setEpisodeNumber(e.number)
                s.cover?.let { b.setPosterArtUri(Uri.parse(it)).setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3) }
                e.image?.let { b.setThumbnailUri(Uri.parse(it)) }
            }
            movieId != null -> {
                val m = catalog.movie(movieId) ?: return null
                b.setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE).setTitle(m.name)
                m.poster?.let { b.setPosterArtUri(Uri.parse(it)).setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3) }
            }
            else -> return null
        }
        return b.build()
    }

    @SuppressLint("RestrictedApi")
    private fun remove(id: Long) {
        runCatching { context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null) }
    }

    private companion object { const val TAG = "WatchNext" }
}
