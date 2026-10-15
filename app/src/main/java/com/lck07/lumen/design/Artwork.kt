package com.lck07.lumen.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.abs

/**
 * A soft, colourful gradient derived from a string, standing in for missing artwork (as the
 * mockup does). The same title always gets the same colours.
 */
fun Modifier.generatedArt(seed: String, variant: Int = 0): Modifier = drawWithCache {
    val h = abs(seed.hashCode() % 360).toFloat()
    val v = abs(variant + seed.length) % 7
    fun hsl(hue: Float, s: Float, l: Float, a: Float = 1f) = Color.hsl(((hue % 360f) + 360f) % 360f, s, l, a)
    val a = h; val b = h + 40 + v * 13; val c = h + 200
    val w = size.width; val ht = size.height
    val base = Brush.linearGradient(listOf(hsl(c, 0.35f, 0.14f), hsl(a, 0.45f, 0.06f)), Offset(w * 0.2f, 0f), Offset(w * 0.8f, ht))
    val glowA = Brush.radialGradient(
        listOf(hsl(a, 0.78f, 0.62f, 0.95f), hsl(a, 0.78f, 0.5f, 0f)),
        center = Offset(w * (0.62f + (v % 3) * 0.08f), ht * (0.28f + (v % 4) * 0.06f)), radius = maxOf(w, ht) * 0.62f,
    )
    val glowB = Brush.radialGradient(
        listOf(hsl(b, 0.7f, 0.42f, 0.9f), hsl(b, 0.7f, 0.3f, 0f)),
        center = Offset(w * (0.18f + (v % 5) * 0.04f), ht * 0.88f), radius = maxOf(w, ht) * 0.55f,
    )
    onDrawBehind {
        drawRect(base)
        drawRect(glowB)
        drawRect(glowA)
    }
}

/**
 * Artwork from the server with the generated gradient behind it, so a missing or slow image
 * never leaves a hole. [onPainter] receives the loaded image (used by glass backdrops).
 */
@Composable
fun Artwork(
    url: String?,
    seed: String,
    modifier: Modifier = Modifier,
    variant: Int = 0,
    contentScale: ContentScale = ContentScale.Crop,
    crossfade: Boolean = true,
    onPainter: ((Painter?) -> Unit)? = null,
    fallback: @Composable BoxScope.(loaded: Boolean) -> Unit = {},
) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.generatedArt(seed, variant)) {
        if (!url.isNullOrBlank()) {
            val context = LocalContext.current
            val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(crossfade).build() }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onState = { s ->
                    loaded = s is AsyncImagePainter.State.Success
                    onPainter?.invoke((s as? AsyncImagePainter.State.Success)?.painter)
                },
            )
        }
        fallback(loaded)
    }
}
