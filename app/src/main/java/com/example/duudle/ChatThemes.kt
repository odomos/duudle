package com.example.duudle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duudle.ui.theme.DuudleColors

// Shared per-chat themes. Each theme is plain data plus a few tiny Canvas ornaments,
// all original shapes (horns, wings, x marks, hearts, fish, flowers). Ornaments sit in
// the bubble's padding or around the name tag, never over the text, and their paths
// are built once per size with drawWithCache. Nothing animates.

enum class ChatPattern { NONE, GRID, DIAMONDS, DOTS }

/** Text colour vs fill is at least 4.5:1 for every bubble below. */
@Immutable
data class BubbleStyle(
    val fill: Color,
    val text: Color,
    val border: Color? = null,
    val borderWidth: Dp = 0.dp,
    /** Dashed inner stitch line, if the theme has one. */
    val stitch: Color? = null,
    /** Colour of the little corner ornaments on this bubble. */
    val ornament: Color = Color.Transparent
)

@Immutable
data class ChatTheme(
    val id: ChatThemeId,
    val background: Color,
    val pattern: ChatPattern,
    val patternColor: Color,
    val mine: BubbleStyle,
    val theirs: BubbleStyle,
    val nameTagFill: Color,
    val nameTagText: Color,
    /** Time labels drawn straight on the background. */
    val meta: Color,
    /** Pill behind day separators and theme notes. */
    val noteFill: Color,
    val noteText: Color,
    /** Name-tag ornaments (horns, wings, fish...). */
    val accent: Color,
    val accent2: Color,
    val cornerRadius: Dp,
    /** Serpent's bubbles have a notched outer edge instead of rounded corners. */
    val jagged: Boolean = false
)

private val ClassicTheme = ChatTheme(
    id = ChatThemeId.CLASSIC,
    background = DuudleColors.Cream,
    pattern = ChatPattern.NONE,
    patternColor = Color.Transparent,
    mine = BubbleStyle(fill = DuudleColors.Ink, text = DuudleColors.Paper),
    theirs = BubbleStyle(fill = DuudleColors.Paper, text = DuudleColors.Ink, border = DuudleColors.Hairline, borderWidth = 1.dp),
    nameTagFill = DuudleColors.Lilac.light,
    nameTagText = DuudleColors.Ink,
    meta = DuudleColors.Muted,
    noteFill = DuudleColors.Paper,
    noteText = DuudleColors.Muted,
    accent = Color.Transparent,
    accent2 = Color.Transparent,
    cornerRadius = 18.dp
)

private val GothicTheme = ChatTheme(
    id = ChatThemeId.GOTHIC,
    background = Color(0xFFF9E1EA),
    pattern = ChatPattern.DOTS,
    patternColor = Color(0xFFF0C3D4),
    mine = BubbleStyle(fill = Color(0xFF2B2A2E), text = Color(0xFFFFE3EE), stitch = Color(0xFFFF9EC2), ornament = Color(0xFFFF9EC2)),
    theirs = BubbleStyle(
        fill = Color(0xFFFFD1E1), text = Color(0xFF2B2A2E), border = Color(0xFF2B2A2E), borderWidth = 1.5.dp,
        stitch = Color(0xFFC9879F), ornament = Color(0xFF2B2A2E)
    ),
    nameTagFill = Color(0xFF2B2A2E),
    nameTagText = Color(0xFFFFC1D9),
    meta = Color(0xFF6B3A50),
    noteFill = Color(0xFFFFF0F5),
    noteText = Color(0xFF6B3A50),
    accent = Color(0xFF2B2A2E),
    accent2 = Color(0xFFFF7FAF),
    cornerRadius = 16.dp
)

