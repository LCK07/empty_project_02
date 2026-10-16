package com.lck07.lumen.ui.detail

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lck07.lumen.AppContainer
import com.lck07.lumen.R
import com.lck07.lumen.container
import com.lck07.lumen.core.library.Library
import com.lck07.lumen.core.library.WatchProgress
import com.lck07.lumen.core.model.Episode
import com.lck07.lumen.core.model.SeriesDetail
import com.lck07.lumen.data.CatalogRepository
import com.lck07.lumen.data.FavouriteKind
import com.lck07.lumen.data.explain
import com.lck07.lumen.design.ButtonRow
import com.lck07.lumen.design.EpisodeTile
import com.lck07.lumen.design.LumenButton
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.LumenSpacing
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.MessageState
import com.lck07.lumen.design.MetaLine
import com.lck07.lumen.design.SegmentedControl
import com.lck07.lumen.design.Spinner
import com.lck07.lumen.design.TvRow
import com.lck07.lumen.design.Txt
import com.lck07.lumen.ui.LocalToaster
import com.lck07.lumen.ui.LocalScreenActive
import com.lck07.lumen.ui.home.DriftingArtwork
import com.lck07.lumen.ui.home.minutesLeft
import com.lck07.lumen.ui.model.EpisodeTileItem
import com.lck07.lumen.ui.model.Target
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Immutable
data class SeriesDetailState(
    val series: com.lck07.lumen.core.model.Series,
    /** Null until the episode list has been fetched once. */
    val detail: SeriesDetail?,
    val backdrop: String?,
    val year: String?,
    val rating: String?,
    /** The episode the main button plays, and whether it resumes. */
    val primary: Episode?,
    val resumes: Boolean,
    val started: Boolean,
    val progress: Map<String, WatchProgress>,
    /** Episodes before the one up next count as watched. */
    val watchedBefore: Int,
    val favourite: Boolean,
)

fun seriesDetailState(c: AppContainer, seriesId: Int): Flow<SeriesDetailState?> {
    val dao = c.db.catalog()
    return combine(
        dao.seriesFlow(seriesId), dao.seriesDetail(seriesId), dao.episodes(seriesId),
        c.library.seriesProgress(seriesId), c.library.isFavourite(FavouriteKind.Series, seriesId.toString()),
    ) { s, extra, episodes, progress, fav ->
        if (s == null) return@combine null
        val merged = CatalogRepository.detailOf(s, extra, episodes)
        val detail = merged.takeIf { episodes.isNotEmpty() }
        val up = detail?.let { d -> Library.upNext(progress) { d }.firstOrNull() }
        val all = detail?.episodes?.values?.flatten().orEmpty()
        val primary = up?.episode ?: all.firstOrNull()
        val primaryIndex = primary?.let { p -> all.indexOfFirst { it.id == p.id } } ?: 0
        SeriesDetailState(
            series = merged.series,
            detail = detail,
            backdrop = extra?.backdrop ?: s.backdrop ?: s.cover,
            year = s.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) },
            rating = s.rating?.takeIf { it > 0 }?.let { "%.1f".format(it) },
            primary = primary,
            resumes = up?.resumes == true,
            started = progress.isNotEmpty(),
            progress = progress.associateBy { it.episodeId.orEmpty() },
            watchedBefore = if (progress.isEmpty()) 0 else primaryIndex,
            favourite = fav,
        )
    }
}

/**
 * Series detail: full-bleed backdrop, title, year · genre · seasons, plot, Resume S2 E5 (or
 * Play), Start Over and Favourite; a season picker and the season's episodes underneath.
 */
