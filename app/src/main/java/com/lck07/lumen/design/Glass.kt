package com.lck07.lumen.design

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/** Real blur needs RenderEffect (Android 12+). Older devices, like the Fire TV stick, get a darker fill. */
val BlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The artwork currently behind floating chrome (the hero backdrop). Glass draws a blurred copy
 * of it, aligned to the screen, so the tab bar looks like frosted glass over the picture.
 */
class Backdrop {
    var painter: Painter? by mutableStateOf(null)
    var bounds: LayoutCoordinates? by mutableStateOf(null)
}

val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/** Registers the composable it modifies as the backdrop that glass blurs. */
fun Modifier.backdropSource(backdrop: Backdrop?, painter: Painter?): Modifier =
    if (backdrop == null) this else onGloballyPositioned { backdrop.painter = painter; backdrop.bounds = it }

/**
 * Glass material for chrome only (tab bar, player overlays, banners, the channel strip):
 * a translucent dark fill with a blurred backdrop where the platform can blur, and a nearly
 * opaque fill where it can't. Never used in the content layer.
 */
@Composable
fun Glass(
    shape: Shape,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val painter = backdrop?.painter
    val canBlur = BlurSupported && painter != null && !LocalReduceTransparency.current
    var self by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val fill = when {
        canBlur -> if (strong) LumenColors.GlassStrong else LumenColors.Glass
        else -> if (strong) LumenColors.GlassStrongOpaque else LumenColors.GlassOpaque
    }
    Box(
        modifier
            .onGloballyPositioned { self = it }
            .clip(shape)
            .drawWithContent {
                drawContent()
                // Inner highlight along the top edge, as on tvOS glass.
                drawRect(Brush.verticalGradient(0f to LumenColors.GlassHighlight, 0.08f to Color.Transparent))
            }
            .border(0.5.dp, LumenColors.GlassEdge, shape),
    ) {
        if (canBlur) {
            val src = backdrop!!.bounds
            val me = self
            if (src != null && me != null && src.isAttached && me.isAttached) {
                val offset: Offset = src.positionInRoot() - me.positionInRoot()
                Image(
                    painter = painter!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .graphicsLayer { translationX = offset.x; translationY = offset.y }
                        .layoutToSize(src.size)
                        .blur(30.dp),
                )
            }
        }
        Box(Modifier.matchParentSize().background(fill))
        content()
    }
}

private fun Modifier.layoutToSize(size: androidx.compose.ui.unit.IntSize): Modifier =
    this.then(Modifier.layout { measurable, _ ->
        val p = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(size.width, size.height))
        layout(0, 0) { p.place(0, 0) }
    })

/** "Reduce transparency": glass falls back to the opaque fill. Follows the high-contrast text setting. */
val LocalReduceTransparency = compositionLocalOf { false }

/** The dark scrim behind hero text: left-to-right and bottom-up, as in the mockup. */
fun Modifier.heroScrim(): Modifier = drawWithContent {
    drawContent()
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.78f), 0.45f to Color.Black.copy(alpha = 0.35f), 0.7f to Color.Transparent,
        ),
    )
    drawRect(
        Brush.verticalGradient(
            0.5f to Color.Transparent, 0.78f to Color.Black.copy(alpha = 0.6f), 1f to Color.Black,
        ),
    )
}