private val InfernoTheme = ChatTheme(
    id = ChatThemeId.INFERNO,
    background = Color(0xFF140B0B),
    pattern = ChatPattern.DOTS,
    patternColor = Color(0xFF3A1A1A),
    mine = BubbleStyle(
        fill = Color(0xFF1C1113), text = Color(0xFFF5E6D3), border = Color(0xFF8B1A1A), borderWidth = 2.dp,
        ornament = Color(0xFFD4A84A)
    ),
    theirs = BubbleStyle(
        fill = Color(0xFF241417), text = Color(0xFFF5E6D3), border = Color(0xFFB22222), borderWidth = 2.dp,
        ornament = Color(0xFFD4A84A)
    ),
    nameTagFill = Color(0xFF8B1A1A),
    nameTagText = Color(0xFFF3D27A),
    meta = Color(0xFFC9B79C),
    noteFill = Color(0xFF2A1A1A),
    noteText = Color(0xFFE8D5B5),
    accent = Color(0xFFB22222),
    accent2 = Color(0xFFD4A84A),
    cornerRadius = 14.dp
)

private val PookieTheme = ChatTheme(
    id = ChatThemeId.POOKIE,
    background = Color(0xFFFFE4F0),
    pattern = ChatPattern.DIAMONDS,
    patternColor = Color(0xFFFFC2DD),
    mine = BubbleStyle(fill = Color(0xFF141414), text = Color(0xFFFFD6EA), ornament = Color(0xFFFF8FC2)),
    theirs = BubbleStyle(
        fill = Color(0xFFFF9EC9), text = Color(0xFF141414), border = Color(0xFF141414), borderWidth = 1.5.dp,
        ornament = Color(0xFF141414)
    ),
    nameTagFill = Color(0xFF141414),
    nameTagText = Color(0xFFFFB3D6),
    meta = Color(0xFF7A2550),
    noteFill = Color(0xFFFFF5FA),
    noteText = Color(0xFF7A2550),
    accent = Color(0xFF141414),
    accent2 = Color(0xFFE8367F),
    cornerRadius = 20.dp
)

private val SerpentTheme = ChatTheme(
    id = ChatThemeId.SERPENT,
    background = Color(0xFFEDEDED),
    pattern = ChatPattern.NONE,
    patternColor = Color.Transparent,
    mine = BubbleStyle(fill = Color(0xFF111111), text = Color(0xFFFFFFFF), ornament = Color(0xFFBDBDBD)),
    theirs = BubbleStyle(
        fill = Color(0xFFFFFFFF), text = Color(0xFF111111), border = Color(0xFF111111), borderWidth = 1.5.dp,
        ornament = Color(0xFF111111)
    ),
    nameTagFill = Color(0xFF111111),
    nameTagText = Color(0xFFFFFFFF),
    meta = Color(0xFF3A3A3A),
    noteFill = Color(0xFFFFFFFF),
    noteText = Color(0xFF3A3A3A),
    accent = Color(0xFF111111),
    accent2 = Color(0xFF111111),
    cornerRadius = 10.dp,
    jagged = true
)

private val SpookyTheme = ChatTheme(
    id = ChatThemeId.SPOOKY,
    background = Color(0xFFEDE3FA),
    pattern = ChatPattern.DOTS,
    patternColor = Color(0xFFD5C2F2),
    mine = BubbleStyle(fill = Color(0xFF5B3A8C), text = Color(0xFFFFFFFF), ornament = Color(0xFFF2D27A)),
    theirs = BubbleStyle(
        fill = Color(0xFFF6F0FF), text = Color(0xFF2E1A47), border = Color(0xFF8A6CC2), borderWidth = 1.5.dp,
        ornament = Color(0xFFB58CF2)
    ),
    nameTagFill = Color(0xFF2E1A47),
    nameTagText = Color(0xFFE9D8FF),
    meta = Color(0xFF4A2F73),
    noteFill = Color(0xFFF8F3FF),
    noteText = Color(0xFF4A2F73),
    accent = Color(0xFF2E1A47),
    accent2 = Color(0xFFB58CF2),
    cornerRadius = 18.dp
)

