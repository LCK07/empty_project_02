package com.lck07.lumen.data

import androidx.room.withTransaction
import com.lck07.lumen.core.LanguageTags
import com.lck07.lumen.core.model.Episode
import com.lck07.lumen.core.model.LiveChannel
import com.lck07.lumen.core.model.Movie
import com.lck07.lumen.core.model.Series
import com.lck07.lumen.core.model.SeriesDetail
import com.lck07.lumen.core.xtream.XtreamClient
import com.lck07.lumen.data.db.CategoryEntity
import com.lck07.lumen.data.db.ChannelEntity
import com.lck07.lumen.data.db.EpisodeEntity
import com.lck07.lumen.data.db.LumenDatabase
import com.lck07.lumen.data.db.MovieDetailEntity
import com.lck07.lumen.data.db.MovieEntity
import com.lck07.lumen.data.db.SeriesDetailEntity
import com.lck07.lumen.data.db.SeriesEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.time.Instant

/** Progress of a catalogue import, for the setup screen. */
data class ImportProgress(
    val step: Step,
    val channels: Int = 0,
    val movies: Int = 0,
    val series: Int = 0,
) {
    enum class Step { Categories, Channels, Movies, Series, Done }
}

/**
 * The cached catalogue: imports it from the server in the background and serves it from Room,
 * so screens show cached data at once and never wait for the network.
 */
class CatalogRepository(private val db: LumenDatabase) {
    private val dao = db.catalog()
    private val importLock = Mutex()

    /** Imports categories, channels, films and series. Older rows are removed only when everything arrived. */
    suspend fun import(client: XtreamClient, onProgress: (ImportProgress) -> Unit = {}): ImportProgress = importLock.withLock {
        withContext(Dispatchers.IO) {
            val gen = (System.currentTimeMillis() / 1000).toInt()
            var p = ImportProgress(ImportProgress.Step.Categories)
            onProgress(p)
            val live = client.liveCategories()
            val movie = client.movieCategories()
            val series = client.seriesCategories()
            dao.upsertCategories(
                live.mapIndexed { i, c -> CategoryEntity(TYPE_LIVE, c.id, c.name, i, gen, LanguageTags.fromText(c.name)) } +
                    movie.mapIndexed { i, c -> CategoryEntity(TYPE_MOVIE, c.id, c.name, i, gen, LanguageTags.fromText(c.name)) } +
                    series.mapIndexed { i, c -> CategoryEntity(TYPE_SERIES, c.id, c.name, i, gen, LanguageTags.fromText(c.name)) },
            )
            val movieCats = movie.associate { it.id to it.name }
            val seriesCats = series.associate { it.id to it.name }

            p = p.copy(step = ImportProgress.Step.Channels); onProgress(p)
            var position = 0
            client.streamLiveChannels { batch ->
                dao.upsertChannels(batch.map { it.toEntity(position++, gen) })
                p = p.copy(channels = position); onProgress(p)
            }

            p = p.copy(step = ImportProgress.Step.Movies); onProgress(p)
            position = 0
            client.streamMovies { batch ->
                dao.upsertMovies(batch.map { it.toEntity(position++, gen, LanguageTags.tag(it.categoryId?.let(movieCats::get), it.name)) })
                p = p.copy(movies = position); onProgress(p)
            }

            p = p.copy(step = ImportProgress.Step.Series); onProgress(p)
            position = 0
            client.streamSeries { batch ->
                dao.upsertSeries(batch.map { it.toEntity(position++, gen, LanguageTags.tag(it.categoryId?.let(seriesCats::get), it.name)) })
                p = p.copy(series = position); onProgress(p)
            }

            dao.pruneCategories(TYPE_LIVE, gen); dao.pruneCategories(TYPE_MOVIE, gen); dao.pruneCategories(TYPE_SERIES, gen)
            dao.pruneChannels(gen); dao.pruneMovies(gen); dao.pruneSeries(gen)
            p = p.copy(step = ImportProgress.Step.Done); onProgress(p)
            p
        }
    }

