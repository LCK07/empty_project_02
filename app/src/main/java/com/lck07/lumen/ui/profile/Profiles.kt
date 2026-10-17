package com.lck07.lumen.ui.profile

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lck07.lumen.R
import com.lck07.lumen.data.db.ProfileEntity
import com.lck07.lumen.design.Avatar
import com.lck07.lumen.design.Glass
import com.lck07.lumen.design.Icon
import com.lck07.lumen.design.ListRow
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.LumenRadii
import com.lck07.lumen.design.LumenSpacing
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.Travel
import com.lck07.lumen.design.Txt
import com.lck07.lumen.design.controlFocusEffect
import com.lck07.lumen.design.displayName
import com.lck07.lumen.design.rememberTvState
import com.lck07.lumen.design.tvClickable
import kotlinx.coroutines.android.awaitFrame

/** The active profile's avatar at the right end of the tab bar; Select opens the switcher. */
@Composable
fun ProfileAvatarButton(profile: ProfileEntity?, onClick: () -> Unit) {
    val p = profile ?: return
    val state = rememberTvState()
    val bg by animateColorAsState(if (state.focused) LumenColors.Focused else Color.Transparent, tween(160), label = "avatar")
    val name = p.displayName()
    Box(
        Modifier
            .controlFocusEffect(state, CircleShape, shadow = 10.dp)
            .background(bg, CircleShape)
            .tvClickable(
                state, label = stringResource(R.string.profile_avatar_label, name),
                onKey = { k -> if (k == Key.DirectionRight) { state.nudge(Travel.Right); true } else false },
                onClick = onClick,
            )
            .padding(3.dp)
            .testTag("profile:avatar"),
        contentAlignment = Alignment.Center,
    ) { Avatar(name, p.avatar, size = 23.dp) }
}

/** A small panel over the app: pick a profile, or go to Settings → Profiles. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProfileSwitcher(profiles: List<ProfileEntity>, activeId: Int, onPick: (Int) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Glass(LumenRadii.Banner, Modifier.width(340.dp).testTag("profile:switcher"), strong = true) {
            // Focus stays inside the panel until it is dismissed.
            Column(
                Modifier.focusProperties { exit = { FocusRequester.Cancel } }.focusGroup().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Txt(stringResource(R.string.profile_switch_title), LumenType.Title3, Modifier.padding(start = 4.dp, bottom = 6.dp))
                profiles.forEach { p ->
                    ListRow(
                        p.displayName(), null, { onPick(p.id) },
                        trailingIcon = if (p.id == activeId) LumenIcons.Check else null, selected = p.id == activeId,
                        focusRequester = if (p.id == activeId) first else null, modifier = Modifier.testTag("profile:${p.id}"),
                    )
                }
                ListRow(stringResource(R.string.profile_manage), null, onManage, focusRequester = if (profiles.none { it.id == activeId }) first else null)
            }
        }
    }
    LaunchedEffect(Unit) { awaitFrame(); runCatching { first.requestFocus() } }
}

/** "Who's watching?" at launch when there is more than one profile. */
@Composable
fun WhoIsWatching(profiles: List<ProfileEntity>, activeId: Int, onPick: (Int) -> Unit, onExit: () -> Unit) {
    val first = remember { FocusRequester() }
    BackHandler(onBack = onExit)
    Column(
        Modifier.fillMaxSize().background(LumenColors.Background).testTag("whos-watching"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Txt(stringResource(R.string.profile_whos_watching), LumenType.Title1, Modifier.padding(bottom = 24.dp))
        LazyRow(
            Modifier.focusRestorer(first), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = LumenSpacing.SafeX, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        ) {
            items(profiles, key = { it.id }) { p ->
                val state = rememberTvState()
                val name = p.displayName()
                Column(
                    Modifier
                        .then(if (p.id == activeId || (profiles.none { it.id == activeId } && p == profiles.first())) Modifier.focusRequester(first) else Modifier)
                        .controlFocusEffect(state, LumenRadii.Banner, focusScale = 1.12f, shadow = 14.dp)
                        .tvClickable(state, label = name) { onPick(p.id) }
                        .padding(10.dp)
                        .testTag("profile:${p.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Avatar(name, p.avatar, size = 88.dp)
                    Txt(name, LumenType.Headline, color = if (state.focused) LumenColors.Label else LumenColors.Label2)
                }
            }
        }
    }
    LaunchedEffect(Unit) { awaitFrame(); runCatching { first.requestFocus() } }
}

/** One row of colour swatches; the first swatch takes [requester]. */
@Composable
fun AvatarColours(selected: Int, onPick: (Int) -> Unit, requester: FocusRequester? = null, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 17.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LumenColors.Avatars.forEachIndexed { i, colour ->
            val state = rememberTvState()
            Box(
                Modifier
                    .then(if (i == 0 && requester != null) Modifier.focusRequester(requester) else Modifier)
                    .size(30.dp)
                    .controlFocusEffect(state, CircleShape, focusScale = 1.2f, shadow = 8.dp)
                    .background(colour, CircleShape)
                    .tvClickable(
                        state, label = stringResource(R.string.profile_colour_n, i + 1), selected = i == selected,
                        onKey = { k ->
                            if ((k == Key.DirectionLeft && i == 0) || (k == Key.DirectionRight && i == LumenColors.Avatars.lastIndex)) {
                                state.nudge(if (k == Key.DirectionLeft) Travel.Left else Travel.Right); true
                            } else false
                        },
                    ) { onPick(i) }
                    .testTag("colour:$i"),
                contentAlignment = Alignment.Center,
            ) { if (i == selected) Icon(LumenIcons.Check, size = 14.dp) }
        }
    }
}
