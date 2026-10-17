package com.lck07.lumen.data

import com.lck07.lumen.core.library.Library
import com.lck07.lumen.core.library.UpNext
import com.lck07.lumen.core.library.WatchProgress
import com.lck07.lumen.data.db.FavouriteEntity
import com.lck07.lumen.data.db.LumenDatabase
import com.lck07.lumen.data.db.ProgressEntity
import com.lck07.lumen.data.db.WatchlistEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant

enum class FavouriteKind(val key: String) { Series("series"), Movie("movie"), Channel("channel") }

/** Something that mirrors progress elsewhere (the Google TV "Watch Next" row). */
interface ProgressListener {
    suspend fun onProgress(p: ProgressEntity)
}

/**
 * Watch progress, favourites and watchlist of the active profile, and the Continue Watching rules
 * from core. Every Flow re-emits when the active profile changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepository(private val db: LumenDatabase, private val catalog: CatalogRepository, private val profiles: ProfileRepository) {
    private val dao = db.library()
    var listener: ProgressListener? = null

    private suspend fun profileId() = profiles.activeId.first()

    /** Active-profile id first, then the query for it. */
    private fun <T> forProfile(query: (Int) -> Flow<T>): Flow<T> = profiles.activeId.flatMapLatest(query)

    val progress: Flow<List<WatchProgress>> = forProfile { id -> dao.progress(id).map { list -> list.map { it.toModel() } } }

    fun seriesProgress(seriesId: Int): Flow<List<WatchProgress>> =
        forProfile { id -> dao.progressForSeries(id, seriesId).map { l -> l.map { it.toModel() } } }
    fun movieProgress(movieId: Int): Flow<WatchProgress?> = forProfile { id -> dao.progressForMovie(id, movieId).map { it?.toModel() } }
    suspend fun progressFor(key: String): WatchProgress? = dao.progress(profileId(), key)?.toModel()

    suspend fun saveEpisodeProgress(seriesId: Int, episodeId: String, positionMs: Long, durationMs: Long) =
        save(ProgressEntity(profileId(), WatchProgress.episodeKey(seriesId, episodeId), seriesId, null, episodeId, positionMs, durationMs, System.currentTimeMillis()))

    suspend fun saveMovieProgress(movieId: Int, positionMs: Long, durationMs: Long) =
        save(ProgressEntity(profileId(), WatchProgress.movieKey(movieId), null, movieId, null, positionMs, durationMs, System.currentTimeMillis()))

    private suspend fun save(p: ProgressEntity) {
        if (p.durationMs <= 0) return
        val existing = dao.progress(p.profileId, p.key)
        val row = p.copy(watchNextId = existing?.watchNextId)
        dao.upsertProgress(row)
        runCatching { listener?.onProgress(row) }
    }

    /** Series to continue, most recent first, using cached episode lists. */
    suspend fun upNext(all: List<WatchProgress>): List<UpNext> {
        val ids = all.mapNotNull { it.seriesId }.distinct()
        val details = ids.associateWith { catalog.cachedSeriesDetail(it) }
        return Library.upNext(all) { details[it] }
    }

    fun favourites(kind: FavouriteKind): Flow<List<String>> = forProfile { id -> dao.favourites(id, kind.key).map { l -> l.map { it.itemId } } }
    fun isFavourite(kind: FavouriteKind, id: String): Flow<Boolean> = forProfile { p -> dao.isFavourite(p, kind.key, id) }
    suspend fun favouriteIdsNow(kind: FavouriteKind): List<String> = dao.favouriteIdsNow(profileId(), kind.key)

    /** Adds or removes a favourite; returns true when it is now a favourite. */
    suspend fun toggleFavourite(kind: FavouriteKind, id: String): Boolean {
        val p = profileId()
        val now = dao.isFavouriteNow(p, kind.key, id)
        if (now) dao.removeFavourite(p, kind.key, id) else dao.addFavourite(FavouriteEntity(p, kind.key, id, System.currentTimeMillis()))
        return !now
    }

    /** Watchlist ids, newest first. Only series and movies are meaningful kinds. */
    fun watchlist(kind: FavouriteKind): Flow<List<String>> = forProfile { id -> dao.watchlist(id, kind.key).map { l -> l.map { it.itemId } } }
    fun isInWatchlist(kind: FavouriteKind, id: String): Flow<Boolean> = forProfile { p -> dao.isInWatchlist(p, kind.key, id) }

    /** Adds or removes a watchlist entry; returns true when it is now on the watchlist. */
    suspend fun toggleWatchlist(kind: FavouriteKind, id: String): Boolean {
        val p = profileId()
        val now = dao.isInWatchlistNow(p, kind.key, id)
        if (now) dao.removeFromWatchlist(p, kind.key, id) else dao.addToWatchlist(WatchlistEntity(p, kind.key, id, System.currentTimeMillis()))
        return !now
    }
}

fun ProgressEntity.toModel() = WatchProgress(key, positionMs, durationMs, Instant.ofEpochMilli(updatedAt))
