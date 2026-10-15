package com.lck07.lumen.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The mockup's icons (24 x 24 viewport, filled or stroked), tinted by the caller. */
object LumenIcons {
    private fun icon(name: String, vararg paths: Pair<String, Float?>): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { (d, stroke) ->
                if (stroke == null) addPath(addPathNodes(d), fill = SolidColor(Color.White))
                else addPath(
                    addPathNodes(d), fill = null, stroke = SolidColor(Color.White), strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},$y h${w - 2 * r} a$r,$r 0 0 1 $r,$r v${h - 2 * r} a$r,$r 0 0 1 ${-r},$r h${-(w - 2 * r)} a$r,$r 0 0 1 ${-r},${-r} v${-(h - 2 * r)} a$r,$r 0 0 1 $r,${-r} z"

    private const val HEART = "M12 20s-7.5-4.6-9.3-9.2C1.5 7.6 3.6 4.5 7 4.5c2 0 3.6 1.1 5 3 1.4-1.9 3-3 5-3 3.4 0 5.5 3.1 4.3 6.3C19.5 15.4 12 20 12 20z"

    val Play = icon("play", "M7 4.5v15a1 1 0 0 0 1.5.86l12.5-7.5a1 1 0 0 0 0-1.72L8.5 3.64A1 1 0 0 0 7 4.5z" to null)
    val Pause = icon("pause", rect(5f, 4f, 5f, 16f, 1.2f) to null, rect(14f, 4f, 5f, 16f, 1.2f) to null)
    val Restart = icon("restart", "M3 12a9 9 0 1 0 3-6.7L3 8" to 2.2f, "M3 3v5h5" to 2.2f)
    val List = icon("list", "M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01" to 2.2f)
    val Heart = icon("heart", HEART to 2.2f)
    val HeartFilled = icon("heartFilled", HEART to null)
    val Search = icon("search", circle(11f, 11f, 7f) to 2.4f, "m20 20-3.5-3.5" to 2.4f)
    val Gear = icon(
        "gear", circle(12f, 12f, 3f) to 2f,
        ("M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1" +
            "a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4" +
            "h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0" +
            "v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4" +
            "h-.1a1.7 1.7 0 0 0-1.5 1z") to 2f,
    )
    val Check = icon("check", "M20 6 9 17l-5-5" to 3f)
    val Chevron = icon("chevron", "m9 18 6-6-6-6" to 2.4f)
    val Subtitles = icon("subtitles", rect(3f, 5f, 18f, 14f, 3f) to 2f, "M10 10.5a2 2 0 1 0 0 3M17 10.5a2 2 0 1 0 0 3" to 2f)
    val Audio = icon("audio", "M11 5 6 9H3v6h3l5 4z" to 2f, "M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13" to 2f)
    val Next = icon("next", "M5 5.5v13a1 1 0 0 0 1.5.86L16 13.7V18a1 1 0 0 0 2 0V6a1 1 0 0 0-2 0v4.3L6.5 4.64A1 1 0 0 0 5 5.5z" to null)
    val Delete = icon("delete", "M21 5H9l-7 7 7 7h12a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1z" to 2f, "m17 9-6 6M11 9l6 6" to 2f)
    val Space = icon("space", "M4 10v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4" to 2.2f)
    val Mic = icon("mic", rect(9f, 3f, 6f, 11f, 3f) to 2f, "M5 11a7 7 0 0 0 14 0M12 18v3" to 2f)
    val Lock = icon("lock", rect(5f, 11f, 14f, 10f, 2f) to 2f, "M8 11V7a4 4 0 0 1 8 0v4" to 2f)
    val Guide = List
    val Warning = icon("warning", "M12 3 2 20h20z" to 2f, "M12 10v4M12 17h.01" to 2.2f)
    val Wifi = icon("wifi", "M2 8.8a15 15 0 0 1 20 0M5 12.6a10 10 0 0 1 14 0M8.5 16.4a5 5 0 0 1 7 0M12 20h.01" to 2.2f)
}
