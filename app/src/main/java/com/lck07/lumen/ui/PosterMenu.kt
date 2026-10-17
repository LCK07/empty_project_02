package com.lck07.lumen.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lck07.lumen.R
import com.lck07.lumen.container
import com.lck07.lumen.data.FavouriteKind
import com.lck07.lumen.design.Glass
import com.lck07.lumen.design.ListRow
import com.lck07.lumen.design.LumenRadii
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.Txt
import com.lck07.lumen.ui.model.PosterItem
import com.lck07.lumen.ui.model.Target
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

/** The poster whose long-press menu is open, if any. One state for the whole app. */
@Stable
class PosterMenuState {
    var item by mutableStateOf<PosterItem?>(null)
        private set

    fun open(p: PosterItem) { if (p.target is Target.SeriesDetail || p.target is Target.MovieDetail) item = p }
    fun close() { item = null }
}

/** Long-press on a movie or series poster: add to / remove from Favourites and Watchlist. [restoreFocus] runs after it closes. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PosterMenuHost(state: PosterMenuState, restoreFocus: () -> Unit) {
    val scope = rememberCoroutineScope()
    val item = state.item ?: return
    val c = LocalContext.current.container
    val res = LocalContext.current.resources
    val toaster = LocalToaster.current
    val (kind, id) = when (val t = item.target) {
        is Target.SeriesDetail -> FavouriteKind.Series to t.seriesId.toString()
        is Target.MovieDetail -> FavouriteKind.Movie to t.movieId.toString()
        else -> return
    }
    val favourite by remember(kind, id) { c.library.isFavourite(kind, id) }.collectAsState(initial = false)
    val onWatchlist by remember(kind, id) { c.library.isInWatchlist(kind, id) }.collectAsState(initial = false)
    val first = remember { FocusRequester() }
    fun close() { state.close(); scope.launch { awaitFrame(); runCatching { restoreFocus() } } }
    BackHandler(onBack = ::close)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Glass(LumenRadii.Banner, Modifier.width(340.dp).testTag("poster:menu"), strong = true) {
            Column(
                Modifier.focusProperties { exit = { FocusRequester.Cancel } }.focusGroup().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Txt(item.title, LumenType.Headline, Modifier.padding(start = 4.dp, bottom = 6.dp), maxLines = 2)
                ListRow(
                    stringResource(if (favourite) R.string.menu_remove_favourite else R.string.menu_add_favourite), null,
                    {
                        scope.launch {
                            val now = c.library.toggleFavourite(kind, id)
                            toaster.show(res.getString(if (now) R.string.added_to_favourites else R.string.removed_from_favourites))
                        }
                        close()
                    },
                    trailingIcon = null, focusRequester = first, modifier = Modifier.testTag("poster:menu:favourite"),
                )
                ListRow(
                    stringResource(if (onWatchlist) R.string.menu_remove_watchlist else R.string.menu_add_watchlist), null,
                    {
                        scope.launch {
                            val now = c.library.toggleWatchlist(kind, id)
                            toaster.show(res.getString(if (now) R.string.added_to_watchlist else R.string.removed_from_watchlist))
                        }
                        close()
                    },
                    trailingIcon = null, modifier = Modifier.testTag("poster:menu:watchlist"),
                )
            }
        }
    }
    LaunchedEffect(Unit) { awaitFrame(); runCatching { first.requestFocus() } }
}