    /** Tags categories, films and series imported before language tags existed. */
    suspend fun tagLanguages() = withContext(Dispatchers.IO) {
        val movieCats = HashMap<String, String>()
        val seriesCats = HashMap<String, String>()
        for ((type, names) in listOf(TYPE_LIVE to null, TYPE_MOVIE to movieCats, TYPE_SERIES to seriesCats)) {
            val cats = dao.categoriesNow(type)
            db.withTransaction { cats.forEach { dao.setCategoryLanguage(type, it.id, LanguageTags.fromText(it.name)) } }
            names?.putAll(cats.associate { it.id to it.name })
        }
        var after = Int.MIN_VALUE
        while (true) {
            val batch = dao.movieNames(after, BATCH).ifEmpty { break }
            db.withTransaction { batch.forEach { dao.setMovieLanguage(it.id, LanguageTags.tag(it.categoryId?.let(movieCats::get), it.name)) } }
            after = batch.last().id
        }
        after = Int.MIN_VALUE
        while (true) {
            val batch = dao.seriesNames(after, BATCH).ifEmpty { break }
            db.withTransaction { batch.forEach { dao.setSeriesLanguage(it.id, LanguageTags.tag(it.categoryId?.let(seriesCats::get), it.name)) } }
            after = batch.last().id
        }
    }

    /** Fetches a series' seasons and episodes (and better artwork) and caches them. */
    suspend fun refreshSeries(client: XtreamClient, seriesId: Int): SeriesDetail? = withContext(Dispatchers.IO) {
        val base = dao.series(seriesId)?.toModel() ?: return@withContext null
        val detail = client.seriesDetail(base)
        dao.replaceSeriesDetail(
            SeriesDetailEntity(seriesId, detail.series.plot, detail.series.genre, detail.series.backdrops.firstOrNull(), System.currentTimeMillis()),
            detail.episodes.values.flatten().map { it.toEntity(seriesId) },
        )
        detail
    }

    suspend fun refreshMovie(client: XtreamClient, movieId: Int) = withContext(Dispatchers.IO) {
        val movie = dao.movie(movieId)?.toModel() ?: return@withContext
        val d = client.movieDetail(movie)
        dao.upsertMovieDetail(
            MovieDetailEntity(
                movieId, d.plot, d.cast, d.director, d.genre, d.releaseDate, d.durationSeconds,
                d.backdrops.firstOrNull(), d.movie.extension, System.currentTimeMillis(),
            ),
        )
    }

    /** A series with its episodes from the cache, or null when they were never fetched. */
    suspend fun cachedSeriesDetail(seriesId: Int): SeriesDetail? {
        val s = dao.series(seriesId) ?: return null
        val episodes = dao.episodesNow(seriesId)
        if (episodes.isEmpty()) return null
        val extra = dao.seriesDetailNow(seriesId)
        return detailOf(s, extra, episodes)
    }

    companion object {
        const val TYPE_LIVE = "live"
        const val TYPE_MOVIE = "movie"
        const val TYPE_SERIES = "series"
        private const val BATCH = 2000

        /** Lower case without accents, for search ("Café" matches "cafe"). */
        fun searchKey(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().trim()

        fun detailOf(s: SeriesEntity, extra: SeriesDetailEntity?, episodes: List<EpisodeEntity>): SeriesDetail {
            val bySeason = episodes.map { it.toModel() }.groupBy { it.season }.mapValues { (_, l) -> l.sortedBy { it.number } }.toSortedMap()
            val series = s.toModel().let { m ->
                if (extra == null) m else m.copy(
                    plot = extra.plot ?: m.plot, genre = extra.genre ?: m.genre,
                    backdrops = listOfNotNull(extra.backdrop).ifEmpty { m.backdrops },
                )
            }
            return SeriesDetail(series, bySeason.keys.toList(), bySeason)
        }
    }
}

fun LiveChannel.toEntity(position: Int, gen: Int) = ChannelEntity(
    id, number, name, CatalogRepository.searchKey(name), logo, epgId, categoryId, archiveDays, position, gen,
)

fun ChannelEntity.toModel() = LiveChannel(id, number, name, logo, epgId, categoryId, archiveDays)

fun Movie.toEntity(position: Int, gen: Int, language: String? = null) = MovieEntity(
    id, name, CatalogRepository.searchKey(name), poster, rating, categoryId, added?.epochSecond, extension, position, gen, language,
)

fun MovieEntity.toModel() = Movie(id, name, poster, rating, categoryId, added?.let(Instant::ofEpochSecond), extension)

fun Series.toEntity(position: Int, gen: Int, language: String? = null) = SeriesEntity(
    id, name, CatalogRepository.searchKey(name), cover, plot, genre, releaseDate, rating, categoryId,
    lastModified?.epochSecond, backdrops.firstOrNull(), position, gen, language,
)

fun SeriesEntity.toModel() = Series(
    id, name, cover, plot, genre, releaseDate, rating, categoryId, lastModified?.let(Instant::ofEpochSecond), listOfNotNull(backdrop),
)

fun Episode.toEntity(seriesId: Int) = EpisodeEntity(id, seriesId, season, number, title, extension, durationSeconds, plot, image)

fun EpisodeEntity.toModel() = Episode(id, season, number, title, extension, durationSecs, plot, image)
