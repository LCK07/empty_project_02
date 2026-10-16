package com.lck07.lumen.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lck07.lumen.design.LumenType.tabular

/** Text in the Lumen type scale. Long text ends with an ellipsis rather than breaking layouts. */
@Composable
fun Txt(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LumenColors.Label,
    maxLines: Int = 1,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (align != null) style.copy(color = color, textAlign = align) else style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        softWrap = maxLines > 1,
    )
}

@Composable
fun Icon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = LumenColors.Label, size: Dp = LumenSizes.Icon, contentDescription: String? = null) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/**
 * The tvOS button: a translucent fill that turns white with black text when focused and
 * scales up; it shrinks on press. With [text] null it is a round icon button and
 * [label] names it for TalkBack.
 */
@Composable
fun LumenButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
    small: Boolean = false,
    focusRequester: FocusRequester? = null,
    onKey: ((Key) -> Boolean)? = null,
    state: TvState = rememberTvState(),
) {
    val shape: Shape = if (small) LumenRadii.ButtonSmall else LumenRadii.Button
    val bg by animateColorAsState(
        when { state.focused -> LumenColors.Focused; else -> LumenColors.Fill },
        tween(160), label = "bg",
    )
    val fg by animateColorAsState(
        when { state.focused -> LumenColors.OnFocused; !enabled -> LumenColors.Label3; else -> LumenColors.Label },
        tween(160), label = "fg",
    )
    val height = if (small) 30.dp else LumenSizes.ButtonHeight
    Row(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .controlFocusEffect(state, shape)
            .background(bg, shape)
            .tvClickable(state, enabled, label = if (text == null) label else null, selected = selected, onKey = onKey, onClick = onClick)
            .height(height)
            .then(if (text == null) Modifier.width(height) else Modifier.padding(horizontal = if (small) 13.dp else 19.dp)),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, tint = fg, size = if (small) 13.dp else LumenSizes.Icon)
        if (text != null) Txt(text, if (small) LumenType.Caption1Strong else LumenType.Callout, color = fg)
    }
}

/** A thin rounded progress track. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = LumenColors.Label, track: Color = LumenColors.Track, height: Dp = LumenSizes.ProgressHeight) {
    Box(modifier.height(height).clip(LumenRadii.Capsule).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(LumenRadii.Capsule).background(color))
    }
}

/** The red LIVE badge. It always has text, so colour is never the only signal. */
@Composable
fun LiveBadge(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(LumenRadii.Badge).background(LumenColors.Live).padding(horizontal = 5.dp, vertical = 3.5.dp)) {
        Txt(text, LumenType.Badge.copy(fontSize = LumenType.Badge.fontSize * 0.75f))
    }
}

/** The blue "new" badge on posters. */
@Composable
fun NewBadge(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCorner4).background(LumenColors.Accent).padding(horizontal = 5.dp, vertical = 3.5.dp)) {
        Txt(text, LumenType.Badge.copy(fontSize = LumenType.Badge.fontSize * 0.8f, letterSpacing = LumenType.Caption2.letterSpacing))
    }
}

private val RoundedCorner4 = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)

/** An outlined age-rating pill ("16+"). */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp, LumenColors.Label3, RoundedCorner3).padding(horizontal = 4.dp)) {
        Txt(text, LumenType.Caption2.copy(fontSize = LumenType.Caption2.fontSize * 0.87f, lineHeight = LumenType.Caption2.fontSize * 1.2f), color = LumenColors.Label2)
    }
}

private val RoundedCorner3 = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)

/** "2025 · Drama · 2 Seasons" with an optional rating pill. */
@Composable
fun MetaLine(parts: List<String>, modifier: Modifier = Modifier, pill: String? = null, color: Color = LumenColors.Label2, style: TextStyle = LumenType.Caption1) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.filter { it.isNotBlank() }.forEachIndexed { i, p ->
            if (i > 0) Txt("·", style, color = color)
            Txt(p, style.tabular(), color = color)
        }
        if (pill != null) Pill(pill)
    }
}

/** An uppercase label above a hero title. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = LumenColors.Label2) {
    Txt(text.uppercase(), LumenType.Eyebrow, modifier, color)
}

/**
 * A segmented control (seasons): a glass capsule of segments. The selected segment has a
 * stronger fill; the focused one is white with black text.
 */
@Composable
fun <T> SegmentedControl(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    selectedRequester: FocusRequester? = null,
) {
    Glass(LumenRadii.Capsule, modifier) {
        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, item ->
                val state = rememberTvState()
                val isSel = item == selected
                val bg by animateColorAsState(
                    when { state.focused -> LumenColors.Focused; isSel -> LumenColors.Fill2; else -> Color.Transparent }, tween(160), label = "seg",
                )
                val fg = when { state.focused -> LumenColors.OnFocused; isSel -> LumenColors.Label; else -> LumenColors.Label2 }
                Box(
                    Modifier
                        .then(if (isSel && selectedRequester != null) Modifier.focusRequester(selectedRequester) else Modifier)
                        .controlFocusEffect(state, LumenRadii.Capsule, LumenMotion.SegmentFocusScale, shadow = 8.dp)
                        .background(bg, LumenRadii.Capsule)
                        .tvClickable(state, role = Role.Tab, selected = isSel, onKey = { k ->
                            // A soft bump at the ends instead of leaving the control sideways.
                            if ((k == Key.DirectionLeft && i == 0) || (k == Key.DirectionRight && i == items.lastIndex)) {
                                state.nudge(if (k == Key.DirectionLeft) Travel.Left else Travel.Right); true
                            } else false
                        }) { onSelect(item) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) { Txt(label(item), LumenType.Caption1Strong, color = fg) }
            }
        }
    }
}

/** Centred message with an optional action, for empty and error states (errors say how to fix it). */
@Composable
fun MessageState(
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    actionRequester: FocusRequester? = null,
) {
    androidx.compose.foundation.layout.Column(
        modifier.widthIn(max = 460.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(icon, tint = LumenColors.Label2, size = 34.dp)
        Txt(title, LumenType.Title3, align = TextAlign.Center, maxLines = 2)
        if (message != null) Txt(message, LumenType.Body, color = LumenColors.Label2, align = TextAlign.Center, maxLines = 4)
        if (action != null && onAction != null) {
            LumenButton(action, onAction, Modifier.padding(top = 8.dp), focusRequester = actionRequester)
        }
    }
}

/** A row of buttons with the tvOS spacing. */
@Composable
fun ButtonRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.heightIn(min = LumenSizes.ButtonHeight), horizontalArrangement = Arrangement.spacedBy(LumenSpacing.ButtonGap), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** The activity indicator: eight ticks fading round, as on Apple platforms. */
@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 28.dp, color: Color = LumenColors.Label) {
    val reduce = LocalReduceMotion.current
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "spinner")
    val phase by t.animateFloat(
        0f, 8f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.LinearEasing)),
        label = "phase",
    )
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val step = if (reduce) 0 else phase.toInt()
        val w = this.size.minDimension
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45.0) - 90)
            val alpha = 0.25f + 0.75f * (((i - step + 8) % 8) / 7f)
            val inner = w * 0.22f; val outer = w * 0.46f
            drawLine(
                color.copy(alpha = alpha),
                androidx.compose.ui.geometry.Offset(center.x + inner * kotlin.math.cos(a).toFloat(), center.y + inner * kotlin.math.sin(a).toFloat()),
                androidx.compose.ui.geometry.Offset(center.x + outer * kotlin.math.cos(a).toFloat(), center.y + outer * kotlin.math.sin(a).toFloat()),
                strokeWidth = w * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}
