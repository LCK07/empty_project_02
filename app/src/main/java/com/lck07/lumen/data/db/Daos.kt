package com.lck07.lumen.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Upsert suspend fun upsertCategories(list: List<CategoryEntity>)
    @Upsert suspend fun upsertChannels(list: List<ChannelEntity>)
    @Upsert suspend fun upsertMovies(list: List<MovieEntity>)
    @Upsert suspend fun upsertSeries(list: List<SeriesEntity>)

    @Query("DELETE FROM category WHERE type = :type AND gen < :gen") suspend fun pruneCategories(type: String, gen: Int)
    @Query("DELETE FROM channel WHERE gen < :gen") suspend fun pruneChannels(gen: Int)
    @Query("DELETE FROM movie WHERE gen < :gen") suspend fun pruneMovies(gen: Int)
    @Query("DELETE FROM series WHERE gen < :gen") suspend fun pruneSeries(gen: Int)

    @Query("SELECT * FROM category WHERE type = :type ORDER BY position") fun categories(type: String): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM category WHERE type = :type ORDER BY position") suspend fun categoriesNow(type: String): List<CategoryEntity>

    // Channels
    @Query("SELECT * FROM channel ORDER BY position") fun channels(): Flow<List<ChannelEntity>>
    @Query("SELECT * FROM channel ORDER BY position") suspend fun channelsNow(): List<ChannelEntity>
    @Query("SELECT * FROM channel WHERE categoryId = :categoryId ORDER BY position LIMIT :limit") fun channelsIn(categoryId: String, limit: Int): Flow<List<ChannelEntity>>
    @Query("SELECT * FROM channel WHERE id = :id") suspend fun channel(id: Int): ChannelEntity?
    @Query("SELECT * FROM channel WHERE categoryId = :categoryId ORDER BY position") suspend fun channelsInNow(categoryId: String): List<ChannelEntity>
    @Query("SELECT * FROM channel WHERE id IN (:ids)") suspend fun channelsByIdNow(ids: List<Int>): List<ChannelEntity>
    @Query("SELECT * FROM channel ORDER BY position LIMIT 1") suspend fun firstChannel(): ChannelEntity?
    @Query("SELECT * FROM channel WHERE id IN (:ids)") fun channelsById(ids: List<Int>): Flow<List<ChannelEntity>>
    @Query("SELECT DISTINCT epgId FROM channel WHERE epgId IS NOT NULL") suspend fun epgIds(): List<String>
    @Query("SELECT COUNT(*) FROM channel") fun channelCount(): Flow<Int>

    // Movies
    @Query("SELECT * FROM movie WHERE categoryId = :categoryId ORDER BY position LIMIT :limit") fun moviesIn(categoryId: String, limit: Int): Flow<List<MovieEntity>>
    @Query("SELECT * FROM movie ORDER BY added DESC, id DESC LIMIT :limit") fun recentMovies(limit: Int): Flow<List<MovieEntity>>
    @Query("SELECT * FROM movie WHERE id = :id") suspend fun movie(id: Int): MovieEntity?
    @Query("SELECT * FROM movie WHERE id = :id") fun movieFlow(id: Int): Flow<MovieEntity?>
    @Query("SELECT * FROM movie WHERE id IN (:ids)") fun moviesById(ids: List<Int>): Flow<List<MovieEntity>>
    @Query("SELECT COUNT(*) FROM movie") fun movieCount(): Flow<Int>
    @Query("SELECT * FROM movie_detail WHERE movieId = :id") fun movieDetail(id: Int): Flow<MovieDetailEntity?>
    @Query("SELECT * FROM movie_detail WHERE movieId = :id") suspend fun movieDetailNow(id: Int): MovieDetailEntity?
    @Upsert suspend fun upsertMovieDetail(detail: MovieDetailEntity)

    // Series
    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY position LIMIT :limit") fun seriesIn(categoryId: String, limit: Int): Flow<List<SeriesEntity>>
    @Query("SELECT * FROM series ORDER BY lastModified DESC, id DESC LIMIT :limit") fun recentlyUpdatedSeries(limit: Int): Flow<List<SeriesEntity>>
    @Query("SELECT * FROM series ORDER BY id DESC LIMIT :limit") fun newestSeries(limit: Int): Flow<List<SeriesEntity>>
    @Query("SELECT * FROM series WHERE id = :id") suspend fun series(id: Int): SeriesEntity?
    @Query("SELECT * FROM series WHERE id = :id") fun seriesFlow(id: Int): Flow<SeriesEntity?>
    @Query("SELECT * FROM series WHERE id IN (:ids)") fun seriesById(ids: List<Int>): Flow<List<SeriesEntity>>
    @Query("SELECT * FROM series WHERE id IN (:ids)") suspend fun seriesByIdNow(ids: List<Int>): List<SeriesEntity>
    @Query("SELECT COUNT(*) FROM series") fun seriesCount(): Flow<Int>
    @Query("SELECT * FROM series_detail WHERE seriesId = :id") fun seriesDetail(id: Int): Flow<SeriesDetailEntity?>
    @Query("SELECT * FROM series_detail WHERE seriesId = :id") suspend fun seriesDetailNow(id: Int): SeriesDetailEntity?
    @Query("SELECT * FROM episode WHERE seriesId = :seriesId ORDER BY season, number") fun episodes(seriesId: Int): Flow<List<EpisodeEntity>>
    @Query("SELECT * FROM episode WHERE seriesId = :seriesId ORDER BY season, number") suspend fun episodesNow(seriesId: Int): List<EpisodeEntity>
    @Query("SELECT * FROM episode WHERE seriesId IN (:seriesIds) ORDER BY seriesId, season, number") suspend fun episodesFor(seriesIds: List<Int>): List<EpisodeEntity>
    @Query("SELECT * FROM episode WHERE id = :id") suspend fun episode(id: String): EpisodeEntity?

    @Upsert suspend fun upsertSeriesDetail(detail: SeriesDetailEntity)
    @Query("DELETE FROM episode WHERE seriesId = :seriesId") suspend fun deleteEpisodes(seriesId: Int)
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) suspend fun insertEpisodes(list: List<EpisodeEntity>)

    @Transaction
    suspend fun replaceSeriesDetail(detail: SeriesDetailEntity, episodes: List<EpisodeEntity>) {
        upsertSeriesDetail(detail)
        deleteEpisodes(detail.seriesId)
        insertEpisodes(episodes)
    }

    // Search: searchKey is the lower-cased name without accents.
    @Query("SELECT * FROM series WHERE searchKey LIKE '%' || :q || '%' ORDER BY (searchKey LIKE :q || '%') DESC, lastModified DESC LIMIT :limit")
    suspend fun searchSeries(q: String, limit: Int): List<SeriesEntity>
    @Query("SELECT * FROM movie WHERE searchKey LIKE '%' || :q || '%' ORDER BY (searchKey LIKE :q || '%') DESC, added DESC LIMIT :limit")
    suspend fun searchMovies(q: String, limit: Int): List<MovieEntity>
    @Query("SELECT * FROM channel WHERE searchKey LIKE '%' || :q || '%' ORDER BY (searchKey LIKE :q || '%') DESC, position LIMIT :limit")
    suspend fun searchChannels(q: String, limit: Int): List<ChannelEntity>
}

