package com.lck07.lumen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lck07.lumen.R
import com.lck07.lumen.container
import com.lck07.lumen.data.AccountState
import com.lck07.lumen.data.FavouriteKind
import com.lck07.lumen.data.ImportUi
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.MessageState
import com.lck07.lumen.player.VodPlayerScreen
import com.lck07.lumen.ui.live.LivePlayerScreen
import com.lck07.lumen.ui.live.LiveTab
import com.lck07.lumen.ui.browse.MoviesTab
import com.lck07.lumen.ui.browse.SeriesTab
import com.lck07.lumen.ui.detail.MovieDetailScreen
import com.lck07.lumen.ui.detail.SeriesDetailScreen
import com.lck07.lumen.ui.home.HomeModel
import com.lck07.lumen.ui.home.HomeScreen
import com.lck07.lumen.ui.home.HomeState
import com.lck07.lumen.ui.model.Target
import com.lck07.lumen.ui.setup.ImportScreen
import com.lck07.lumen.ui.setup.SetupScreen
import kotlinx.coroutines.launch

/**
 * Decides what the app shows: Add Account when signed out, the library import after signing
 * in, and otherwise the tabs. Cached data shows at once; refreshes run behind it.
 */
@Composable
fun AppShell(navigator: Navigator, onExit: () -> Unit) {
    val c = LocalContext.current.container
    val account by c.account.state.collectAsState()
    val settings by c.settings.settings.collectAsState(initial = null)
    val import by c.session.import.collectAsState()

    when (val a = account) {
        AccountState.Loading -> Blank()
        AccountState.SignedOut -> SetupScreen(signIn = { acct ->
            c.account.signIn(acct)
            c.session.startImport(c.account.clientFor(acct))
        })
        is AccountState.SignedIn -> {
            val s = settings ?: return Blank()
            val needsImport = s.catalogImportedAt == 0L
            if (needsImport || import !is ImportUi.Idle) {
                val scope = rememberCoroutineScope()
                LaunchedEffect(a) { if (needsImport && import is ImportUi.Idle) c.session.startImport(a.client) }
                ImportScreen(
                    if (needsImport && import is ImportUi.Idle) ImportUi.Running(com.lck07.lumen.data.ImportProgress(com.lck07.lumen.data.ImportProgress.Step.Categories)) else import,
                    onRetry = { c.session.startImport(a.client) },
                    onSignOut = { scope.launch { c.session.signOut() } },
                )
            } else {
                LaunchedEffect(a.account) { c.session.refreshInBackground() }
                MainTabs(navigator, onExit)
            }
        }
    }
}

@Composable
private fun Blank() = Box(Modifier.fillMaxSize().background(LumenColors.Background))

/**
 * Opens whatever a tile or button points at. Playback started elsewhere (Home, Search) gets
 * the series or movie detail underneath, so leaving the player lands somewhere relevant.
 */
fun Navigator.open(t: Target) {
    fun under(detail: Screen) { if (top?.key != detail.key) push(detail) }
    when (t) {
        is Target.SeriesDetail -> push(Screen.SeriesDetail(t.seriesId))
        is Target.MovieDetail -> push(Screen.MovieDetail(t.movieId))
        is Target.PlayEpisode -> { under(Screen.SeriesDetail(t.seriesId)); push(Screen.Vod(VodRequest.Episode(t.seriesId, t.episodeId, t.startOver))) }
        is Target.PlaySeries -> { under(Screen.SeriesDetail(t.seriesId)); push(Screen.Vod(VodRequest.SeriesStart(t.seriesId))) }
        is Target.PlayMovie -> { under(Screen.MovieDetail(t.movieId)); push(Screen.Vod(VodRequest.Movie(t.movieId, t.startOver))) }
        is Target.PlayChannel -> push(Screen.Live(t.channelId))
    }
}

@Composable
private fun MainTabs(navigator: Navigator, onExit: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val onOpen: (Target) -> Unit = { navigator.open(it) }
    LumenRoot(
        navigator = navigator,
        onExit = onExit,
        tabContent = { tab, chrome ->
            when (tab) {
                TopTab.Home -> {
                    val model = remember { HomeModel(c, context.resources) }
                    val state by remember { model.state }.collectAsState(initial = HomeState())
                    val resume = remember { FocusRequester() }
                    HomeScreen(
                        state = state,
                        onOpen = onOpen,
                        onToggleFavouriteChannel = { ch ->
                            scope.launch {
                                val on = c.library.toggleFavourite(FavouriteKind.Channel, ch.id.toString())
                                toaster.show(context.getString(if (on) R.string.added_to_favourites else R.string.removed_from_favourites))
                            }
                        },
                        onAtTop = { chrome.contentAtTop = it },
                        resumeRequester = resume,
                        contentRequester = navigator.contentRequester,
                        scrollToTop = chrome.scrollToTop,
                    )
                    // Resume has focus on launch, so one press plays.
                    val hasHero = state.hero != null
                    LaunchedEffect(hasHero) { if (hasHero) { withFrameNanos { }; runCatching { resume.requestFocus() } } }
                }
                TopTab.Series -> SeriesTab(chrome, onOpen)
                TopTab.Movies -> MoviesTab(chrome, onOpen)
                TopTab.Live -> LiveTab(chrome, onWatch = { navigator.push(Screen.Live(it)) }, onGuide = { navigator.selectTab(TopTab.Guide) })
                else -> ComingSoon(tab)
            }
        },
        screenContent = { screen ->
            when (screen) {
                is Screen.SeriesDetail -> SeriesDetailScreen(screen.seriesId, onOpen)
                is Screen.MovieDetail -> MovieDetailScreen(screen.movieId, onOpen)
                is Screen.Vod -> VodPlayerScreen(screen.request, onPlayNext = { navigator.replaceTop(Screen.Vod(it)) }, onExit = { navigator.pop() })
                is Screen.Live -> LivePlayerScreen(screen.channelId, onGuide = { navigator.selectTab(TopTab.Guide) })
            }
        },
    )
}

/** Placeholder for parts that arrive in later milestones. */
@Composable
private fun ComingSoon(tab: TopTab?) {
    Box(Modifier.fillMaxSize().background(LumenColors.Background), contentAlignment = Alignment.Center) {
        MessageState(tab?.name ?: stringResource(R.string.loading), null, icon = LumenIcons.Warning)
    }
}
