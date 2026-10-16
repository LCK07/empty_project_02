package com.lck07.lumen.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lck07.lumen.design.TvRow
import com.lck07.lumen.ui.home.HomeScreen
import com.lck07.lumen.ui.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Focus and navigation rules from the brief: Back, row focus memory, Home opening on Resume. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-xhdpi")
class FocusNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun key(k: Key) = rule.onRoot().performKeyInput { pressKey(k) }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }

    @Test
    fun homeOpensWithFocusOnResume() {
        rule.setContent {
            val resume = remember { FocusRequester() }
            HomeScreen(
                state = SampleData.home(), onOpen = {}, onToggleFavouriteChannel = {}, onAtTop = {},
                resumeRequester = resume, contentRequester = remember { FocusRequester() }, scrollToTop = 0,
            )
            LaunchedEffect(Unit) { withFrameNanos { }; resume.requestFocus() }
        }
        rule.onNodeWithTag("hero:play").assertIsFocused()
    }

    @Test
    fun backMovesToTabBarThenLeaves() {
        var exits = 0
        rule.setContent {
            val navigator = remember { Navigator() }
            val first = remember { FocusRequester() }
            LumenRoot(
                navigator = navigator,
                onExit = { exits++ },
                tabContent = { _, _ ->
                    Column { Box(Modifier.size(100.dp).focusRequester(first).testTag("content").focusable()) }
                    LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
                },
                screenContent = {},
            )
        }
        rule.onNodeWithTag("content").assertIsFocused()
        back()
        rule.waitUntil(2_000) { runCatching { rule.onNodeWithTag("tab:Home").assertIsFocused() }.isSuccess }
        assertEquals(0, exits)
        back()
        assertEquals(1, exits)
    }

    @Test
    fun backFromAScreenReturnsToTheTab() {
        lateinit var navigator: Navigator
        rule.setContent {
            navigator = remember { Navigator() }
            LumenRoot(navigator = navigator, onExit = {}, tabContent = { _, _ -> }, screenContent = { Box(Modifier.testTag("screen")) })
        }
        rule.runOnUiThread { navigator.push(Screen.SeriesDetail(1)) }
        rule.waitForIdle()
        assertEquals(1, navigator.stack.size)
        back()
        assertEquals(0, navigator.stack.size)
    }

    @Test
    fun upFromContentLandsOnTheSelectedTab() {
        rule.setContent {
            val content = remember { FocusRequester() }
            Column {
                com.lck07.lumen.design.TabBar(
                    tabs = listOf("a", "b", "c").map { com.lck07.lumen.design.TabItem(it, it) },
                    selected = "c", onSelect = {}, visible = true, contentRequester = content,
                )
                Box(Modifier.size(100.dp).focusRequester(content).testTag("content").focusable())
            }
            LaunchedEffect(Unit) { withFrameNanos { }; content.requestFocus() }
        }
        // Visit another tab first, so a focus restorer would remember it.
        key(Key.DirectionUp); key(Key.DirectionLeft)
        rule.onNodeWithTag("tab:b").assertIsFocused()
        key(Key.DirectionDown)
        rule.onNodeWithTag("content").assertIsFocused()
        key(Key.DirectionUp)
        rule.onNodeWithTag("tab:c").assertIsFocused()
    }

    @Test
    fun rowRemembersItsFocusedTile() {
        rule.setContent {
            val first = remember { FocusRequester() }
            Column {
                for (r in 0..1) {
                    TvRow(title = null, items = (0..5).toList(), key = { it }) { i, _ ->
                        Box(Modifier.size(80.dp, 60.dp).then(if (r == 0 && i == 0) Modifier.focusRequester(first) else Modifier).testTag("r$r:$i").focusable())
                    }
                }
            }
            LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
        }
        key(Key.DirectionRight); key(Key.DirectionRight)
        rule.onNodeWithTag("r0:2").assertIsFocused()
        key(Key.DirectionDown)
        key(Key.DirectionLeft)
        key(Key.DirectionUp)
        rule.onNodeWithTag("r0:2").assertIsFocused()
    }
}