private val PondTheme = ChatTheme(
    id = ChatThemeId.POND,
    background = Color(0xFFF2FBF4),
    pattern = ChatPattern.GRID,
    patternColor = Color(0xFFD3EEDC),
    mine = BubbleStyle(fill = Color(0xFF2A6F50), text = Color(0xFFFFFFFF), ornament = Color(0xFFF6C945)),
    theirs = BubbleStyle(
        fill = Color(0xFFFFFFFF), text = Color(0xFF1F3D2E), border = Color(0xFF7CC8A0), borderWidth = 2.dp,
        ornament = Color(0xFFF28C38)
    ),
    nameTagFill = Color(0xFFBDEBD0),
    nameTagText = Color(0xFF1F3D2E),
    meta = Color(0xFF2E5E45),
    noteFill = Color(0xFFFFFFFF),
    noteText = Color(0xFF2E5E45),
    accent = Color(0xFFF28C38),
    accent2 = Color(0xFFF6C945),
    cornerRadius = 18.dp
)

val AllChatThemes: List<ChatTheme> = listOf(
    ClassicTheme, GothicTheme, InfernoTheme, PookieTheme, SerpentTheme, SpookyTheme, PondTheme
)

fun chatThemeFor(id: ChatThemeId): ChatTheme = AllChatThemes.firstOrNull { it.id == id } ?: ClassicTheme

/** Horizontal padding inside a bubble: wide enough that corner ornaments and notches stay clear of the text. */
val BubbleHorizontalPadding = 16.dp
val BubbleVerticalPadding = 10.dp

/**
 * Bubble outline. Grouped bubbles get a tight corner on the sender's side where they
 * meet the next one; Serpent uses its notched shape instead.
 */
fun chatBubbleShape(theme: ChatTheme, mine: Boolean, firstInGroup: Boolean, lastInGroup: Boolean): Shape {
    if (theme.jagged) return JaggedBubbleShape(theme.cornerRadius, notchOnEnd = mine)
    val r = theme.cornerRadius
    val tight = 6.dp
    val top = if (firstInGroup) r else tight
    val bottom = if (lastInGroup) r else tight
    return if (mine) {
        RoundedCornerShape(topStart = r, topEnd = top, bottomEnd = bottom, bottomStart = r)
    } else {
        RoundedCornerShape(topStart = top, topEnd = r, bottomEnd = r, bottomStart = bottom)
    }
}

