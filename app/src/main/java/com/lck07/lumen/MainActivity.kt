package com.lck07.lumen

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.lck07.lumen.design.FocusTravel
import com.lck07.lumen.design.LocalReduceMotion
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.Txt
import com.lck07.lumen.design.reduceMotionEnabled
import com.lck07.lumen.ui.LocalToaster
import com.lck07.lumen.ui.LumenRoot
import com.lck07.lumen.ui.Navigator
import com.lck07.lumen.ui.TopTab
import com.lck07.lumen.ui.home.HomeScreen
import com.lck07.lumen.ui.sample.SampleData

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotionEnabled(this)) {
                val navigator = remember { Navigator() }
                val resume = remember { FocusRequester() }
                LumenRoot(
                    navigator = navigator,
                    onExit = { finish() },
                    tabContent = { tab, chrome ->
                        when (tab) {
                            TopTab.Home -> {
                                val toaster = LocalToaster.current
                                HomeScreen(
                                    state = remember { SampleData.home() },
                                    onOpen = { toaster.show(it.toString()) },
                                    onToggleFavouriteChannel = { toaster.show(getString(R.string.removed_from_favourites)) },
                                    onAtTop = { chrome.contentAtTop = it },
                                    resumeRequester = resume,
                                    contentRequester = navigator.contentRequester,
                                    scrollToTop = chrome.scrollToTop,
                                )
                                LaunchedEffect(Unit) { withFrameNanos { }; runCatching { resume.requestFocus() } }
                            }
                            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Txt(tab.name, LumenType.Title2) }
                        }
                    },
                    screenContent = { },
                )
            }
        }
        // Compose draws its own focus; the platform's grey focus highlight would show through.
        window.decorView.defaultFocusHighlightEnabled = false
        (findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0))?.defaultFocusHighlightEnabled = false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) FocusTravel.record(event.keyCode)
        return super.dispatchKeyEvent(event)
    }
}
