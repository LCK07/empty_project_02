package com.lck07.lumen

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lck07.lumen.design.FocusTravel
import com.lck07.lumen.design.LocalReduceMotion
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.TabBar
import com.lck07.lumen.design.TabItem
import com.lck07.lumen.design.reduceMotionEnabled

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotionEnabled(this)) {
                var tab by remember { mutableStateOf("home") }
                Box(Modifier.fillMaxSize().background(LumenColors.Background)) {
                    TabBar(
                        tabs = listOf(
                            TabItem("home", stringResource(R.string.tab_home)),
                            TabItem("series", stringResource(R.string.tab_series)),
                            TabItem("movies", stringResource(R.string.tab_movies)),
                            TabItem("live", stringResource(R.string.tab_live)),
                            TabItem("guide", stringResource(R.string.tab_guide)),
                            TabItem("search", stringResource(R.string.tab_search), LumenIcons.Search),
                            TabItem("settings", stringResource(R.string.tab_settings), LumenIcons.Gear),
                        ),
                        selected = tab,
                        onSelect = { tab = it },
                        visible = true,
                    )
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) FocusTravel.record(event.keyCode)
        return super.dispatchKeyEvent(event)
    }
}