/** Rounded on one side, a row of notches down the other (the outer side). */
private class JaggedBubbleShape(private val radius: Dp, private val notchOnEnd: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val right = notchOnEnd == (layoutDirection == LayoutDirection.Ltr)
        val w = size.width
        val h = size.height
        val r = with(density) { radius.toPx() }.coerceAtMost(minOf(w, h) / 2f)
        val depth = with(density) { 5.dp.toPx() }
        val step = with(density) { 10.dp.toPx() }
        fun fx(x: Float) = if (right) x else w - x
        val teeth = (h / step).toInt().coerceAtLeast(2)
        val seg = h / teeth
        val path = Path().apply {
            moveTo(fx(r), 0f)
            lineTo(fx(w - depth), 0f)
            for (i in 0 until teeth) {
                lineTo(fx(w), i * seg + seg / 2f)
                lineTo(fx(w - depth), (i + 1) * seg)
            }
            lineTo(fx(r), h)
            quadraticTo(fx(0f), h, fx(0f), h - r)
            lineTo(fx(0f), r)
            quadraticTo(fx(0f), 0f, fx(r), 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Fills with the theme's background and its faint pattern (grid, diamonds or dots). */
fun Modifier.chatBackground(theme: ChatTheme): Modifier = drawWithCache {
    val s = 1.dp.toPx()
    val w = size.width
    val h = size.height
    val lines = Path()
    var dots: List<Offset> = emptyList()
    when (theme.pattern) {
        ChatPattern.GRID -> {
            val step = 22 * s
            var x = step
            while (x < w) { lines.moveTo(x, 0f); lines.lineTo(x, h); x += step }
            var y = step
            while (y < h) { lines.moveTo(0f, y); lines.lineTo(w, y); y += step }
        }
        ChatPattern.DIAMONDS -> {
            val step = 26 * s
            var c = 0f
            while (c < w + h) {
                lines.moveTo(c, 0f); lines.lineTo(c - h, h)
                lines.moveTo(c - h, 0f); lines.lineTo(c, h)
                c += step
            }
        }
        ChatPattern.DOTS -> {
            val step = 24 * s
            val points = ArrayList<Offset>()
            var row = 0
            var y = step / 2f
            while (y < h) {
                var x = if (row % 2 == 0) step / 2f else step
                while (x < w) { points.add(Offset(x, y)); x += step }
                y += step
                row++
            }
            dots = points
        }
        ChatPattern.NONE -> Unit
    }
    onDrawBehind {
        drawRect(theme.background)
        if (!lines.isEmpty) drawPath(lines, theme.patternColor, style = Stroke(width = s))
        if (dots.isNotEmpty()) {
            drawPoints(dots, PointMode.Points, theme.patternColor, strokeWidth = 3.5f * s, cap = StrokeCap.Round)
        }
    }
}

/** Fill, border and the theme's corner ornaments for one bubble. Pair with [chatBubbleShape]. */
fun Modifier.chatBubble(theme: ChatTheme, mine: Boolean, shape: Shape): Modifier {
    val style = if (mine) theme.mine else theme.theirs
    var m = this.clip(shape).background(style.fill, shape)
    if (style.border != null && style.borderWidth > 0.dp) m = m.border(style.borderWidth, style.border, shape)
    if (theme.id == ChatThemeId.CLASSIC) return m
    return m.drawWithCache {
        val s = 1.dp.toPx()
        val w = size.width
        val h = size.height
        // "Outer" is the side the bubble hugs: right for mine, left for theirs (flipped in RTL).
        val outerRight = mine == (layoutDirection == LayoutDirection.Ltr)
        fun outerX(inset: Float) = if (outerRight) w - inset else inset
        fun innerX(inset: Float) = if (outerRight) inset else w - inset
        val radius = theme.cornerRadius.toPx()
        val stitch = Stroke(width = 1.2f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * s, 3 * s)))
        val heart = if (theme.id == ChatThemeId.POOKIE) heartPath(Offset(innerX(9 * s), h - 9 * s), 3.5f * s) else null
        val sparkles = when (theme.id) {
            ChatThemeId.INFERNO -> listOf(sparklePath(Offset(outerX(8 * s), 8 * s), 3.2f * s))
            ChatThemeId.SPOOKY -> listOf(
                sparklePath(Offset(outerX(8 * s), 8 * s), 3.5f * s),
                sparklePath(Offset(innerX(7 * s), h - 7 * s), 2.4f * s)
            )
            else -> emptyList()
        }
        onDrawBehind {
            when (theme.id) {
                ChatThemeId.GOTHIC -> {
                    style.stitch?.let { c ->
                        val inset = 4 * s
                        drawRoundRect(
                            c, topLeft = Offset(inset, inset), size = Size(w - 2 * inset, h - 2 * inset),
                            cornerRadius = CornerRadius((radius - inset).coerceAtLeast(2 * s)), style = stitch
                        )
                    }
                    drawCross(Offset(outerX(9 * s), 9 * s), 2.2f * s, style.ornament, 1.3f * s)
                    drawCross(Offset(innerX(9 * s), h - 9 * s), 2.2f * s, style.ornament, 1.3f * s)
                }
                ChatThemeId.INFERNO -> {
                    sparkles.forEach { drawPath(it, style.ornament) }
                    // A thin gold line just inside the bottom edge, below the text.
                    drawLine(
                        style.ornament, Offset(radius, h - 3.5f * s), Offset(w - radius, h - 3.5f * s),
                        strokeWidth = 1f * s, cap = StrokeCap.Round
                    )
                }
                ChatThemeId.POOKIE -> {
                    drawCross(Offset(outerX(9 * s), 9 * s), 2.5f * s, style.ornament, 1.5f * s)
                    heart?.let { drawPath(it, theme.accent2) }
                }
                ChatThemeId.SERPENT -> {
                    // Stitch x's along the top padding, clear of the notched edge.
                    var x = 14 * s
                    while (x < w - 14 * s) {
                        drawCross(Offset(x, 5 * s), 1.8f * s, style.ornament, 1f * s)
                        x += 14 * s
                    }
                }
                ChatThemeId.SPOOKY -> {
                    sparkles.forEach { drawPath(it, style.ornament) }
                }
                ChatThemeId.POND -> drawFlower(Offset(outerX(8 * s), h - 8 * s), 2f * s, style.ornament, theme.accent2)
                ChatThemeId.CLASSIC -> Unit
            }
        }
    }
}

/** The received-message name tag, with the theme's horns, wings, fish or heart around it. */
@Composable
fun ChatNameTag(theme: ChatTheme, name: String, modifier: Modifier = Modifier) {
    val side = 12.dp
    val top = 7.dp
    Box(
        modifier = modifier
            .drawWithCache {
                val s = 1.dp.toPx()
                val pl = side.toPx()
                val pr = size.width - side.toPx()
                val pt = top.toPx()
                val cy = (pt + size.height) / 2f
                val leftHorn = hornPath(pl + 11 * s, pt + 2 * s, -1f, s)
                val rightHorn = hornPath(pr - 11 * s, pt + 2 * s, 1f, s)
                val leftWing = wingPath(pl + 1 * s, cy, -1f, s)
                val rightWing = wingPath(pr - 1 * s, cy, 1f, s)
                val fish = fishPath(Offset(pr + 6 * s, cy), s)
                val heart = heartPath(Offset(pr + 6 * s, cy), 2.8f * s)
                val sparkle = sparklePath(Offset(pr + 4 * s, pt - 1 * s), 2.5f * s)
                onDrawBehind {
                    when (theme.id) {
                        ChatThemeId.GOTHIC -> {
                            drawPath(leftHorn, theme.accent)
                            drawPath(rightHorn, theme.accent)
                            drawBow(Offset(pr + 6 * s, cy), s, theme.accent2)
                        }
                        ChatThemeId.INFERNO -> {
                            drawPath(leftWing, theme.accent)
                            drawPath(rightWing, theme.accent)
                            drawPath(leftHorn, theme.accent2)
                            drawPath(rightHorn, theme.accent2)
                        }
                        ChatThemeId.POOKIE -> {
                            drawCross(Offset(pl - 6 * s, cy), 2.5f * s, theme.accent, 1.5f * s)
                            drawPath(heart, theme.accent2)
                        }
                        ChatThemeId.SERPENT -> drawCross(Offset(pl - 6 * s, cy), 2.2f * s, theme.accent, 1.3f * s)
                        ChatThemeId.SPOOKY -> {
                            drawPath(leftWing, theme.accent)
                            drawPath(rightWing, theme.accent)
                            drawPath(sparkle, theme.accent2)
                        }
                        ChatThemeId.POND -> {
                            drawPath(fish, theme.accent)
                            drawFlower(Offset(pl - 6 * s, cy), 1.7f * s, Color.White, theme.accent2)
                        }
                        ChatThemeId.CLASSIC -> Unit
                    }
                }
            }
            .padding(start = side, end = side, top = top, bottom = 3.dp)
    ) {
        Text(
            name,
            color = theme.nameTagText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(theme.nameTagFill)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

// ----- Ornament shapes. Sizes are in px; [s] is 1dp in px. -----

private fun DrawScope.drawCross(center: Offset, arm: Float, color: Color, width: Float) {
    drawLine(color, Offset(center.x - arm, center.y - arm), Offset(center.x + arm, center.y + arm), width, StrokeCap.Round)
    drawLine(color, Offset(center.x - arm, center.y + arm), Offset(center.x + arm, center.y - arm), width, StrokeCap.Round)
}

/** A four-point sparkle. */
private fun sparklePath(center: Offset, r: Float): Path {
    val k = r * 0.28f
    return Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x + k, center.y - k, center.x + r, center.y)
        quadraticTo(center.x + k, center.y + k, center.x, center.y + r)
        quadraticTo(center.x - k, center.y + k, center.x - r, center.y)
        quadraticTo(center.x - k, center.y - k, center.x, center.y - r)
        close()
    }
}

/** Five petals around a centre dot. */
private fun DrawScope.drawFlower(center: Offset, petal: Float, petalColor: Color, centreColor: Color) {
    for (i in 0 until 5) {
        val a = Math.toRadians(i * 72.0 - 90.0)
        drawCircle(petalColor, petal, Offset(center.x + (petal * 1.4f * Math.cos(a)).toFloat(), center.y + (petal * 1.4f * Math.sin(a)).toFloat()))
    }
    drawCircle(centreColor, petal * 0.9f, center)
}

/** A tiny bow: two triangles meeting at a knot. */
private fun DrawScope.drawBow(center: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y)
        lineTo(center.x - 5 * s, center.y - 3.5f * s)
        lineTo(center.x - 5 * s, center.y + 3.5f * s)
        close()
        moveTo(center.x, center.y)
        lineTo(center.x + 5 * s, center.y - 3.5f * s)
        lineTo(center.x + 5 * s, center.y + 3.5f * s)
        close()
    }
    drawPath(path, color)
    drawCircle(color, 1.6f * s, center)
}

