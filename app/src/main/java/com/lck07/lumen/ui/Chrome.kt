package com.lck07.lumen.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lck07.lumen.design.Glass
import com.lck07.lumen.design.LocalReduceMotion
import com.lck07.lumen.design.LumenRadii
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.Txt
import kotlinx.coroutines.delay

/** False for screens kept composed underneath the top one: they pause animations and previews. */
val LocalScreenActive = compositionLocalOf { true }

/**
 * One screen in the stack. Hidden layers stay composed (so Back restores their focus and
 * scroll) but are not drawn and cannot take focus.
 */
@Composable
fun ScreenLayer(active: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val requester = remember { FocusRequester() }
    CompositionLocalProvider(LocalScreenActive provides active) {
        Box(
            modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (active) 1f else 0f }
                .focusProperties { onEnter = { if (!active) cancelFocusChange() } }
                .focusGroup(),
        ) {
            Box(Modifier.fillMaxSize().focusRequester(requester).focusRestorer(), content = content)
        }
    }
    // Becoming the top screen again: focus returns to where it was in this screen. (On first
    // appearance each screen focuses its own primary control.)
    var wasHidden by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) wasHidden = true
        else if (wasHidden) { withFrameNanos { }; runCatching { requester.requestFocus() } }
    }
}

/** Short confirmations ("Added to Favourites") in a glass capsule at the top. */
@Stable
class Toaster {
    var message by mutableStateOf<String?>(null)
        private set
    var serial by mutableStateOf(0)
        private set

    fun show(text: String) { message = text; serial++ }
    internal fun clear() { message = null }
}

val LocalToaster = staticCompositionLocalOf { Toaster() }

@Composable
fun ToastHost(toaster: Toaster, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val msg = toaster.message
    val shown by animateFloatAsState(if (msg != null) 1f else 0f, tween(if (reduce) 120 else 350), label = "toast")
    LaunchedEffect(toaster.serial) { if (toaster.message != null) { delay(2400); toaster.clear() } }
    var last by remember { mutableStateOf("") }
    if (msg != null) last = msg
    if (shown > 0.01f) Box(modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.TopCenter) {
        Glass(
            LumenRadii.Capsule,
            Modifier
                .graphicsLayer { alpha = shown; if (!reduce) translationY = -(1f - shown) * 15.dp.toPx() }
                .semantics { liveRegion = LiveRegionMode.Polite },
            strong = true,
        ) {
            Txt(last, LumenType.Caption1Strong, Modifier.padding(horizontal = 17.dp, vertical = 11.dp))
        }
    }
}
