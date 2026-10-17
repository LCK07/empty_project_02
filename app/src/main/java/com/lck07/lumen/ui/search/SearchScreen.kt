package com.lck07.lumen.ui.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lck07.lumen.R
import com.lck07.lumen.container
import com.lck07.lumen.data.CatalogRepository
import com.lck07.lumen.data.LanguageFilter
import com.lck07.lumen.data.SettingsStore
import com.lck07.lumen.design.ChannelTile
import com.lck07.lumen.design.Icon
import com.lck07.lumen.design.LocalReduceMotion
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.LumenRadii
import com.lck07.lumen.design.LumenSpacing
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.NoBringIntoView
import com.lck07.lumen.design.PosterTile
import com.lck07.lumen.design.TvRow
import com.lck07.lumen.design.Txt
import com.lck07.lumen.design.controlFocusEffect
import com.lck07.lumen.design.rememberTvState
import com.lck07.lumen.design.revealOnFocus
import com.lck07.lumen.design.tvClickable
import com.lck07.lumen.ui.LocalScreenActive
import com.lck07.lumen.ui.TabChrome
import com.lck07.lumen.ui.home.channelItems
import com.lck07.lumen.ui.home.poster
import com.lck07.lumen.ui.model.Target
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val LIMIT = 40

/** One key of the single-row keyboard. */
private sealed interface KeyCap {
    data class Letter(val c: Char) : KeyCap
    data object Space : KeyCap
    data object Delete : KeyCap
    data object Voice : KeyCap
}

/**
 * Search: the Apple-style single-row keyboard (a–z, space, delete), results across series,
 * movies and channels updating as you type, and voice search where the device offers it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(chrome: TabChrome, onOpen: (Target) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val res = context.resources
    var query by rememberSaveable { mutableStateOf("") }
    val firstKey = remember { FocusRequester() }

    // Voice search through the system recogniser (Google TV); absent elsewhere.
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }
    val voiceAvailable = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it.lowercase().take(40) }
    }
    fun startVoice() { if (voiceAvailable) runCatching { voice.launch(voiceIntent) } }

    val filter by c.profiles.languageFilter.collectAsState(initial = LanguageFilter.of(""))
    val results by produceState(SearchResults(), query, filter) {
        delay(150)
        value = search(c, res, query, filter)
    }

    LaunchedEffect(Unit) { chrome.contentAtTop = true }
    val active = LocalScreenActive.current
    LaunchedEffect(active) { if (active) { awaitFrame(); runCatching { firstKey.requestFocus() } } }

    Box(
        Modifier
            .fillMaxSize()
            .testTag("search")
            .onPreviewKeyEvent { e ->
                // The remote's search/voice key starts voice search here.
                if (e.type == KeyEventType.KeyDown && e.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_SEARCH && voiceAvailable) { startVoice(); true } else false
            },
    ) {
        // The query line with the blue caret.
        Row(
            Modifier.padding(start = LumenSpacing.SafeX, end = LumenSpacing.SafeX, top = 95.dp).fillMaxWidth().heightIn(min = 35.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (query.isEmpty()) {
                Caret()
                Txt(stringResource(R.string.search_placeholder), LumenType.Title2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = LumenColors.Label3)
            } else {
                Txt(query, LumenType.Title2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                Caret()
            }
        }
        Box(Modifier.padding(start = LumenSpacing.SafeX, end = LumenSpacing.SafeX, top = 139.dp).fillMaxWidth().height(1.dp).background(LumenColors.Separator))

        // The keyboard.
        val keys = remember(voiceAvailable) {
            buildList {
                if (voiceAvailable) add(KeyCap.Voice)
                ('a'..'z').forEach { add(KeyCap.Letter(it)) }
                add(KeyCap.Space); add(KeyCap.Delete)
            }
        }
        Row(
            Modifier.padding(start = LumenSpacing.SafeX, end = LumenSpacing.SafeX, top = 160.dp).fillMaxWidth().focusRestorer(firstKey),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            keys.forEachIndexed { i, k ->
                val label = when (k) {
                    is KeyCap.Letter -> k.c.toString()
                    KeyCap.Space -> stringResource(R.string.search_space)
                    KeyCap.Delete -> stringResource(R.string.search_delete)
                    KeyCap.Voice -> stringResource(R.string.search_voice)
                }
                KeyButton(
                    text = (k as? KeyCap.Letter)?.c?.toString(),
                    icon = when (k) { KeyCap.Space -> LumenIcons.Space; KeyCap.Delete -> LumenIcons.Delete; KeyCap.Voice -> LumenIcons.Mic; else -> null },
                    label = label,
                    width = if (k is KeyCap.Letter) (if (voiceAvailable) 25.dp else 26.dp) else 52.dp,
                    requester = if ((k as? KeyCap.Letter)?.c == 'a') firstKey else null,
                    first = i == 0, last = i == keys.lastIndex,
                ) {
                    query = when (k) {
                        is KeyCap.Letter -> (query + k.c).take(40)
                        KeyCap.Space -> if (query.isNotEmpty() && !query.endsWith(' ')) "$query " else query
                        KeyCap.Delete -> query.dropLast(1)
                        KeyCap.Voice -> { startVoice(); query }
                    }
                }
            }
        }

        // Results.
        val list = rememberLazyListState()
        CompositionLocalProvider(LocalBringIntoViewSpec provides NoBringIntoView) {
            LazyColumn(
                state = list,
                modifier = Modifier.padding(top = 205.dp).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val r = results
                if (query.isNotBlank() && r.empty && r.query == query) item(key = "none") {
                    Txt(stringResource(R.string.search_no_results, query.trim()), LumenType.Body, Modifier.padding(horizontal = LumenSpacing.SafeX, vertical = 20.dp), color = LumenColors.Label2, maxLines = 2)
                }
                var row = 0
                if (r.series.isNotEmpty()) { val i = row++; item(key = "series") {
                    TvRow(stringResource(if (query.isBlank()) R.string.row_recently_added_series else R.string.row_series), r.series, { it.key }, Modifier.revealOnFocus(list, i, 0.dp), tag = "row:search-series") { item, slot ->
                        PosterTile(item, { onOpen(item.target) }, slot = slot)
                    }
                } }
                if (r.movies.isNotEmpty()) { val i = row++; item(key = "movies") {
                    TvRow(stringResource(if (query.isBlank()) R.string.row_recently_added_movies else R.string.row_movies), r.movies, { it.key }, Modifier.revealOnFocus(list, i, 0.dp), tag = "row:search-movies") { item, slot ->
                        PosterTile(item, { onOpen(item.target) }, slot = slot)
                    }
                } }
                if (r.channels.isNotEmpty()) { val i = row++; item(key = "channels") {
                    TvRow(stringResource(R.string.row_channels), r.channels, { it.id }, Modifier.revealOnFocus(list, i, 0.dp), tag = "row:search-channels") { item, slot ->
                        ChannelTile(item, { onOpen(Target.PlayChannel(item.id)) }, slot = slot)
                    }
                } }
            }
        }
    }
}

data class SearchResults(
    val query: String = "",
    val series: List<com.lck07.lumen.ui.model.PosterItem> = emptyList(),
    val movies: List<com.lck07.lumen.ui.model.PosterItem> = emptyList(),
    val channels: List<com.lck07.lumen.ui.model.ChannelItem> = emptyList(),
) {
    val empty get() = series.isEmpty() && movies.isEmpty() && channels.isEmpty()
}

/** Searches the cached catalogue (names without accents, so "cafe" finds "Café"); locked categories stay hidden. */
private suspend fun search(c: com.lck07.lumen.AppContainer, res: android.content.res.Resources, query: String, f: LanguageFilter): SearchResults {
    val dao = c.db.catalog()
    val q = CatalogRepository.searchKey(query)
    val s = c.settings.current()
    val unlocked = c.parental.unlocked.value || !s.parentalLockOn
    fun allowed(type: String, cat: String?) = unlocked || cat == null || SettingsStore.categoryKey(type, cat) !in s.lockedCategories
    if (q.isBlank()) {
        return SearchResults(
            query,
            series = dao.recentlyUpdatedSeries(LIMIT, f.all, f.langs).first().filter { allowed(CatalogRepository.TYPE_SERIES, it.categoryId) }.map { it.poster(res) },
            movies = dao.recentMovies(LIMIT, f.all, f.langs).first().filter { allowed(CatalogRepository.TYPE_MOVIE, it.categoryId) }.map { it.poster(res) },
        )
    }
    val onNow = c.guide.onNow.first()
    return SearchResults(
        query,
        series = dao.searchSeries(q, LIMIT, f.all, f.langs).filter { allowed(CatalogRepository.TYPE_SERIES, it.categoryId) }.map { it.poster(res) },
        movies = dao.searchMovies(q, LIMIT, f.all, f.langs).filter { allowed(CatalogRepository.TYPE_MOVIE, it.categoryId) }.map { it.poster(res) },
        channels = channelItems(dao.searchChannels(q, LIMIT).filter { allowed(CatalogRepository.TYPE_LIVE, it.categoryId) }, onNow, emptySet()),
    )
}