@Composable
fun SeriesDetailScreen(seriesId: Int, onOpen: (Target) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val res = context.resources
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val state by remember(seriesId) { seriesDetailState(c, seriesId) }.collectAsState(initial = null)
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    var attempt by remember { mutableStateOf(0) }
    val primaryButton = remember { FocusRequester() }

    // Cached data shows at once; the episode list is refreshed from the server behind it.
    LaunchedEffect(seriesId, attempt) {
        val client = c.account.client ?: return@LaunchedEffect
        loadError = null
        try { c.catalog.refreshSeries(client, seriesId) } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            loadError = e
        }
    }

    val s = state ?: return
    val series = s.series
    val name = series.name
    var season by rememberSaveable(seriesId) { mutableStateOf<Int?>(null) }
    val seasons = s.detail?.seasons.orEmpty()
    val shownSeason = season?.takeIf { it in seasons } ?: s.primary?.season ?: seasons.firstOrNull()

    // The main button takes focus the first time this screen is on top (it may open hidden,
    // underneath a player started from Home).
    val active = LocalScreenActive.current
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(active, state != null) { if (active && !focusedOnce) { awaitFrame(); focusedOnce = runCatching { primaryButton.requestFocus() }.isSuccess } }

    Box(Modifier.fillMaxSize().testTag("series-detail")) {
        DriftingArtwork(s.backdrop, name.ifEmpty { "series$seriesId" })
        Column(
            Modifier.padding(start = LumenSpacing.SafeX, top = 125.dp).width(500.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            val long = name.length > 18
            Txt(name, if (long) LumenType.HeroSmall else LumenType.Hero, maxLines = 2)
            MetaLine(
                listOfNotNull(
                    s.year, series.genre?.substringBefore(',')?.trim(),
                    seasons.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.seasons_count, it, it) },
                    s.rating?.let { stringResource(R.string.rating, it) },
                ),
            )
            series.plot?.let { Txt(it, LumenType.BodyLong, color = LumenColors.Label2, maxLines = if (long) 2 else 3, modifier = Modifier.width(450.dp)) }
            ButtonRow(Modifier.padding(top = 7.dp)) {
                val p = s.primary
                val label = when {
                    p == null -> stringResource(R.string.action_play)
                    s.resumes -> stringResource(R.string.action_resume_se, p.season, p.number)
                    s.started -> stringResource(R.string.action_play_se, p.season, p.number)
                    else -> stringResource(R.string.action_play)
                }
                LumenButton(
                    label,
                    { if (p != null) onOpen(Target.PlayEpisode(seriesId, p.id)) else onOpen(Target.PlaySeries(seriesId)) },
                    icon = LumenIcons.Play, focusRequester = primaryButton, modifier = Modifier.testTag("detail:play"),
                )
                if (p != null) LumenButton(
                    stringResource(R.string.action_start_over),
                    { onOpen(Target.PlayEpisode(seriesId, p.id, startOver = true)) }, icon = LumenIcons.Restart,
                )
                FavouriteButton(s.favourite) {
                    scope.launch {
                        val now = c.library.toggleFavourite(FavouriteKind.Series, seriesId.toString())
                        toaster.show(res.getString(if (now) R.string.added_to_favourites else R.string.removed_from_favourites))
                    }
                }
            }
        }

        val detail = s.detail
        when {
            detail != null && shownSeason != null -> {
                if (seasons.size > 1 || seasons.firstOrNull() != 1) SegmentedControl(
                    seasons, shownSeason, { res.getString(R.string.season_n, it) }, { season = it },
                    Modifier.padding(start = LumenSpacing.SafeX).offset(y = 360.dp).testTag("seasons"),
                )
                val all = detail.episodes.values.flatten()
                val items = detail.episodes[shownSeason].orEmpty().map { e -> episodeItem(res, seriesId, name, e, s, all) }
                key(shownSeason) {
                    TvRow(null, items, { it.key }, Modifier.offset(y = 395.dp), captionSpace = 0.dp, tag = "row:episodes") { item, slot ->
                        EpisodeTile(item, { onOpen(item.target) }, slot = slot, small = true)
                    }
                }
            }
            loadError != null -> Box(Modifier.fillMaxWidth().offset(y = 380.dp), contentAlignment = Alignment.Center) {
                MessageState(
                    res.explain(loadError!!), null, action = stringResource(R.string.action_retry), onAction = { attempt++ },
                    modifier = Modifier.padding(horizontal = LumenSpacing.SafeX),
                )
            }
            else -> Box(Modifier.fillMaxWidth().offset(y = 400.dp).height(100.dp), contentAlignment = Alignment.Center) { Spinner() }
        }
    }
}

private fun episodeItem(res: Resources, seriesId: Int, seriesName: String, e: Episode, s: SeriesDetailState, all: List<Episode>): EpisodeTileItem {
    val p = s.progress[e.id]
    val index = all.indexOfFirst { it.id == e.id }
    val watched = p?.finished == true || (index in 0 until s.watchedBefore && p?.inProgress != true)
    val minutes = e.durationSeconds?.let { (it + 59) / 60 }
    val sub = when {
        p?.inProgress == true -> res.getString(R.string.minutes_left, minutesLeft(p))
        minutes != null -> res.getString(R.string.minutes, minutes)
        else -> res.getString(R.string.episode_n, e.number)
    }
    return EpisodeTileItem(
        key = "ep-${e.id}", title = "${e.number}. ${e.title}", subtitle = sub, image = e.image,
        seed = "$seriesName ${e.season} ${e.number}", target = Target.PlayEpisode(seriesId, e.id),
        progress = p?.takeIf { it.inProgress }?.fraction, watched = watched,
    )
}

/** The round heart button: filled when the title is a favourite (the "selected" state). */
@Composable
fun FavouriteButton(on: Boolean, onClick: () -> Unit) {
    LumenButton(
        null, onClick, icon = if (on) LumenIcons.HeartFilled else LumenIcons.Heart,
        label = stringResource(if (on) R.string.favourite_on else R.string.favourite_off), selected = on,
        modifier = Modifier.testTag("detail:favourite"),
    )
}
