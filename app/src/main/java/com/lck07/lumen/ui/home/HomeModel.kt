package com.lck07.lumen.ui.home

import android.content.res.Resources
import com.lck07.lumen.AppContainer
import com.lck07.lumen.R
import com.lck07.lumen.core.library.Library
import com.lck07.lumen.core.library.UpNext
import com.lck07.lumen.core.library.WatchProgress
import com.lck07.lumen.data.FavouriteKind
import com.lck07.lumen.data.Show
import com.lck07.lumen.data.db.ChannelEntity
import com.lck07.lumen.data.db.MovieEntity
import com.lck07.lumen.data.db.SeriesEntity
import com.lck07.lumen.ui.model.ChannelItem
import com.lck07.lumen.ui.model.EpisodeTileItem
import com.lck07.lumen.ui.model.PosterItem
import com.lck07.lumen.ui.model.Target
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private val Hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
fun hm(ms: Long): String = Hm.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun minutesLeft(p: WatchProgress): Int = ceil(p.remainingMs / 60_000.0).toInt().coerceAtLeast(1)

/** Channel tiles with what's on now. */
fun channelItems(channels: List<ChannelEntity>, onNow: Map<String, Show>, favourites: Set<String>, now: Long = System.currentTimeMillis()): List<ChannelItem> =
    channels.map { c ->
        val show = c.epgId?.let { onNow[it] }
        ChannelItem(
            c.id, c.number, c.name, c.logo, show?.title, show?.progress(now),
            show?.let { "${hm(it.start)}–${hm(it.stop)}" }, c.id.toString() in favourites,
        )
    }

fun SeriesEntity.poster(res: Resources, isNew: Boolean = false) = PosterItem(
    key = "s$id", title = name, caption = listOfNotNull(name, genre?.substringBefore(',')?.trim()).joinToString(" · "),
    image = cover, target = Target.SeriesDetail(id), isNew = isNew, favouriteKey = id.toString(),
)

fun MovieEntity.poster(res: Resources, isNew: Boolean = false) = PosterItem(
    key = "m$id", title = name,
    caption = listOfNotNull(name, rating?.takeIf { it > 0 }?.let { res.getString(R.string.rating, "%.1f".format(it)) }).joinToString(" · "),
    image = poster, target = Target.MovieDetail(id), isNew = isNew, favouriteKey = id.toString(),
)

