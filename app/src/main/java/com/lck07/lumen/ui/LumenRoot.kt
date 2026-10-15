package com.lck07.lumen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import com.lck07.lumen.R
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.TabBar
import com.lck07.lumen.design.TabItem

/** Shared state between the tab bar and the tab's content. */
@Stable
class TabChrome {
    /** The content is scrolled to its top, so the tab bar shows. */
    var contentAtTop by mutableStateOf(true)
    var tabBarFocused by mutableStateOf(false)
    /** Incremented to ask the current tab to scroll back to its top. */
    var scrollToTop by mutableIntStateOf(0)
    val tabRequester = FocusRequester()
    val tabBarVisible: Boolean get() = tabBarFocused || contentAtTop
}

/**
 * The app frame: the current tab under the glass tab bar, screens stacked on top, toasts.
 * Back goes to the parent screen; on a tab it first moves focus to the tab bar, and a second
 * Back leaves the app.
 */
@Composable
fun LumenRoot(
    navigator: Navigator,
    onExit: () -> Unit,
    tabContent: @Composable (tab: TopTab, chrome: TabChrome) -> Unit,
    screenContent: @Composable (screen: Screen) -> Unit,
) {
    val toaster = remember { Toaster() }
    val chrome = remember { TabChrome() }
    val holder = rememberSaveableStateHolder()

    BackHandler {
        when {
            navigator.pop() -> Unit
            !chrome.tabBarFocused -> {
                chrome.scrollToTop++
                runCatching { chrome.tabRequester.requestFocus() }
            }
            else -> onExit()
        }
    }

    CompositionLocalProvider(LocalToaster provides toaster) {
        Box(Modifier.fillMaxSize().background(LumenColors.Background)) {
            ScreenLayer(active = navigator.stack.isEmpty()) {
                Box(Modifier.fillMaxSize().focusRequester(navigator.contentRequester).focusRestorer()) {
                    holder.SaveableStateProvider(navigator.tab.key) {
                        key(navigator.tab) { tabContent(navigator.tab, chrome) }
                    }
                }
                TabBar(
                    tabs = listOf(
                        TabItem(TopTab.Home, stringResource(R.string.tab_home)),
                        TabItem(TopTab.Series, stringResource(R.string.tab_series)),
                        TabItem(TopTab.Movies, stringResource(R.string.tab_movies)),
                        TabItem(TopTab.Live, stringResource(R.string.tab_live)),
                        TabItem(TopTab.Guide, stringResource(R.string.tab_guide)),
                        TabItem(TopTab.Search, stringResource(R.string.tab_search), LumenIcons.Search),
                        TabItem(TopTab.Settings, stringResource(R.string.tab_settings), LumenIcons.Gear),
                    ),
                    selected = navigator.tab,
                    onSelect = { t -> if (t != navigator.tab) { navigator.selectTab(t); chrome.contentAtTop = true } },
                    visible = chrome.tabBarVisible,
                    selectedRequester = chrome.tabRequester,
                    contentRequester = navigator.contentRequester,
                    modifier = Modifier.onFocusChanged { chrome.tabBarFocused = it.hasFocus },
                )
            }
            navigator.stack.forEachIndexed { i, screen ->
                key(screen.key) {
                    ScreenLayer(active = i == navigator.stack.lastIndex) { screenContent(screen) }
                }
            }
            ToastHost(toaster)
        }
    }
}
