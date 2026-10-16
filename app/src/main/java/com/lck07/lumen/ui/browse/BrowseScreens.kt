package com.lck07.lumen.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lck07.lumen.R
import com.lck07.lumen.container
import com.lck07.lumen.data.CatalogRepository
import com.lck07.lumen.data.FavouriteKind
import com.lck07.lumen.data.SettingsStore
import com.lck07.lumen.data.db.CategoryEntity
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.MessageState
import com.lck07.lumen.design.NoBringIntoView
import com.lck07.lumen.design.PosterTile
import com.lck07.lumen.design.RowSpacing
import com.lck07.lumen.design.TvRow
import com.lck07.lumen.design.revealOnFocus
import com.lck07.lumen.ui.TabChrome
import com.lck07.lumen.ui.home.poster
import com.lck07.lumen.ui.model.PosterItem
import com.lck07.lumen.ui.model.Target
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Rows load this many items first and grow by as many when focus nears the end. */
const val PAGE = 60

/** A page of rows under the tab bar; it reports whether it is scrolled to the top. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowsPage(
    chrome: TabChrome,
    modifier: Modifier = Modifier,
    list: LazyListState = rememberLazyListState(),
    top: Dp = 100.dp,
    tag: String,
    content: LazyListScope.(list: LazyListState) -> Unit,
) {
    val atTop by remember { derivedStateOf { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < 60 } }
    LaunchedEffect(atTop) { chrome.contentAtTop = atTop }
    LaunchedEffect(chrome.scrollToTop) { if (chrome.scrollToTop > 0) list.animateScrollToItem(0) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides NoBringIntoView) {
        LazyColumn(
            state = list,
            modifier = modifier.fillMaxSize().testTag(tag),
            verticalArrangement = RowSpacing,
            contentPadding = PaddingValues(top = top, bottom = 70.dp),
        ) { content(list) }
    }
}

/** A poster row whose items come from a paged query. */
@Composable
fun PagedPosterRow(
    title: String,
    index: Int,
    list: LazyListState,
    load: (limit: Int) -> Flow<List<PosterItem>>,
    onOpen: (Target) -> Unit,
    tag: String,
) {
    var limit by rememberSaveable { mutableIntStateOf(PAGE) }
    val items by remember(limit) { load(limit) }.collectAsState(initial = null)
    val shown = items ?: return
    if (shown.isEmpty()) { Box(Modifier.height(0.dp)); return }
    TvRow(
        title, shown, { it.key }, Modifier.revealOnFocus(list, index), tag = tag,
        onNearEnd = { if (shown.size >= limit) limit += PAGE },
    ) { item, slot -> PosterTile(item, { onOpen(item.target) }, slot = slot) }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun SeriesTab(chrome: TabChrome, onOpen: (Target) -> Unit) {
    val c = LocalContext.current.container
    val res = LocalContext.current.resources
    val dao = c.db.catalog()
    val categories by remember { visibleCategories(c, CatalogRepository.TYPE_SERIES) }.collectAsState(initial = null)
    val count by remember { dao.seriesCount() }.collectAsState(initial = -1)
    if (count == 0) { EmptyLibrary(); return }
    val cats = categories ?: return
    RowsPage(chrome, tag = "series") { list ->
        item(key = "mine") {
            PagedPosterRow(stringResource(R.string.row_your_series), 0, list, { limit ->
                combine(c.library.progress, c.library.favourites(FavouriteKind.Series)) { p, f ->
                    (p.mapNotNull { it.seriesId } + f.mapNotNull { it.toIntOrNull() }).distinct().take(limit)
                }.flatMapLatest { ids ->
                    if (ids.isEmpty()) flowOf(emptyList())
                    else dao.seriesById(ids).map { l -> val o = ids.withIndex().associate { it.value to it.index }; l.sortedBy { o[it.id] }.map { it.poster(res) } }
                }
            }, onOpen, "row:mine")
        }
        item(key = "updated") {
            PagedPosterRow(stringResource(R.string.row_recently_updated), 1, list, { limit -> dao.recentlyUpdatedSeries(limit).map { l -> l.map { it.poster(res) } } }, onOpen, "row:updated")
        }
        cats.forEachIndexed { i, cat ->
            item(key = "cat:${cat.id}") {
                PagedPosterRow(cat.name, i + 2, list, { limit -> dao.seriesIn(cat.id, limit).map { l -> l.map { it.poster(res) } } }, onOpen, "row:cat:${cat.id}")
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun MoviesTab(chrome: TabChrome, onOpen: (Target) -> Unit) {
    val c = LocalContext.current.container
    val res = LocalContext.current.resources
    val dao = c.db.catalog()
    val categories by remember { visibleCategories(c, CatalogRepository.TYPE_MOVIE) }.collectAsState(initial = null)
    val count by remember { dao.movieCount() }.collectAsState(initial = -1)
    if (count == 0) { EmptyLibrary(); return }
    val cats = categories ?: return
    RowsPage(chrome, tag = "movies") { list ->
        item(key = "continue") {
            PagedPosterRow(stringResource(R.string.row_continue_watching), 0, list, { limit ->
                c.library.progress.map { p -> com.lck07.lumen.core.library.Library.moviesInProgress(p).mapNotNull { it.movieId }.take(limit) }
                    .flatMapLatest { ids ->
                        if (ids.isEmpty()) flowOf(emptyList())
                        else dao.moviesById(ids).map { l -> val o = ids.withIndex().associate { it.value to it.index }; l.sortedBy { o[it.id] }.map { it.poster(res) } }
                    }
            }, onOpen, "row:continue")
        }
        item(key = "mine") {
            PagedPosterRow(stringResource(R.string.row_your_movies), 1, list, { limit ->
                c.library.favourites(FavouriteKind.Movie).flatMapLatest { f ->
                    val ids = f.mapNotNull { it.toIntOrNull() }.take(limit)
                    if (ids.isEmpty()) flowOf(emptyList())
                    else dao.moviesById(ids).map { l -> val o = ids.withIndex().associate { it.value to it.index }; l.sortedBy { o[it.id] }.map { it.poster(res) } }
                }
            }, onOpen, "row:mymovies")
        }
        item(key = "added") {
            PagedPosterRow(stringResource(R.string.row_recently_added_movies), 2, list, { limit -> dao.recentMovies(limit).map { l -> l.map { it.poster(res) } } }, onOpen, "row:added")
        }
        cats.forEachIndexed { i, cat ->
            item(key = "cat:${cat.id}") {
                PagedPosterRow(cat.name, i + 3, list, { limit -> dao.moviesIn(cat.id, limit).map { l -> l.map { it.poster(res) } } }, onOpen, "row:cat:${cat.id}")
            }
        }
    }
}

/** Categories of a type, without the ones locked by the parental lock (until unlocked). */
fun visibleCategories(c: com.lck07.lumen.AppContainer, type: String): Flow<List<CategoryEntity>> =
    combine(c.db.catalog().categories(type), c.settings.settings, c.parental.unlocked) { cats, s, unlocked ->
        if (unlocked || !s.parentalLockOn) cats
        else cats.filter { SettingsStore.categoryKey(type, it.id) !in s.lockedCategories }
    }

@Composable
fun EmptyLibrary() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        MessageState(stringResource(R.string.tab_series), stringResource(R.string.empty_library), icon = LumenIcons.Warning, modifier = Modifier.fillMaxWidth(0.6f))
    }
}
