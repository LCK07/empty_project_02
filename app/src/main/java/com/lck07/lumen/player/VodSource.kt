package com.lck07.lumen.player

import android.content.res.Resources
import com.lck07.lumen.AppContainer
import com.lck07.lumen.R
import com.lck07.lumen.core.library.WatchProgress
import com.lck07.lumen.core.model.Episode
import com.lck07.lumen.core.model.SeriesDetail
import com.lck07.lumen.core.xtream.XtreamException
import com.lck07.lumen.data.toModel
import com.lck07.lumen.ui.VodRequest

/** Everything the player needs for one film or episode. */
data class VodSource(
    /** The progress key (see [WatchProgress]). */
    val key: String,
    val url: String,
    val title: String,
    /** "Season 2, Episode 5: Tide Line" or "2021 · Drama". */
    val subtitle: String,
    val startMs: Long,
    val seriesId: Int?,
    val episode: Episode?,
    val movieId: Int?,
    /** The episode after this one, if any. */
    val next: Episode?,
    val nextLabel: String?,
)

/** Resolves what to play from the cache, fetching the episode list when it isn't cached yet. */
suspend fun resolveVod(c: AppContainer, res: Resources, request: VodRequest): VodSource {
    val client = c.account.client ?: throw XtreamException.AuthFailed()
    suspend fun detail(seriesId: Int): SeriesDetail =
        c.catalog.cachedSeriesDetail(seriesId) ?: c.catalog.refreshSeries(client, seriesId) ?: throw XtreamException.BadResponse()

    fun episodeSource(d: SeriesDetail, e: Episode, startMs: Long): VodSource {
        val next = d.next(e)
        return VodSource(
            key = WatchProgress.episodeKey(d.series.id, e.id),
            url = client.episodeUrl(e),
            title = d.series.name,
            subtitle = res.getString(R.string.season_episode_title, e.season, e.number, e.title),
            startMs = startMs,
            seriesId = d.series.id, episode = e, movieId = null,
            next = next,
            nextLabel = next?.let { res.getString(R.string.season_episode_title, it.season, it.number, it.title) },
        )
    }

    suspend fun resumeAt(key: String, startOver: Boolean): Long {
        if (startOver) return 0
        val p = c.library.progressFor(key) ?: return 0
        return if (p.inProgress) p.positionMs else 0
    }

    return when (request) {
        is VodRequest.Episode -> {
            var d = detail(request.seriesId)
            var e = d.episodes.values.flatten().firstOrNull { it.id == request.episodeId }
            if (e == null) {
                // The cached list may be out of date: fetch it again once.
                d = c.catalog.refreshSeries(client, request.seriesId) ?: d
                e = d.episodes.values.flatten().firstOrNull { it.id == request.episodeId } ?: throw XtreamException.BadResponse()
            }
            episodeSource(d, e, resumeAt(WatchProgress.episodeKey(request.seriesId, e.id), request.startOver))
        }
        is VodRequest.SeriesStart -> {
            val d = detail(request.seriesId)
            val e = d.seasons.firstNotNullOfOrNull { d.episodes[it]?.firstOrNull() } ?: throw XtreamException.BadResponse()
            episodeSource(d, e, 0)
        }
        is VodRequest.Movie -> {
            val dao = c.db.catalog()
            val m = dao.movie(request.movieId)?.toModel() ?: throw XtreamException.BadResponse()
            // get_vod_info knows the real container (mkv, mp4...); the list sometimes doesn't.
            runCatching { c.catalog.refreshMovie(client, request.movieId) }
            val extra = dao.movieDetailNow(request.movieId)
            val movie = m.copy(extension = extra?.extension ?: m.extension)
            val key = WatchProgress.movieKey(m.id)
            VodSource(
                key = key,
                url = client.movieUrl(movie),
                title = m.name,
                subtitle = listOfNotNull(
                    extra?.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) },
                    extra?.genre?.substringBefore(',')?.trim(),
                ).joinToString(" · "),
                startMs = resumeAt(key, request.startOver),
                seriesId = null, episode = null, movieId = m.id, next = null, nextLabel = null,
            )
        }
    }
}
