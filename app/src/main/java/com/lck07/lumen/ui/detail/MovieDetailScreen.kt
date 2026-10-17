package com.lck07.lumen.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lck07.lumen.R
import com.lck07.lumen.container
import com.lck07.lumen.data.FavouriteKind
import com.lck07.lumen.design.ButtonRow
import com.lck07.lumen.design.LumenButton
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.LumenSpacing
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.LumenType.tabular
import com.lck07.lumen.design.MetaLine
import com.lck07.lumen.design.ProgressBar
import com.lck07.lumen.design.Txt
import com.lck07.lumen.ui.LocalToaster
import com.lck07.lumen.ui.LocalScreenActive
import com.lck07.lumen.ui.home.DriftingArtwork
import com.lck07.lumen.ui.home.minutesLeft
import com.lck07.lumen.ui.model.Target
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Movie detail: backdrop, title, year · genre · length, plot, Resume or Play, Start Over, Favourite. */
@Composable
fun MovieDetailScreen(movieId: Int, onOpen: (Target) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val res = context.resources
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val dao = c.db.catalog()
    val data by remember(movieId) {
        combine(dao.movieFlow(movieId), dao.movieDetail(movieId), c.library.movieProgress(movieId), c.library.isFavourite(FavouriteKind.Movie, movieId.toString())) { m, d, p, f ->
            Quad(m, d, p, f)
        }
    }.collectAsState(initial = null)
    val primary = remember { FocusRequester() }
    val onWatchlist by remember(movieId) { c.library.isInWatchlist(FavouriteKind.Movie, movieId.toString()) }.collectAsState(initial = false)

    LaunchedEffect(movieId) {
        val client = c.account.client ?: return@LaunchedEffect
        runCatching { c.catalog.refreshMovie(client, movieId) }
    }

    val (movie, detail, progress, favourite) = data ?: return
    movie ?: return
    // The main button takes focus the first time this screen is on top (it may open hidden,
    // underneath a player started from Home).
    val active = LocalScreenActive.current
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(active, movie.id) { if (active && !focusedOnce) { awaitFrame(); focusedOnce = runCatching { primary.requestFocus() }.isSuccess } }
    val resumes = progress?.inProgress == true
    val minutes = detail?.durationSecs?.let { (it + 59) / 60 }

    Box(Modifier.fillMaxSize().testTag("movie-detail")) {
        DriftingArtwork(detail?.backdrop ?: movie.poster, movie.name)
        Column(
            Modifier.padding(start = LumenSpacing.SafeX, top = 165.dp).width(500.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Txt(movie.name, if (movie.name.length > 18) LumenType.HeroSmall else LumenType.Hero, maxLines = 2)
            MetaLine(
                listOfNotNull(
                    detail?.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) },
                    detail?.genre?.substringBefore(',')?.trim(),
                    minutes?.let { res.getString(R.string.minutes, it) },
                    movie.rating?.takeIf { it > 0 }?.let { res.getString(R.string.rating, "%.1f".format(it)) },
                ),
            )
            detail?.plot?.let { Txt(it, LumenType.BodyLong, color = LumenColors.Label2, maxLines = 3, modifier = Modifier.width(450.dp)) }
            listOfNotNull(detail?.director, detail?.cast).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                Txt(it, LumenType.Caption1, color = LumenColors.Label3, maxLines = 1, modifier = Modifier.width(450.dp))
            }
            if (resumes && progress != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ProgressBar(progress.fraction, Modifier.width(110.dp))
                Txt(stringResource(R.string.minutes_left, minutesLeft(progress)), LumenType.Caption1.tabular(), color = LumenColors.Label2)
            }
            // The buttons may run wider than the text column, so labels are never cut off.
            ButtonRow(Modifier.padding(top = 7.dp).wrapContentWidth(Alignment.Start, unbounded = true)) {
                LumenButton(
                    stringResource(if (resumes) R.string.action_resume else R.string.action_play),
                    { onOpen(Target.PlayMovie(movieId)) }, icon = LumenIcons.Play, focusRequester = primary,
                    modifier = Modifier.testTag("detail:play"),
                )
                if (resumes) LumenButton(stringResource(R.string.action_start_over), { onOpen(Target.PlayMovie(movieId, startOver = true)) }, icon = LumenIcons.Restart)
                FavouriteButton(favourite) {
                    scope.launch {
                        val now = c.library.toggleFavourite(FavouriteKind.Movie, movieId.toString())
                        toaster.show(res.getString(if (now) R.string.added_to_favourites else R.string.removed_from_favourites))
                    }
                }
                WatchlistButton(onWatchlist) {
                    scope.launch {
                        val now = c.library.toggleWatchlist(FavouriteKind.Movie, movieId.toString())
                        toaster.show(res.getString(if (now) R.string.added_to_watchlist else R.string.removed_from_watchlist))
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
