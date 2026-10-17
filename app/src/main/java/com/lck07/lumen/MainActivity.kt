package com.lck07.lumen

import android.os.Bundle
import android.content.Intent
import kotlinx.coroutines.launch
import android.view.KeyEvent
import com.lck07.lumen.core.library.WatchProgress
import com.lck07.lumen.ui.Screen
import com.lck07.lumen.ui.TopTab
import com.lck07.lumen.ui.model.Target
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.lck07.lumen.design.FocusTravel
import com.lck07.lumen.design.LocalReduceMotion
import com.lck07.lumen.design.reduceMotionEnabled
import com.lck07.lumen.ui.AppShell
import com.lck07.lumen.ui.Navigator

class MainActivity : ComponentActivity() {
    private val navigator = Navigator()

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(Locales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotionEnabled(this)) {
                AppShell(navigator, onExit = { finish() })
            }
        }
        if (savedInstanceState == null) handle(intent)
        // Compose draws its own focus; the platform's grey focus highlight would show through.
        window.decorView.defaultFocusHighlightEnabled = false
        (findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0))?.defaultFocusHighlightEnabled = false
    }

    override fun onStop() {
        super.onStop()
        container.scope.launch { container.settings.touchLastScreen(System.currentTimeMillis()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** lumen://play?key=episode:12:345 (Watch Next on the Google TV home screen) plays that title. */
    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "lumen") return
        val p = WatchProgress(uri.getQueryParameter("key") ?: return, 0, 0, java.time.Instant.EPOCH)
        navigator.pending = when {
            p.seriesId != null && p.episodeId != null -> Target.PlayEpisode(p.seriesId!!, p.episodeId!!)
            p.movieId != null -> Target.PlayMovie(p.movieId!!)
            else -> null
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) FocusTravel.record(event.keyCode)
        // The remote's Guide key opens the Guide from anywhere.
        if (event.keyCode == KeyEvent.KEYCODE_SEARCH && navigator.tab != TopTab.Search) {
            if (event.action == KeyEvent.ACTION_UP) navigator.selectTab(TopTab.Search)
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_GUIDE) {
            if (event.action == KeyEvent.ACTION_UP) navigator.push(Screen.Guide)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
