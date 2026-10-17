package com.lck07.lumen.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class TabItem<T>(val key: T, val title: String, val icon: ImageVector? = null)

/**
 * The glass capsule tab bar, centred at the top. It slides up and fades when [visible] is
 * false (focus has moved down into the content) and returns when focus comes back up.
 * Selecting a tab switches to it; the current tab keeps a fill.
 */
@Composable
fun <T> TabBar(
    tabs: List<TabItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
    selectedRequester: FocusRequester = remember { FocusRequester() },
    contentRequester: FocusRequester? = null,
    /** Something after the last tab (the profile avatar); it takes the right-edge bump itself. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val reduce = LocalReduceMotion.current
    val shown by animateFloatAsState(if (visible) 1f else 0f, if (reduce) tween(150) else tween(450), label = "tabbar")
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 22.dp)
            .graphicsLayer {
                alpha = shown
                if (!reduce) translationY = -(1f - shown) * 70.dp.toPx()
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Glass(LumenRadii.Capsule, Modifier.testTag("tabbar")) {
            Row(
                Modifier
                    .focusRestorer(selectedRequester)
                    .focusProperties {
                        canFocus = visible
                        if (contentRequester != null) down = contentRequester
                    }
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { i, tab ->
                    val state = rememberTvState()
                    val isSel = tab.key == selected
                    val bg by animateColorAsState(
                        when { state.focused -> LumenColors.Focused; isSel -> LumenColors.Fill; else -> Color.Transparent }, tween(160), label = "tab",
                    )
                    val fg = when { state.focused -> LumenColors.OnFocused; isSel -> LumenColors.Label; else -> LumenColors.Label2 }
                    Row(
                        Modifier
                            .then(if (isSel) Modifier.focusRequester(selectedRequester) else Modifier)
                            .controlFocusEffect(state, LumenRadii.Capsule, shadow = 10.dp)
                            .background(bg, LumenRadii.Capsule)
                            .tvClickable(
                                state, role = Role.Tab, selected = isSel,
                                label = if (tab.icon != null) tab.title else null,
                                onKey = { k ->
                                    if ((k == Key.DirectionLeft && i == 0) || (k == Key.DirectionRight && i == tabs.lastIndex && trailing == null)) {
                                        state.nudge(if (k == Key.DirectionLeft) Travel.Left else Travel.Right); true
                                    } else false
                                },
                            ) { onSelect(tab.key) }
                            .testTag("tab:${tab.key}")
                            .heightIn(min = LumenSizes.TabHeight)
                            .padding(horizontal = if (tab.icon != null) 9.dp else 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (tab.icon != null) Icon(tab.icon, tint = fg, size = 14.dp)
                        else Txt(tab.title, LumenType.Caption1.copy(fontWeight = if (isSel || state.focused) FontWeight.SemiBold else FontWeight.Medium), color = fg)
                    }
                }
                trailing?.invoke()
            }
        }
    }
}
