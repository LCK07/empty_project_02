package com.lck07.lumen.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lck07.lumen.R

/*
  Design tokens. The mockup is drawn in tvOS points on a 1920 x 1080 canvas; Android TV at
  1080p is 960 x 540 dp, so every point value is halved here. Screens use only these tokens.
*/

/** Colours (dark appearance only), from apple-hig-web/tokens.css and the mockup. */
object LumenColors {
    val Background = Color.Black
    val Label = Color.White
    /** Secondary text: rgba(235, 235, 245, 0.68). */
    val Label2 = Color(235, 235, 245).copy(alpha = 0.68f)
    /** Tertiary text: rgba(235, 235, 245, 0.42). */
    val Label3 = Color(235, 235, 245).copy(alpha = 0.42f)
    val Fill = Color.White.copy(alpha = 0.14f)
    val Fill2 = Color.White.copy(alpha = 0.22f)
    val FillSubtle = Color.White.copy(alpha = 0.07f)
    val FillRow = Color.White.copy(alpha = 0.06f)
    val FillNow = Color.White.copy(alpha = 0.13f)
    val Track = Color.White.copy(alpha = 0.25f)
    val Separator = Color(84, 84, 88).copy(alpha = 0.6f)
    val TileBackground = Color(0xFF1C1C1E)
    val Live = Color(0xFFFF4245)
    val Accent = Color(0xFF0091FF)
    /** Focused controls are white with black text, as on Apple TV. */
    val Focused = Color.White
    val OnFocused = Color.Black
    val OnFocused2 = Color.Black.copy(alpha = 0.6f)
    val Scrim = Color.Black

    /** Glass (translucent chrome). The *Opaque variants are used where blur is unavailable. */
    val Glass = Color(60, 60, 67).copy(alpha = 0.42f)
    val GlassStrong = Color(28, 28, 30).copy(alpha = 0.72f)
    val GlassOpaque = Color(38, 38, 42).copy(alpha = 0.94f)
    val GlassStrongOpaque = Color(22, 22, 24).copy(alpha = 0.95f)
    val GlassHighlight = Color.White.copy(alpha = 0.16f)
    val GlassEdge = Color.White.copy(alpha = 0.08f)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

private val Trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

private fun style(size: Float, weight: FontWeight = FontWeight.Medium, line: Float = 1.2f, tracking: Float = -0.01f) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * line).sp,
    letterSpacing = tracking.em,
    lineHeightStyle = Trim,
    fontFeatureSettings = "cv11, ss01",
)

/** tvOS text styles in sp (points / 2). Caption 2 is the smallest size used anywhere. */
object LumenType {
    /** Hero titles: Title 1, bolder and tighter, as in the mockup (112 pt). */
    val Hero = style(56f, FontWeight.ExtraBold, line = 0.98f, tracking = -0.035f)
    val HeroSmall = style(48f, FontWeight.ExtraBold, line = 1.0f, tracking = -0.035f)
    val Title1 = style(38f)
    val Title2 = style(28.5f, FontWeight.Bold, tracking = -0.02f)
    val Title3 = style(24f, FontWeight.Bold, line = 1.1f, tracking = -0.02f)
    val Headline = style(19f, FontWeight.SemiBold, tracking = -0.015f)
    val Callout = style(15.5f, FontWeight.SemiBold)
    val Body = style(14.5f, FontWeight.Medium, line = 1.25f)
    val BodyLong = style(14.5f, FontWeight.Normal, line = 1.38f)
    val Caption1 = style(12.5f, FontWeight.Medium, line = 1.3f)
    val Caption1Strong = style(12.5f, FontWeight.SemiBold, line = 1.2f)
    val Caption2 = style(11.5f, FontWeight.Medium, line = 1.3f)
    val Caption2Strong = style(11.5f, FontWeight.SemiBold, line = 1.25f)
    val Eyebrow = style(11.5f, FontWeight.SemiBold, tracking = 0.08f)
    val Badge = style(11.5f, FontWeight.ExtraBold, line = 1f, tracking = 0.06f)
    val PosterTitle = style(17f, FontWeight.ExtraBold, line = 1f, tracking = -0.03f)
    val Logo = style(12f, FontWeight.ExtraBold, line = 1f, tracking = -0.02f)

    /** Tabular figures for times, durations and channel numbers. */
    fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum, cv11")
}

/** Layout values in dp. */
object LumenSpacing {
    /** Safe area: 80 x 60 pt. Artwork runs edge to edge; text and controls stay inside. */
    val SafeX = 40.dp
    val SafeY = 30.dp
    /** Grid gutter (40 pt). */
    val Gutter = 20.dp
    val RowGap = 28.dp
    val RowTitleGap = 11.dp
    /** Space above and below a row of tiles so the focus lift and shadow aren't clipped. */
    val RowPadTop = 9.dp
    val RowPadBottom = 13.dp
    val ButtonGap = 12.dp
    val Screen = 960.dp
    val ScreenHeight = 540.dp
}

object LumenSizes {
    val EpisodeW = 260.dp; val EpisodeH = 146.dp
    val EpisodeSmallW = 220.dp; val EpisodeSmallH = 124.dp
    val PosterW = 130.dp; val PosterH = 195.dp
    val ChannelW = 200.dp; val ChannelH = 112.5.dp
    val ButtonHeight = 38.dp
    val IconButton = 38.dp
    val Icon = 15.dp
    val TabHeight = 29.dp
    val ProgressHeight = 3.dp
}

object LumenRadii {
    val Tile = RoundedCornerShape(11.dp)
    val Button = RoundedCornerShape(9.dp)
    val ButtonSmall = RoundedCornerShape(7.dp)
    val Cell = RoundedCornerShape(7.dp)
    val Row = RoundedCornerShape(10.dp)
    val Banner = RoundedCornerShape(15.dp)
    val Key = RoundedCornerShape(6.dp)
    val Badge = RoundedCornerShape(3.5.dp)
    val Logo = RoundedCornerShape(5.dp)
    val Capsule = RoundedCornerShape(percent = 50)
}

/** Motion. Focus uses springs (damping ~0.85, response ~0.35 s) so it can be interrupted. */
object LumenMotion {
    /** Stiffness for a 0.35 s response: (2 pi / 0.35)^2. */
    const val FocusStiffness = 322f
    const val FocusDamping = 0.82f
    fun <T> focus(): SpringSpec<T> = spring(dampingRatio = FocusDamping, stiffness = FocusStiffness)
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 3000f)
    fun <T> quick(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

    const val TileFocusScale = 1.1f
    const val ButtonFocusScale = 1.08f
    const val SegmentFocusScale = 1.06f
    const val RowFocusScale = 1.04f
    const val PressScale = 0.96f
    const val LeanDegrees = 3.5f
    const val LeanMillis = 260L
    val TileLift: Dp = 3.dp
    val TileShadow: Dp = 22.dp
    val ButtonShadow: Dp = 12.dp

    const val OverlayTimeoutMs = 4_000L
}