/** Which guide import is complete and shown. Rows of an import still running stay hidden. */
@Entity(tableName = "guide_state")
data class GuideStateEntity(@PrimaryKey val id: Int = 0, val activeGen: Int, val importedAt: Long)

@Dao
interface GuideDao {
    @Insert suspend fun insert(list: List<ProgrammeEntity>)
    /** For the streaming guide import, which runs on a background thread already. */
    @Insert fun insertBlocking(list: List<ProgrammeEntity>)
    @Query("SELECT * FROM guide_state WHERE id = 0") suspend fun state(): GuideStateEntity?
    @Query("SELECT * FROM guide_state WHERE id = 0") fun stateFlow(): Flow<GuideStateEntity?>
    @Upsert suspend fun setState(state: GuideStateEntity)
    @Query("DELETE FROM programme WHERE gen != :gen") suspend fun pruneOtherGens(gen: Int)
    @Query("DELETE FROM programme WHERE gen = :gen") suspend fun deleteGen(gen: Int)
    @Query("DELETE FROM programme WHERE stop < :before") suspend fun deleteEndedBefore(before: Long)

    @Transaction
    suspend fun activate(gen: Int, at: Long) {
        setState(GuideStateEntity(0, gen, at))
        pruneOtherGens(gen)
    }

    @Query(
        "SELECT channelKey, start, stop, title, description FROM programme " +
            "WHERE gen = (SELECT activeGen FROM guide_state WHERE id = 0) AND start <= :now AND stop > :now",
    )
    suspend fun onNow(now: Long): List<NowRow>