/** Builds Home from the cache: the hero and rows update as progress, favourites and the guide change. */
class HomeModel(private val c: AppContainer, private val res: Resources) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<HomeState> = run {
        val dao = c.db.catalog()
        val continueFlow: Flow<Pair<List<UpNext>, List<WatchProgress>>> = c.library.progress.map { all ->
            // Fetch episode lists we don't have yet (e.g. after a reinstall) so the hero can resume.
            val client = c.account.client
            all.mapNotNull { it.seriesId }.distinct().take(6).forEach { id ->
                if (c.catalog.cachedSeriesDetail(id) == null && client != null) runCatching { c.catalog.refreshSeries(client, id) }
            }
            c.library.upNext(all) to Library.moviesInProgress(all)
        }
        val favChannels: Flow<List<ChannelItem>> = c.library.favourites(FavouriteKind.Channel).flatMapLatest { ids ->
            val intIds = ids.mapNotNull { it.toIntOrNull() }
            if (intIds.isEmpty()) flowOf(emptyList())
            else combine(dao.channelsById(intIds), c.guide.onNow) { ch, now ->
                val order = intIds.withIndex().associate { it.value to it.index }
                channelItems(ch.sortedBy { order[it.id] ?: 0 }, now, ids.toSet())
            }
        }
        val recentSeries = dao.recentlyUpdatedSeries(24)
        val recentMovies = dao.recentMovies(24)
        val moviesInProgress: Flow<List<MovieEntity>> = continueFlow.flatMapLatest { (_, movies) ->
            val ids = movies.mapNotNull { it.movieId }
            if (ids.isEmpty()) flowOf(emptyList()) else dao.moviesById(ids)
        }
        combine(continueFlow, moviesInProgress, favChannels, recentSeries, recentMovies) { (upNext, movieProgress), movies, channels, newSeries, newMovies ->
            build(upNext, movieProgress, movies, channels, newSeries, newMovies)
        }
    }

    private suspend fun build(
        upNext: List<UpNext>,
        movieProgress: List<WatchProgress>,
        movies: List<MovieEntity>,
        channels: List<ChannelItem>,
        newSeries: List<SeriesEntity>,
        newMovies: List<MovieEntity>,
    ): HomeState {
        val first = upNext.firstOrNull()
        val heroSeries = first?.let { c.db.catalog().series(it.detail.series.id) }
        val hero: HomeHero? = when {
            first != null -> {
                val ep = first.episode
                val s = first.detail.series
                HomeHero(
                    eyebrow = res.getString(if (first.resumes) R.string.eyebrow_continue_watching else R.string.eyebrow_up_next),
                    title = s.name,
                    backdrop = s.backdrops.firstOrNull() ?: heroSeries?.backdrop ?: s.cover,
                    episodeLine = res.getString(R.string.season_episode_title, ep.season, ep.number, ep.title),
                    genre = s.genre?.substringBefore(',')?.trim(),
                    rating = null,
                    progress = first.progress?.takeIf { first.resumes }?.fraction,
                    minutesLeft = first.progress?.takeIf { first.resumes }?.let(::minutesLeft),
                    resumes = first.resumes,
                    play = Target.PlayEpisode(s.id, ep.id),
                    startOver = if (first.resumes) Target.PlayEpisode(s.id, ep.id, startOver = true) else null,
                    episodes = Target.SeriesDetail(s.id),
                )
            }
            newSeries.isNotEmpty() -> {
                val s = newSeries.first()
                HomeHero(
                    eyebrow = res.getString(R.string.eyebrow_new_series), title = s.name, backdrop = s.backdrop ?: s.cover,
                    episodeLine = null, genre = s.genre?.substringBefore(',')?.trim(), rating = null, progress = null, minutesLeft = null,
                    resumes = false, play = Target.PlaySeries(s.id), startOver = null, episodes = Target.SeriesDetail(s.id),
                )
            }
            else -> null
        }

        val continueTiles = upNext.drop(1).map { u ->
            val s = u.detail.series
            val e = u.episode
            val sub = if (u.resumes && u.progress != null)
                "${res.getString(R.string.season_episode, e.season, e.number)} · ${res.getString(R.string.minutes_left, minutesLeft(u.progress!!))}"
            else res.getString(R.string.season_episode_title, e.season, e.number, e.title)
            EpisodeTileItem(
                key = "cw-s${s.id}", title = s.name, subtitle = sub, image = e.image ?: s.backdrops.firstOrNull() ?: s.cover, seed = s.name,
                target = Target.PlayEpisode(s.id, e.id), progress = u.progress?.takeIf { u.resumes }?.fraction,
            )
        } + movieProgress.mapNotNull { p ->
            val m = movies.firstOrNull { it.id == p.movieId } ?: return@mapNotNull null
            EpisodeTileItem(
                key = "cw-m${m.id}", title = m.name, subtitle = res.getString(R.string.minutes_left, minutesLeft(p)), image = m.poster,
                seed = m.name, target = Target.PlayMovie(m.id), progress = p.fraction,
            )
        }

        val upNextTiles = first?.let { u ->
            val all = u.detail.episodes.values.flatten()
            val i = all.indexOfFirst { it.id == u.episode.id }
            all.drop(i + 1).take(12).map { e ->
                EpisodeTileItem(
                    key = "un-${e.id}", title = "${e.number}. ${e.title}",
                    subtitle = e.durationSeconds?.let { res.getString(R.string.episode_meta, e.season, (it + 59) / 60) } ?: res.getString(R.string.season_n, e.season),
                    image = e.image, seed = "${u.detail.series.name} ${e.number}", target = Target.PlayEpisode(u.detail.series.id, e.id),
                )
            }
        }.orEmpty()

        val weekAgo = System.currentTimeMillis() / 1000 - 7 * 86_400
        return HomeState(
            hero = hero,
            continueWatching = continueTiles,
            upNextTitle = first?.detail?.series?.name,
            upNext = upNextTiles,
            favouriteChannels = channels,
            newEpisodes = newSeries.filter { it.id != first?.detail?.series?.id }.map { it.poster(res, isNew = (it.lastModified ?: 0) > weekAgo) },
            recentMovies = newMovies.map { it.poster(res, isNew = (it.added ?: 0) > weekAgo) },
            loaded = true,
        )
    }
}