private fun heartPath(center: Offset, r: Float): Path = Path().apply {
    moveTo(center.x, center.y + r)
    cubicTo(center.x - 2.2f * r, center.y - 0.2f * r, center.x - 0.9f * r, center.y - 1.7f * r, center.x, center.y - 0.6f * r)
    cubicTo(center.x + 0.9f * r, center.y - 1.7f * r, center.x + 2.2f * r, center.y - 0.2f * r, center.x, center.y + r)
    close()
}

/** A small curved horn whose base sits on the tag's top edge; [dir] tilts the tip outwards. */
private fun hornPath(x: Float, baseY: Float, dir: Float, s: Float): Path = Path().apply {
    moveTo(x - 3 * s, baseY)
    quadraticTo(x - 2 * s + dir * 1 * s, baseY - 5 * s, x + dir * 3 * s, baseY - 8 * s)
    quadraticTo(x + 2 * s + dir * 0.5f * s, baseY - 4 * s, x + 3 * s, baseY)
    close()
}

/** A scalloped bat-wing silhouette sticking out of the tag's side; [dir] -1 = left, 1 = right. */
private fun wingPath(edgeX: Float, cy: Float, dir: Float, s: Float): Path = Path().apply {
    moveTo(edgeX, cy - 3 * s)
    lineTo(edgeX + dir * 11 * s, cy - 6 * s)
    quadraticTo(edgeX + dir * 9 * s, cy - 1 * s, edgeX + dir * 8 * s, cy + 1 * s)
    quadraticTo(edgeX + dir * 6.5f * s, cy - 1 * s, edgeX + dir * 5 * s, cy + 2.5f * s)
    quadraticTo(edgeX + dir * 3.5f * s, cy + 0.5f * s, edgeX + dir * 2 * s, cy + 4 * s)
    lineTo(edgeX, cy + 3 * s)
    close()
}

/** A little fish facing the tag: oval body plus a tail. */
private fun fishPath(center: Offset, s: Float): Path = Path().apply {
    addOval(androidx.compose.ui.geometry.Rect(center.x - 4.5f * s, center.y - 2.8f * s, center.x + 2.5f * s, center.y + 2.8f * s))
    moveTo(center.x + 1.5f * s, center.y)
    lineTo(center.x + 6 * s, center.y - 3 * s)
    lineTo(center.x + 6 * s, center.y + 3 * s)
    close()
}
