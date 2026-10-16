package com.lck07.lumen

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.lck07.lumen.design.FocusTravel
import com.lck07.lumen.design.LocalReduceMotion
import com.lck07.lumen.design.reduceMotionEnabled
import com.lck07.lumen.ui.AppShell
import com.lck07.lumen.ui.Navigator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotionEnabled(this)) {
                val navigator = remember { Navigator() }
                AppShell(navigator, onExit = { finish() })
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
