package com.lck07.lumen.design

import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** The direction focus last moved, so a newly focused tile can lean that way. */
enum class Travel { None, Left, Right, Up, Down }

object FocusTravel {
    /** Written by the activity for every D-pad key, read when a tile gains focus. */
    @Volatile var last: Travel = Travel.None

    fun record(keyCode: Int) {
        last = when (keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> Travel.Left
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> Travel.Right
            AndroidKeyEvent.KEYCODE_DPAD_UP -> Travel.Up
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> Travel.Down
            else -> last
        }
    }
}

/** True when the system's "Remove animations" setting is on. */
val LocalReduceMotion = staticCompositionLocalOf { false }

fun reduceMotionEnabled(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Focus and press state of one focusable item. The five tvOS states are normal, focused,
 * pressed, selected (passed separately) and unavailable (enabled = false).
 */
@Stable
class TvState {
    var focused by mutableStateOf(false)
        internal set
    var pressed by mutableStateOf(false)
        internal set
    /** Bumps when focus can't go further, for the small "end of row" nudge. */
    internal var bump by mutableStateOf(0 to Travel.None)

    fun nudge(direction: Travel) { bump = (bump.first + 1) to direction }
}

@Composable
fun rememberTvState(): TvState = remember { TvState() }

private val SelectKeys = setOf(
    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER, AndroidKeyEvent.KEYCODE_BUTTON_A,
)
private const val LONG_PRESS_MS = 550L

/**
 * Makes an element focusable and activatable with the remote: Select presses (the element
 * shrinks at once), releasing activates, holding activates [onLongClick]. Also clickable by
 * touch and by accessibility services, with [label] read by TalkBack.
 */
fun Modifier.tvClickable(
    state: TvState,
    enabled: Boolean = true,
    label: String? = null,
    role: Role = Role.Button,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    onKey: ((Key) -> Boolean)? = null,
    onClick: () -> Unit,
): Modifier {
    var downAt = 0L
    var longFired = false
    return this
        .onFocusChanged {
            state.focused = it.isFocused
            if (!it.isFocused) state.pressed = false
        }
        .onKeyEvent { e ->
            val code = e.key.nativeKeyCode
            if (code in SelectKeys) {
                if (!enabled) return@onKeyEvent true
                when (e.type) {
                    KeyEventType.KeyDown -> {
                        val repeat = e.nativeKeyEvent.repeatCount
                        if (repeat == 0) {
                            downAt = SystemClock.uptimeMillis(); longFired = false; state.pressed = true
                        } else if (onLongClick != null && !longFired && SystemClock.uptimeMillis() - downAt >= LONG_PRESS_MS) {
                            longFired = true; state.pressed = false; onLongClick()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        val wasPressed = state.pressed
                        state.pressed = false
                        if (wasPressed && !longFired) onClick()
                        longFired = false
                        true
                    }
                    else -> false
                }
            } else if (e.type == KeyEventType.KeyDown && onKey != null) {
                onKey(e.key)
            } else false
        }
        .semantics(mergeDescendants = true) {
            this.role = role
            if (selected != null) this.selected = selected
            onClick(label = label) { if (enabled) onClick(); enabled }
            if (onLongClick != null) onLongClick { onLongClick(); true }
            if (label != null) contentDescription = label
        }
        .pointerInput(enabled, onLongClick) {
            if (enabled) detectTapGestures(
                onPress = { state.pressed = true; tryAwaitRelease(); state.pressed = false },
                onLongPress = onLongClick?.let { l -> { _ -> l() } },
                onTap = { onClick() },
            )
        }
        .focusable(enabled)
}

/**
 * The tvOS tile focus effect: lift and scale with a deep soft shadow, a specular sheen from
 * the top, and a brief lean toward the direction focus came from before settling.
 */
@Composable
fun Modifier.tileFocusEffect(
    state: TvState,
    shape: Shape = LumenRadii.Tile,
    focusScale: Float = LumenMotion.TileFocusScale,
    lift: Dp = LumenMotion.TileLift,
    shadow: Dp = LumenMotion.TileShadow,
    sheen: Boolean = true,
    lean: Boolean = true,
): Modifier {
    val reduce = LocalReduceMotion.current
    val focused = state.focused
    val target = when {
        state.pressed -> LumenMotion.PressScale * (if (focused) focusScale else 1f)
        focused -> focusScale
        else -> 1f
    }
    val scale by animateFloatAsState(
        target, if (reduce) tween(0) else if (state.pressed) LumenMotion.press() else LumenMotion.focus(), label = "scale",
    )
    val elevation by animateDpAsState(if (focused) shadow else 0.dp, if (reduce) tween(0) else LumenMotion.focus(), label = "shadow")
    val sheenAlpha by animateFloatAsState(if (focused && sheen) 1f else 0f, if (reduce) tween(0) else tween(300), label = "sheen")
    val liftPx = with(LocalDensity.current) { lift.toPx() }
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var sheenX by remember { mutableStateOf(0.3f) }

    LaunchedEffect(focused) {
        if (!focused || reduce || !lean) { rotX.snapTo(0f); rotY.snapTo(0f); return@LaunchedEffect }
        val dir = FocusTravel.last
        val d = LumenMotion.LeanDegrees
        val (x, y) = when (dir) { Travel.Left -> 0f to d; Travel.Right -> 0f to -d; Travel.Up -> -d to 0f; Travel.Down -> d to 0f; Travel.None -> 0f to 0f }
        sheenX = when (dir) { Travel.Left -> 0.2f; Travel.Right -> 0.8f; else -> 0.5f }
        launch { rotX.animateTo(x, LumenMotion.quick()); kotlinx.coroutines.delay(LumenMotion.LeanMillis); rotX.animateTo(0f, LumenMotion.settle()) }
        launch { rotY.animateTo(y, LumenMotion.quick()); kotlinx.coroutines.delay(LumenMotion.LeanMillis); rotY.animateTo(0f, LumenMotion.settle()) }
    }
    LaunchedEffect(state.bump) {
        if (state.bump.first == 0 || reduce) return@LaunchedEffect
        scope.launch {
            val sign = if (state.bump.second == Travel.Left || state.bump.second == Travel.Up) -1f else 1f
            nudge.animateTo(sign * 6f, tween(90)); nudge.animateTo(0f, LumenMotion.settle())
        }
    }
    val horizontalBump = state.bump.second == Travel.Left || state.bump.second == Travel.Right
    return this
        .graphicsLayer {
            scaleX = scale; scaleY = scale
            translationY = if (focused) -liftPx * ((scale - 1f) / (focusScale - 1f)).coerceIn(0f, 1f) else 0f
            if (horizontalBump) translationX = nudge.value * density else translationY += nudge.value * density
            rotationX = rotX.value; rotationY = rotY.value
            cameraDistance = 14f * density
            shadowElevation = elevation.toPx()
            ambientShadowColor = Color.Black
            spotShadowColor = Color.Black
            this.shape = shape
            clip = true
        }
        .drawWithContent {
            drawContent()
            if (sheenAlpha > 0f) drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.30f * sheenAlpha), 0.7f to Color.Transparent,
                    center = Offset(size.width * sheenX, 0f), radius = size.width * 0.75f,
                ),
            )
        }
}

/** Scale and press for buttons, tabs, segments, keys and list rows (the colour change is up to the caller). */
@Composable
fun Modifier.controlFocusEffect(state: TvState, shape: Shape, focusScale: Float = LumenMotion.ButtonFocusScale, shadow: Dp = LumenMotion.ButtonShadow): Modifier =
    tileFocusEffect(state, shape, focusScale, lift = 0.dp, shadow = shadow, sheen = false, lean = false)