@Composable
private fun Caret() {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "caret")
    val a by t.animateFloat(1f, 0f, infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse), label = "blink")
    Box(Modifier.padding(horizontal = 2.dp).size(2.dp, 28.dp).graphicsLayer { alpha = if (reduce) 1f else if (a > 0.5f) 1f else 0f }.background(LumenColors.Accent))
}

@Composable
private fun KeyButton(
    text: String?,
    icon: ImageVector?,
    label: String,
    width: Dp,
    requester: FocusRequester?,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
) {
    val state = rememberTvState()
    val bg by animateColorAsState(if (state.focused) LumenColors.Focused else androidx.compose.ui.graphics.Color.Transparent, tween(120), label = "key")
    val fg = if (state.focused) LumenColors.OnFocused else LumenColors.Label2
    Box(
        Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .controlFocusEffect(state, LumenRadii.Key, focusScale = 1.18f, shadow = 8.dp)
            .background(bg, LumenRadii.Key)
            .tvClickable(state, label = label, role = Role.Button, onKey = { k ->
                if ((k == Key.DirectionLeft && first) || (k == Key.DirectionRight && last)) { state.nudge(if (first) com.lck07.lumen.design.Travel.Left else com.lck07.lumen.design.Travel.Right); true } else false
            }, onClick = onClick)
            .size(width, 32.dp)
            .testTag("key:${text ?: label}"),
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) Txt(text, LumenType.Caption1Strong, color = fg)
        else if (icon != null) Icon(icon, tint = fg, size = 14.dp)
    }
}
