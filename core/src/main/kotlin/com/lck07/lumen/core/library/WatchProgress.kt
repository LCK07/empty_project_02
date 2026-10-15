package com.lck07.lumen.core.library

import com.lck07.lumen.core.model.Episode
import com.lck07.lumen.core.model.SeriesDetail
import java.time.Instant

/** Where someone stopped in a film or an episode. */
data class WatchProgress(
    /** "movie:<id>" or "episode:<seriesId>:<episodeId>". */
    val key: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Instant,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val remainingMs: Long get() = (durationMs - positionMs).coerceAtLeast(0)

    /** Watched far enough to count as started, not so far that it counts as finished. */
    val inProgress: Boolean get() = fraction in STARTED..FINISHED && positionMs > MIN_POSITION_MS
    val finished: Boolean get() = fraction > FINISHED || (durationMs > 0 && remainingMs < CREDITS_MS)

    val seriesId: Int? get() = key.takeIf { it.startsWith("episode:") }?.split(':')?.getOrNull(1)?.toIntOrNull()
    val episodeId: String? get() = key.takeIf { it.startsWith("episode:") }?.split(':')?.getOrNull(2)
    val movieId: Int? get() = key.takeIf { it.startsWith("movie:") }?.substringAfter(':')?.toIntOrNull()

    companion object {
        const val STARTED = 0.02f
        const val FINISHED = 0.95f
        const val MIN_POSITION_MS = 60_000L
        const val CREDITS_MS = 90_000L

        fun movieKey(movieId: Int) = "movie:$movieId"
        fun episodeKey(seriesId: Int, episodeId: String) = "episode:$seriesId:$episodeId"
    }
}

/** What Home offers for a series: the episode to play and how far into it. */
data class UpNext(val detail: SeriesDetail, val episode: Episode, val progress: WatchProgress?, val lastWatched: Instant) {
    /** True when this resumes a half-watched episode, false when it starts the next one. */
    val resumes: Boolean get() = progress?.inProgress == true
}

object Library {
    /**
     * Series to continue, most recently watched first. A half-watched episode resumes;
     * a finished one moves on to the next episode; a finished series drops out.
     */
    fun upNext(progress: Collection<WatchProgress>, details: (seriesId: Int) -> SeriesDetail?): List<UpNext> =
        progress.filter { it.seriesId != null }
            .groupBy { it.seriesId!! }
            .mapNotNull { (seriesId, entries) ->
                val detail = details(seriesId) ?: return@mapNotNull null
                val latest = entries.maxBy { it.updatedAt }
                val episode = detail.episodes.values.flatten().firstOrNull { it.id == latest.episodeId } ?: return@mapNotNull null
                when {
                    latest.finished -> detail.next(episode)?.let { next ->
                        UpNext(detail, next, entries.firstOrNull { it.episodeId == next.id }?.takeIf { it.inProgress }, latest.updatedAt)
                    }
                    else -> UpNext(detail, episode, latest.takeIf { it.inProgress }, latest.updatedAt)
                }
            }
            .sortedByDescending { it.lastWatched }

    /** Films started and not finished, most recent first. */
    fun moviesInProgress(progress: Collection<WatchProgress>): List<WatchProgress> =
        progress.filter { it.movieId != null && it.inProgress }.sortedByDescending { it.updatedAt }
}
