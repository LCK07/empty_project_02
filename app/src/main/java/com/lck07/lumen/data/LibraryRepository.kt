package com.lck07.lumen.data

import com.lck07.lumen.core.library.Library
import com.lck07.lumen.core.library.UpNext
import com.lck07.lumen.core.library.WatchProgress
import com.lck07.lumen.data.db.FavouriteEntity
import com.lck07.lumen.data.db.LumenDatabase
import com.lck07.lumen.data.db.ProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

enum class FavouriteKind(val key: String) { Series("series"), Movie("movie"), Channel("channel") }

/** Something that mirrors progress elsewhere (the Google TV "Watch Next" row). */
interface ProgressListener {
    suspend fun onProgress(p: ProgressEntity)
}

/** Watch progress and favourites, and the Continue Watching rules from core. */
class LibraryRepository(private val db: LumenDatabase, private val catalog: CatalogRepository) {
    private val dao = db.library()
    var listener: ProgressListener? = null

    val progress: Flow<List<WatchProgress>> = dao.progress().map { list -> list.map { it.toModel() } }

    fun seriesProgress(seriesId: Int): Flow<List<WatchProgress>> = dao.progressForSeries(seriesId).map { l -> l.map { it.toModel() } }
    fun movieProgress(movieId: Int): Flow<WatchProgress?> = dao.progressForMovie(movieId).map { it?.toModel() }
    suspend fun progressFor(key: String): WatchProgress? = dao.progress(key)?.toModel()

    suspend fun saveEpisodeProgress(seriesId: Int, episodeId: String, positionMs: Long, durationMs: Long) =
        save(ProgressEntity(WatchProgress.episodeKey(seriesId, episodeId), seriesId, null, episodeId, positionMs, durationMs, System.currentTimeMillis()))

    suspend fun saveMovieProgress(movieId: Int, positionMs: Long, durationMs: Long) =
        save(ProgressEntity(WatchProgress.movieKey(movieId), null, movieId, null, positionMs, durationMs, System.currentTimeMillis()))

    private suspend fun save(p: ProgressEntity) {
        if (p.durationMs <= 0) return
        val existing = dao.progress(p.key)
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

    fun favourites(kind: FavouriteKind): Flow<List<String>> = dao.favourites(kind.key).map { l -> l.map { it.itemId } }
    fun isFavourite(kind: FavouriteKind, id: String): Flow<Boolean> = dao.isFavourite(kind.key, id)

    /** Adds or removes a favourite; returns true when it is now a favourite. */
    suspend fun toggleFavourite(kind: FavouriteKind, id: String): Boolean {
        val now = dao.isFavouriteNow(kind.key, id)
        if (now) dao.removeFavourite(kind.key, id) else dao.addFavourite(FavouriteEntity(kind.key, id, System.currentTimeMillis()))
        return !now
    }
}

fun ProgressEntity.toModel() = WatchProgress(key, positionMs, durationMs, Instant.ofEpochMilli(updatedAt))
