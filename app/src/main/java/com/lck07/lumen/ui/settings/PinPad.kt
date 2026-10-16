package com.lck07.lumen.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lck07.lumen.R
import com.lck07.lumen.design.Icon
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.LumenRadii
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.Txt
import com.lck07.lumen.design.controlFocusEffect
import com.lck07.lumen.design.rememberTvState
import com.lck07.lumen.design.tvClickable
import kotlinx.coroutines.android.awaitFrame

/**
 * Four-digit PIN entry: dots for the digits typed, and a single row of keys 1–0 and delete.
 * Number keys on the remote (where there are any) type too.
 */
@Composable
fun PinPad(title: String, error: String?, onDone: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    fun type(d: Char) {
        if (pin.length >= 4) return
        pin += d
        if (pin.length == 4) { val p = pin; pin = ""; onDone(p) }
    }
    Column(
        Modifier.onPreviewKeyEvent { e ->
            val code = e.key.nativeKeyCode
            if (e.type == KeyEventType.KeyDown && code in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9) { type('0' + (code - android.view.KeyEvent.KEYCODE_0)); true } else false
        },
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Txt(title, LumenType.Title3)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            repeat(4) { i ->
                Box(
                    Modifier.size(14.dp).background(if (i < pin.length) Color.White else Color.Transparent, CircleShape).border(1.5.dp, LumenColors.Label2, CircleShape),
                )
            }
        }
        if (error != null) Txt(error, LumenType.Callout, color = LumenColors.Live, maxLines = 2, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ("1234567890").forEachIndexed { i, d -> PinKey(d.toString(), null, if (i == 0) first else null, isFirst = i == 0) { type(d) } }
            PinKey(null, stringResource(R.string.search_delete), null, isLast = true) { pin = pin.dropLast(1) }
        }
    }
    LaunchedEffect(Unit) { awaitFrame(); runCatching { first.requestFocus() } }
}

@Composable
private fun PinKey(text: String?, label: String?, requester: FocusRequester?, isFirst: Boolean = false, isLast: Boolean = false, onClick: () -> Unit) {
    val state = rememberTvState()
    val bg by animateColorAsState(if (state.focused) LumenColors.Focused else LumenColors.Fill, tween(120), label = "pin")
    val fg = if (state.focused) LumenColors.OnFocused else LumenColors.Label
    Box(
        Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .controlFocusEffect(state, LumenRadii.Key, focusScale = 1.15f, shadow = 8.dp)
            .background(bg, LumenRadii.Key)
            // Focus stays on the keys until the PIN is done or Back is pressed.
            .tvClickable(
                state, label = label ?: text,
                onKey = { k ->
                    when (k) {
                        androidx.compose.ui.input.key.Key.DirectionUp, androidx.compose.ui.input.key.Key.DirectionDown -> true
                        androidx.compose.ui.input.key.Key.DirectionLeft -> if (isFirst) { state.nudge(com.lck07.lumen.design.Travel.Left); true } else false
                        androidx.compose.ui.input.key.Key.DirectionRight -> if (isLast) { state.nudge(com.lck07.lumen.design.Travel.Right); true } else false
                        else -> false
                    }
                },
                onClick = onClick,
            )
            .size(if (text != null) 34.dp else 52.dp, 40.dp)
            .testTag("pin:${text ?: "delete"}"),
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) Txt(text, LumenType.Headline, color = fg) else Icon(LumenIcons.Delete, tint = fg, size = 15.dp)
    }
}