    @Query(
        "SELECT channelKey, start, stop, title, description FROM programme " +
            "WHERE gen = (SELECT activeGen FROM guide_state WHERE id = 0) AND channelKey = :key AND stop > :now ORDER BY start LIMIT :limit",
    )
    suspend fun upcoming(key: String, now: Long, limit: Int): List<NowRow>

    @Query(
        "SELECT channelKey, start, stop, title, description FROM programme " +
            "WHERE gen = (SELECT activeGen FROM guide_state WHERE id = 0) AND channelKey IN (:keys) AND stop > :from AND start < :to ORDER BY channelKey, start",
    )
    suspend fun range(keys: List<String>, from: Long, to: Long): List<NowRow>

    @Query("SELECT COUNT(*) FROM programme WHERE gen = (SELECT activeGen FROM guide_state WHERE id = 0)")
    fun count(): Flow<Int>
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM progress ORDER BY updatedAt DESC") fun progress(): Flow<List<ProgressEntity>>
    @Query("SELECT * FROM progress ORDER BY updatedAt DESC") suspend fun progressNow(): List<ProgressEntity>
    @Query("SELECT * FROM progress WHERE `key` = :key") suspend fun progress(key: String): ProgressEntity?
    @Query("SELECT * FROM progress WHERE seriesId = :seriesId") fun progressForSeries(seriesId: Int): Flow<List<ProgressEntity>>
    @Query("SELECT * FROM progress WHERE movieId = :movieId") fun progressForMovie(movieId: Int): Flow<ProgressEntity?>
    @Upsert suspend fun upsertProgress(p: ProgressEntity)
    @Query("UPDATE progress SET watchNextId = :id WHERE `key` = :key") suspend fun setWatchNextId(key: String, id: Long?)
    @Query("DELETE FROM progress WHERE `key` = :key") suspend fun deleteProgress(key: String)

    @Query("SELECT * FROM favourite WHERE kind = :kind ORDER BY addedAt DESC") fun favourites(kind: String): Flow<List<FavouriteEntity>>
    @Query("SELECT * FROM favourite ORDER BY addedAt DESC") fun allFavourites(): Flow<List<FavouriteEntity>>
    @Query("SELECT itemId FROM favourite WHERE kind = :kind ORDER BY addedAt DESC") suspend fun favouriteIdsNow(kind: String): List<String>
    @Query("SELECT COUNT(*) > 0 FROM favourite WHERE kind = :kind AND itemId = :itemId") fun isFavourite(kind: String, itemId: String): Flow<Boolean>
    @Query("SELECT COUNT(*) > 0 FROM favourite WHERE kind = :kind AND itemId = :itemId") suspend fun isFavouriteNow(kind: String, itemId: String): Boolean
    @Upsert suspend fun addFavourite(f: FavouriteEntity)
    @Query("DELETE FROM favourite WHERE kind = :kind AND itemId = :itemId") suspend fun removeFavourite(kind: String, itemId: String)
}
