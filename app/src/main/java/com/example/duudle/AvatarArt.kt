package com.example.duudle

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.example.duudle.ui.theme.DuudleColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// The profile character, drawn flat: solid fills, one thick round Ink outline, no shading.
// 3/4 view facing slightly left. Every shape is defined in one 100 x 100 design space and
// every part is placed from the shared anchors below, so any combination lines up.
// A new option = one enum entry in Avatar.kt, plus a branch in each exhaustive `when` over
// it (the compiler lists them).
//
// Drawing happens in pixel space: the design-space paths are copied and transformed to the
// canvas size once (drawWithCache), and stroke widths/radii are multiplied by the scale. There
// is no canvas scale transform around path drawing, because before API 28 the hardware
// renderer rasterises paths at scale 1 and stretches them, which looks blurry at ~8x.

/**
 * BUST: head and shoulders, item fully visible. HEAD: zoomed onto head, hair, glasses, item.
 * FULL: the whole body, portrait, with headroom for the tallest item kept even when none
 * is worn (so the figure never moves when an item is toggled). TORSO, LEGS, FEET: square
 * thumbnails of the top (with hands), the bottoms and the shoes. FACE: square, tight on the
 * face and the edge of the hair (eyes, cheeks, glasses pickers).
 */
enum class AvatarFraming { BUST, HEAD, FULL, TORSO, LEGS, FEET, FACE }

/** Width / height of the framing's view box, for sizing the composable's container. */
val AvatarFraming.aspect: Float
    get() = viewFor(this, AvatarItem.NONE).let { it.width / it.height }

// ---- Design space anchors (units; the head and shoulders fill the 100 x 100 bust) ------

private const val STROKE = 2.6f

private const val HEAD_CX = 50f
private const val FACE_LEFT = 36f       // near-vertical front edge of the face
private const val FACE_RIGHT = 64f      // where the ear sits
private const val SKULL_TOP = 28f       // top of the bare head (always under hair)
private const val CHIN_Y = 65.5f

// Close together near the front (left) edge, about a third of the way down from hairline
// to chin, and far enough from that edge that the front lens stays on the face.
private const val EYE_Y = 50f
private const val EYE_FRONT_X = 41.5f
private const val EYE_BACK_X = 47.5f
private const val EYE_R = 1.7f

private const val EAR_CX = 64f
private const val EAR_CY = 51.5f
private const val EAR_R = 4.4f

private const val NECK_LEFT = 46f
private const val NECK_RIGHT = 56f
private const val NECK_CX = 51f
/** Where the neck's sides meet the outfit; the neck ends in a U that dips to NECKLINE_Y + NECKLINE_DIP / 2. */
private const val NECKLINE_Y = 72f
private const val NECKLINE_DIP = 10f
/** Top of the torso next to the neck; the shoulders slope down from here. */
private const val SHOULDER_TOP_Y = 73f

private val BustView = Rect(0f, 0f, 100f, 100f)
// With an item: the tallest combination is the cat on the bun (ear outline at y ~0.6; the
// flower on the bun reaches ~0.9). The top leaves ~2 units clear above it; the bottom still
// shows the chin and its outline (y ~66.8).
private val HeadView = Rect(15.5f, -1.5f, 84.5f, 67.5f)
// No item: shifted down so the head and hair sit centred (bun top ~15.5, chin ~66.8) and
// the ponytail (to ~76.8) fits; the long hair's curl runs off the bottom.
private val HeadViewNoItem = Rect(16.5f, 12f, 83.5f, 79f)
// Whole body: same headroom as HeadView whatever the item; sides clear the sleeves'
// outline (~10.3 to ~90.7); bottom clears the soles' outline (~169.8).
// ~6 units below the soles' outline (~170.6), so the shoes never touch the card edge.
private val FullView = Rect(7.5f, -2f, 93.5f, 176f)
// Square thumbnails. TORSO: collar to below the hands, sleeves included.
// LEGS: just below the crotch to just below the soles, between the hands, a little tighter
// so cuffs, creases and pockets read; only the outline at the skirt's hem corners and the
// cargo ankles touches the sides.
// FEET: one shoe (the left foot), cut just above the ankle so it fills the cell: the
// bottoms take ~25% of the height; high-tops and boots run up out of the top edge.
// FACE: the face from fringe to chin, ear included, cut at the edges of the hair.
private val TorsoView = Rect(8.5f, 70f, 92.5f, 154f)
private val LegsView = Rect(27.3f, 125.4f, 73.7f, 171.8f)
private val FeetView = Rect(27.2f, 151.8f, 49f, 173.6f)
private val FaceView = Rect(32f, 33f, 70f, 71f)

/**
 * [itemRoom] only matters for HEAD: true always leaves headroom for an item, false never
 * does, null decides from [item]. Grids pass a fixed value so every cell in one grid uses
 * the same view box (a "No item" cell mustn't zoom differently from its neighbours).
 */
private fun viewFor(framing: AvatarFraming, item: AvatarItem, itemRoom: Boolean? = null): Rect = when (framing) {
    AvatarFraming.BUST -> BustView
    AvatarFraming.HEAD -> if (itemRoom ?: (item != AvatarItem.NONE)) HeadView else HeadViewNoItem
    AvatarFraming.FULL -> FullView
    AvatarFraming.TORSO -> TorsoView
    AvatarFraming.LEGS -> LegsView
    AvatarFraming.FEET -> FeetView
    AvatarFraming.FACE -> FaceView
}

/**
 * Nothing above this is part of the lower body (hem, hands, legs, bottoms, shoes): a view
 * that ends above it (BUST, HEAD) skips recording those parts entirely, so it draws exactly
 * what it drew before the body existed. (The bust shows y <= 100; outlines reach 1.3 further.)
 */
private const val LOWER_BODY_TOP = 101.3f

/**
 * Nothing the upper layers draw (top, neck, head, hair, item) reaches below this: the shirt
 * tail ends at ~123 plus its outline. A view starting below it (LEGS, FEET) skips them.
 */
private const val UPPER_BODY_BOTTOM = 124.5f

/**
 * Top centre of each hair style: items stand on this point. It's a touch below the hair's
 * highest point so an item's bottom outline overlaps the hair instead of floating.
 */
private fun crownOf(hair: AvatarHair): Offset = when (hair) {
    AvatarHair.CURLY -> Offset(50f, 19f)
    AvatarHair.BOB -> Offset(50f, 22f)
    AvatarHair.PONYTAIL -> Offset(50f, 25.5f)
    AvatarHair.LONG_WAVY -> Offset(50f, 21.5f)
    AvatarHair.SHORT -> Offset(50f, 25f)
    AvatarHair.BUN -> Offset(BUN_CENTER.x, BUN_CENTER.y - BUN_R + 0.8f)
}

// ---- Colours not taken from the config -----------------------------------------------

private val Ink = DuudleColors.Ink
private val Paper = DuudleColors.Paper
private val CatWhite = Color(0xFFFFFFFF)
private val CatOrange = Color(0xFFF39C3D)
private val CatGrey = Color(0xFF8E8E93)
private val DuckYellow = Color(0xFFFFD43B)
private val BeakOrange = Color(0xFFFF8A1F)
private val PetalRed = Color(0xFFE8343A)
private val FlowerYellow = Color(0xFFFFD43B)
private val LeafGreen = Color(0xFF3DBE64)
private val AntennaPink = Color(0xFFFF7AC8)
private val HatOrange = Color(0xFFFF8A1F)
private val HatYellow = Color(0xFFFFD43B)
private val HairTieCoral = Color(0xFFFF6B66)

// Clear glasses use a thinner wire: at the full outline weight a lens this small would be
// all rim, and the eye dot inside it would disappear.
private const val GLASSES_WIRE = 1.5f
private const val GLINT_WIDTH = 0.9f
private const val TAIL_WIDTH = 2.4f

/**
 * Interior detail lines (drawstrings, folds, ribbing, seams, placket) vanish on very dark
 * outfits (navy, black), so those get a lightened tone; the silhouette outline stays Ink.
 * Purple (~0.076) still reads with Ink details, so the cut-off sits below it.
 */
private const val DARK_OUTFIT_LUMINANCE = 0.05f

private fun detailColorFor(cloth: Color): Color =
    if (cloth.luminance() < DARK_OUTFIT_LUMINANCE) lerp(cloth, Color.White, 0.4f) else Ink

// ---- Composable ----------------------------------------------------------------------

/**
 * The character, sized to the framing's aspect ([AvatarFraming.aspect]; square for all but
 * FULL). Decorative (hidden from TalkBack) unless [contentDescription] is given. The
 * drawing is recorded once per size and config, so the ~25 copies in the customiser grids
 * only replay it.
 */
@Composable
fun DuudleCharacter(
    config: AvatarConfig,
    modifier: Modifier = Modifier,
    // BUST stays the default because it was the only framing before the full body existed;
    // every current caller passes a framing explicitly.
    framing: AvatarFraming = AvatarFraming.BUST,
    drawBackdrop: Boolean = true,
    contentDescription: String? = null,
    itemRoom: Boolean? = null,
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Spacer(
        modifier = modifier
            .fitAspect(framing.aspect)
            .clipToBounds()
            .then(semantics)
            .drawWithCache {
                val art = recordCharacter(config, framing, size.width, size.height, itemRoom)
                val backdrop = Color(config.backdrop.color)
                onDrawBehind {
                    if (drawBackdrop) drawRect(backdrop)
                    art.replay(this)
                }
            }
    )
}

/**
 * Takes the framing's [aspect] only where the parent leaves a dimension free (e.g. a given
 * width in a scrolling column). When the parent fixes both (a cell, a card), it fills them
 * and the drawing contain-fits inside: never stretched, never cropped. (Modifier.aspectRatio
 * can't satisfy fixed constraints of another shape and falls back to an oversized, centred,
 * cropped canvas: that cut the heads off the outfit thumbnails.)
 */
private fun Modifier.fitAspect(aspect: Float): Modifier = layout { measurable, constraints ->
    var w = constraints.maxWidth
    var h = constraints.maxHeight
    when {
        constraints.hasBoundedWidth && constraints.hasBoundedHeight -> {
            if (w.toFloat() / h > aspect) w = (h * aspect).roundToInt() else h = (w / aspect).roundToInt()
        }
        constraints.hasBoundedWidth -> h = (w / aspect).roundToInt()
        constraints.hasBoundedHeight -> w = (h * aspect).roundToInt()
        else -> {
            w = constraints.minWidth
            h = constraints.minHeight
        }
    }
    w = w.coerceIn(constraints.minWidth, constraints.maxWidth)
    h = h.coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(w, h) { placeable.place(0, 0) }
}

private fun recordCharacter(
    config: AvatarConfig,
    framing: AvatarFraming,
    widthPx: Float,
    heightPx: Float,
    itemRoom: Boolean?,
): ArtRecording {
    val view = viewFor(framing, config.item, itemRoom)
    // Fit the view box inside the canvas and centre it (for square views in a square canvas
    // this is exactly width / view width with no offset).
    val pxPerUnit = min(widthPx / view.width, heightPx / view.height)
    val offsetX = (widthPx - view.width * pxPerUnit) / 2f
    val offsetY = (heightPx - view.height * pxPerUnit) / 2f
    val recorder = ArtRecorder(
        pxPerUnit,
        Affine.translate(offsetX, offsetY)
            .then(Affine.scale(pxPerUnit))
            .then(Affine.translate(-view.left, -view.top))
    )
    val skin = Color(config.skin.color)
    val cloth = Color(config.outfitColor.color)
    val detail = detailColorFor(cloth)
    val lowerBody = view.bottom > LOWER_BODY_TOP
    val upperBody = view.top < UPPER_BODY_BOTTOM
    with(recorder) {
        // Strict layer order: back to front.
        if (lowerBody) {
            drawLegs(skin)
            drawBottoms(config.bottom, Color(config.bottomColor.color))
            drawShoes(config.shoes, Color(config.shoeColor.color))
            // Before the top, so the cuffs cover the wrists.
            drawHands(skin)
        }
        if (!upperBody) return recorder.finish()
        drawOutfitBody(config.outfit, cloth, detail, lowerBody)
        drawNeck(skin)
        drawOutfitNeckline(config.outfit, cloth, skin, detail, lowerBody)
        drawBackHair(config.hair)
        drawFace(skin)
        drawCheeks(config.cheeks, skin)
        drawEyes(config.eyes)
        drawFrontHair(config.hair)
        drawGlasses(config.glasses)
        drawItem(config.item, crownOf(config.hair))
    }
    return recorder.finish()
}

// ---- Pixel-space recording -----------------------------------------------------------

/** 2D affine map: x' = a*x + c*y + tx, y' = b*x + d*y + ty. */
private class Affine(
    val a: Float, val b: Float, val c: Float, val d: Float, val tx: Float, val ty: Float,
) {
    fun map(p: Offset) = Offset(a * p.x + c * p.y + tx, b * p.x + d * p.y + ty)

    /** This map applied after [inner]. */
    fun then(inner: Affine) = Affine(
        a * inner.a + c * inner.b, b * inner.a + d * inner.b,
        a * inner.c + c * inner.d, b * inner.c + d * inner.d,
        a * inner.tx + c * inner.ty + tx, b * inner.tx + d * inner.ty + ty,
    )

    fun toMatrix(): Matrix = Matrix().also {
        it.values[Matrix.ScaleX] = a
        it.values[Matrix.SkewY] = b
        it.values[Matrix.SkewX] = c
        it.values[Matrix.ScaleY] = d
        it.values[Matrix.TranslateX] = tx
        it.values[Matrix.TranslateY] = ty
    }

    companion object {
        fun scale(s: Float) = Affine(s, 0f, 0f, s, 0f, 0f)
        fun translate(x: Float, y: Float) = Affine(1f, 0f, 0f, 1f, x, y)
        fun rotate(degrees: Float): Affine {
            val r = Math.toRadians(degrees.toDouble())
            val cs = cos(r).toFloat()
            val sn = sin(r).toFloat()
            return Affine(cs, sn, -sn, cs, 0f, 0f)
        }
    }
}

private class ArtRecording(private val ops: List<DrawScope.() -> Unit>) {
    fun replay(scope: DrawScope) = ops.forEach { it(scope) }
}

/**
 * Turns design-space shapes into pixel-space draw calls. Paths are copied and transformed,
 * points mapped, and widths/radii multiplied by [pxPerUnit] (the maps only ever scale
 * uniformly, translate and rotate).
 */
private class ArtRecorder(private val pxPerUnit: Float, private var map: Affine) {
    private var ops = ArrayList<DrawScope.() -> Unit>()
    private val outline = stroke(STROKE)
    private var matrix = map.toMatrix()

    fun finish() = ArtRecording(ops)

    fun stroke(widthUnits: Float) =
        Stroke(width = widthUnits * pxPerUnit, cap = StrokeCap.Round, join = StrokeJoin.Round)

    private fun px(src: Path): Path = Path().apply {
        addPath(src)
        transform(matrix)
    }

    fun fill(path: Path, color: Color) {
        val p = px(path)
        ops.add { drawPath(p, color) }
    }

    fun lines(path: Path, color: Color = Ink, widthUnits: Float = STROKE) {
        val p = px(path)
        val s = stroke(widthUnits)
        ops.add { drawPath(p, color, style = s) }
    }

    fun inked(path: Path, fill: Color) {
        val p = px(path)
        val s = outline
        ops.add {
            drawPath(p, fill)
            drawPath(p, Ink, style = s)
        }
    }

    fun dot(center: Offset, radius: Float, color: Color) {
        val c = map.map(center)
        val r = radius * pxPerUnit
        ops.add { drawCircle(color, r, c) }
    }

    fun inkedCircle(center: Offset, radius: Float, fill: Color) {
        val c = map.map(center)
        val r = radius * pxPerUnit
        val s = outline
        ops.add {
            drawCircle(fill, r, c)
            drawCircle(Ink, r, c, style = s)
        }
    }

    /** Records [block] clipped to [clip]. */
    fun clipped(clip: Path, block: ArtRecorder.() -> Unit) {
        val p = px(clip)
        val outer = ops
        val inner = ArrayList<DrawScope.() -> Unit>()
        ops = inner
        block()
        ops = outer
        ops.add { clipPath(p) { inner.forEach { it(this) } } }
    }

    /** Records [block] in a local frame placed by [local] (e.g. an item standing on the crown). */
    fun within(local: Affine, block: ArtRecorder.() -> Unit) {
        val saved = map
        val savedMatrix = matrix
        map = saved.then(local)
        matrix = map.toMatrix()
        block()
        map = saved
        matrix = savedMatrix
    }
}

// ---- Path helpers --------------------------------------------------------------------

private fun path(build: Path.() -> Unit): Path = Path().apply(build)

/** Points along an ellipse, angles in degrees (0 = right, 90 = down, 270 = up). */
private fun arcPoints(cx: Float, cy: Float, rx: Float, ry: Float, fromDeg: Float, toDeg: Float, segments: Int) =
    (0..segments).map { i ->
        val a = Math.toRadians((fromDeg + (toDeg - fromDeg) * i / segments).toDouble())
        Offset(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
    }

/**
 * Rounded bumps through [points] (the path must already be at the first one). Each bump
 * bulges to the left of the direction of travel: outwards when going clockwise around a shape.
 */
private fun Path.bumpsThrough(points: List<Offset>, bulge: Float) {
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        quadraticTo((a.x + b.x) / 2f + dy / len * bulge, (a.y + b.y) / 2f - dx / len * bulge, b.x, b.y)
    }
}

private fun quadAt(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    return a * (u * u) + c * (2f * u * t) + b * (t * t)
}

// ---- Body ----------------------------------------------------------------------------

// Lower body anchors (all below LOWER_BODY_TOP). The top keeps its bust outline; below
// that the sleeves hang as arms ending in rounded cuffs, and the body between them
// narrows a little to its own hem.
private const val HEM_Y = 119f            // bottom of the body
private const val SLEEVE_END_Y = 113.4f   // bottom of the cuffs
private const val CUFF_SEAM_Y = 109.5f    // seam across each sleeve, above the cuff
private const val HEM_BAND_Y = 114.5f     // hoodie / sweater band across the body only
private const val ARMPIT_Y = 110.6f       // where each sleeve parts from the body
private const val SLEEVE_SEAM_LEFT = 26.5f
private const val SLEEVE_SEAM_RIGHT = 74.5f
private const val BODY_CX = 50.5f         // the torso is symmetric about this
private const val WAIST_Y = 110f          // top of the bottoms, hidden under the top
private const val CROTCH_Y = 127f
private const val LEFT_LEG_CX = 40.5f
private const val RIGHT_LEG_CX = 60.5f
private const val ANKLE_Y = 161f          // bottom of the skin legs (under the shoes)
private const val PANTS_HEM_Y = 158f
private const val SHORTS_HEM_Y = 138f
private const val SKIRT_HEM_Y = 142f
private const val SHOE_TOP_Y = 157.5f     // shoe origin; low shoes start here, tall ones above; soles at +11

// The bust's torso, kept exactly as it was so BUST and HEAD don't change by a pixel.
private val TorsoPath: Path by lazy {
    path {
        moveTo(12f, 108f)
        lineTo(12.5f, 95f)
        cubicTo(13f, 84f, 20f, 78.5f, 31f, 76.5f)
        lineTo(NECK_LEFT - 2f, SHOULDER_TOP_Y)
        lineTo(NECK_RIGHT + 2f, SHOULDER_TOP_Y)
        lineTo(71f, 76.5f)
        cubicTo(82f, 78.5f, 88.5f, 84f, 88.5f, 95f)
        lineTo(89f, 108f)
        close()
    }
}

/** Short arm / sleeve seam lines, running off the bottom edge. */
private val ArmLinesPath: Path by lazy {
    path {
        moveTo(26.5f, 89f)
        lineTo(26.5f, 106f)
        moveTo(74.5f, 89f)
        lineTo(74.5f, 106f)
    }
}

// The bust's sides drift outwards 0.5 per 13 down. In the full body they follow that same
// line to LOWER_BODY_TOP, then curve in to a rounded cuff.
private const val SIDE_DRIFT = 0.5f / 13f
private val LeftSideAtBustEdge = Offset(12.5f - (LOWER_BODY_TOP - 95f) * SIDE_DRIFT, LOWER_BODY_TOP)

private fun mirrorX(x: Float) = 2f * BODY_CX - x

/**
 * The full top as one outline: shoulders as in the bust; each sleeve hangs as an arm and
 * ends in a rounded cuff; between cuff and body there's a small notch at the armpit, so
 * the arms read as separate; the body tapers slightly to its own hem.
 */
private val FullTorsoPath: Path by lazy {
    val l = LeftSideAtBustEdge
    val c1 = Offset(l.x - (106f - l.y) * SIDE_DRIFT, 106f)
    path {
        moveTo(l.x, l.y)
        // Left sleeve: down the outside to the cuff, across it, up into the armpit notch.
        cubicTo(c1.x, c1.y, 12.4f, 109.5f, 13.6f, 111.4f)
        quadraticTo(14.4f, SLEEVE_END_Y, 16.6f, SLEEVE_END_Y)
        lineTo(23.8f, SLEEVE_END_Y)
        quadraticTo(SLEEVE_SEAM_LEFT, SLEEVE_END_Y, SLEEVE_SEAM_LEFT, ARMPIT_Y)
        // Body: tapering down to a rounded hem corner, across, and up the other side.
        lineTo(28.2f, HEM_Y - 1.8f)
        quadraticTo(28.6f, HEM_Y, 30.4f, HEM_Y)
        lineTo(mirrorX(30.4f), HEM_Y)
        quadraticTo(mirrorX(28.6f), HEM_Y, mirrorX(28.2f), HEM_Y - 1.8f)
        lineTo(SLEEVE_SEAM_RIGHT, ARMPIT_Y)
        // Right sleeve, mirrored.
        quadraticTo(SLEEVE_SEAM_RIGHT, SLEEVE_END_Y, mirrorX(23.8f), SLEEVE_END_Y)
        lineTo(mirrorX(16.6f), SLEEVE_END_Y)
        quadraticTo(mirrorX(14.4f), SLEEVE_END_Y, mirrorX(13.6f), 111.4f)
        cubicTo(mirrorX(12.4f), 109.5f, mirrorX(c1.x), c1.y, mirrorX(l.x), l.y)
        // Shoulders, as in the bust (traced the other way round).
        lineTo(88.5f, 95f)
        cubicTo(88.5f, 84f, 82f, 78.5f, 71f, 76.5f)
        lineTo(NECK_RIGHT + 2f, SHOULDER_TOP_Y)
        lineTo(NECK_LEFT - 2f, SHOULDER_TOP_Y)
        lineTo(31f, 76.5f)
        cubicTo(20f, 78.5f, 13f, 84f, 12.5f, 95f)
        close()
    }
}

/** Sleeve seams down to the armpit notch, plus a cuff seam across each sleeve. */
private val FullArmLinesPath: Path by lazy {
    path {
        moveTo(SLEEVE_SEAM_LEFT, 89f)
        lineTo(SLEEVE_SEAM_LEFT, ARMPIT_Y - 1f)
        moveTo(SLEEVE_SEAM_RIGHT, 89f)
        lineTo(SLEEVE_SEAM_RIGHT, ARMPIT_Y - 1f)
        moveTo(14.1f, CUFF_SEAM_Y)
        lineTo(SLEEVE_SEAM_LEFT - 1.3f, CUFF_SEAM_Y)
        moveTo(SLEEVE_SEAM_RIGHT + 1.3f, CUFF_SEAM_Y)
        lineTo(mirrorX(14.1f), CUFF_SEAM_Y)
    }
}

/** Hoodie and sweater: a band across the body (not the sleeves) above the hem. */
private val HemBandPath: Path by lazy {
    path {
        moveTo(29f, HEM_BAND_Y)
        lineTo(mirrorX(29f), HEM_BAND_Y)
    }
}

/** Sweater: ribbing in the hem band. */
private val HemRibsPath: Path by lazy {
    path {
        var x = 31.5f
        while (x < mirrorX(31f)) {
            moveTo(x, HEM_BAND_Y + 1.4f)
            lineTo(x, HEM_Y - 1.4f)
            x += 3.8f
        }
    }
}

/** Collared shirt: the rounded shirt tail hanging just below the hem (drawn under the top). */
private val ShirtTailPath: Path by lazy {
    path {
        moveTo(29f, HEM_Y - 1f)
        quadraticTo(BODY_CX, HEM_Y + 9f, mirrorX(29f), HEM_Y - 1f)
        close()
    }
}

/** Neck column; its bottom is the shared U every neckline starts from. */
private val NeckPath: Path by lazy {
    path {
        moveTo(NECK_LEFT, 56f)
        lineTo(NECK_LEFT, NECKLINE_Y)
        quadraticTo(NECK_CX, NECKLINE_Y + NECKLINE_DIP, NECK_RIGHT, NECKLINE_Y)
        lineTo(NECK_RIGHT, 56f)
        close()
    }
}

private val FacePath: Path by lazy {
    path {
        moveTo(FACE_LEFT, 42f)
        lineTo(FACE_LEFT + 0.2f, 55f)
        // Rounded chin at the lower left, then the jaw runs right towards the neck and ear.
        cubicTo(FACE_LEFT + 0.4f, 61.5f, 39.5f, CHIN_Y, 45f, CHIN_Y)
        cubicTo(50f, CHIN_Y, 54.5f, 63.5f, 57.5f, 61f)
        cubicTo(61f, 58f, FACE_RIGHT - 0.2f, 55f, FACE_RIGHT, EAR_CY)
        lineTo(FACE_RIGHT, 41f)
        cubicTo(FACE_RIGHT, 32.5f, 58f, SKULL_TOP, HEAD_CX, SKULL_TOP)
        cubicTo(42f, SKULL_TOP, FACE_LEFT, 32.5f, FACE_LEFT, 42f)
        close()
    }
}

private fun ArtRecorder.drawNeck(skin: Color) = inked(NeckPath, skin)

private fun ArtRecorder.drawFace(skin: Color) {
    // Ear first: the face covers its left half, leaving a bump on the right.
    inkedCircle(Offset(EAR_CX, EAR_CY), EAR_R, skin)
    inked(FacePath, skin)
}

// Line eyes use a lighter weight than the outline, about the dots' visual weight.
private const val EYE_LINE = 1.8f
private const val WIDE_EYE_R = 2.1f
private val BlushPink = Color(0xFFFFA3B5)

/** A closed, upward-curving eye (^) centred on [x], [EYE_Y]. */
private fun happyEyePath(x: Float): Path = path {
    moveTo(x - 1.9f, EYE_Y + 0.7f)
    quadraticTo(x, EYE_Y - 2.3f, x + 1.9f, EYE_Y + 0.7f)
}

private val HappyEyesPath: Path by lazy {
    path {
        addPath(happyEyePath(EYE_FRONT_X))
        addPath(happyEyePath(EYE_BACK_X))
    }
}

private val WinkEyePath: Path by lazy { happyEyePath(EYE_BACK_X) }

private val SleepyEyesPath: Path by lazy {
    path {
        for (x in floatArrayOf(EYE_FRONT_X, EYE_BACK_X)) {
            moveTo(x - 1.8f, EYE_Y + 0.4f)
            lineTo(x + 1.8f, EYE_Y + 0.4f)
        }
    }
}

/** Every style is centred on the EYE_* anchors, so all glasses still sit on the eyes. */
private fun ArtRecorder.drawEyes(eyes: AvatarEyes) {
    when (eyes) {
        AvatarEyes.DOTS -> {
            dot(Offset(EYE_FRONT_X, EYE_Y), EYE_R, Ink)
            dot(Offset(EYE_BACK_X, EYE_Y), EYE_R, Ink)
        }
        AvatarEyes.HAPPY -> lines(HappyEyesPath, widthUnits = EYE_LINE)
        AvatarEyes.SLEEPY -> lines(SleepyEyesPath, widthUnits = EYE_LINE)
        AvatarEyes.WINK -> {
            dot(Offset(EYE_FRONT_X, EYE_Y), EYE_R, Ink)
            lines(WinkEyePath, widthUnits = EYE_LINE)
        }
        AvatarEyes.WIDE -> {
            for (x in floatArrayOf(EYE_FRONT_X, EYE_BACK_X)) {
                dot(Offset(x, EYE_Y), WIDE_EYE_R, Ink)
                dot(Offset(x - 0.7f, EYE_Y - 0.8f), 0.6f, Paper)
            }
        }
    }
}

/** Below the eyes and clear of the lenses (whose bottom outline reaches y ~54.2). */
private fun ArtRecorder.drawCheeks(cheeks: AvatarCheeks, skin: Color) {
    when (cheeks) {
        AvatarCheeks.NONE -> Unit
        AvatarCheeks.BLUSH -> fill(BlushPath, BlushPink)
        AvatarCheeks.FRECKLES -> {
            val freckle = lerp(skin, Ink, 0.45f)
            for (p in Freckles) dot(p, FRECKLE_R, freckle)
        }
    }
}

/**
 * Two soft ovals: one on the front cheek just below-left of the front eye, a smaller one
 * on the back cheek before the ear (a single oval under the eyes read as a mouth).
 */
private val BlushPath: Path by lazy {
    path {
        addOval(Rect(38.2f, 54.6f, 42.8f, 57.2f))
        addOval(Rect(53.6f, 54.8f, 57f, 56.8f))
    }
}

private val Freckles = listOf(
    Offset(39.4f, 55.6f), Offset(41.6f, 56.6f), Offset(43.6f, 55.6f),
    Offset(54.4f, 55.8f), Offset(56.4f, 56.6f),
)
private const val FRECKLE_R = 0.7f

// ---- Outfits -------------------------------------------------------------------------

/**
 * Same cut for every outfit; the neckline (and, with the lower body, the hem) is what tells
 * them apart. [lowerBody] switches from the bust torso to the full one with a hem.
 */
private fun ArtRecorder.drawOutfitBody(outfit: AvatarOutfit, cloth: Color, detail: Color, lowerBody: Boolean) {
    if (!lowerBody) {
        inked(TorsoPath, cloth)
        lines(ArmLinesPath, detail)
        return
    }
    if (outfit == AvatarOutfit.COLLAR) inked(ShirtTailPath, cloth)
    inked(FullTorsoPath, cloth)
    lines(FullArmLinesPath, detail)
    when (outfit) {
        AvatarOutfit.HOODIE -> lines(HemBandPath, detail)
        AvatarOutfit.SWEATER -> {
            lines(HemBandPath, detail)
            lines(HemRibsPath, detail)
        }
        AvatarOutfit.TEE, AvatarOutfit.COLLAR -> Unit
    }
}

/** Drawn over the neck, so it hides the neck's bottom edge. */
private fun ArtRecorder.drawOutfitNeckline(outfit: AvatarOutfit, cloth: Color, skin: Color, detail: Color, lowerBody: Boolean) {
    when (outfit) {
        AvatarOutfit.HOODIE -> {
            inked(HoodPath, cloth)
            lines(HoodFoldsPath, detail)
            lines(DrawstringsPath, detail)
        }
        AvatarOutfit.COLLAR -> {
            fill(CollarOpeningPath, skin)
            inked(CollarLeftPath, cloth)
            inked(CollarRightPath, cloth)
            lines(if (lowerBody) FullPlacketPath else PlacketPath, detail)
            dot(Offset(NECK_CX + 2.6f, 91.5f), 1.1f, detail)
            dot(Offset(NECK_CX + 2.6f, 98.5f), 1.1f, detail)
            if (lowerBody) {
                dot(Offset(NECK_CX + 2.6f, 105.5f), 1.1f, detail)
                dot(Offset(NECK_CX + 2.6f, 112.5f), 1.1f, detail)
            }
        }
        AvatarOutfit.TEE -> {
            inked(TeeBandPath, cloth)
            lines(TeeBandSeamPath, detail)
        }
        AvatarOutfit.SWEATER -> {
            inked(SweaterBandPath, cloth)
            lines(SweaterBandSeamPath, detail)
            lines(SweaterRibsPath, detail)
            // A white shirt underneath: its collar sits inside the ribbed neckline.
            fill(UnderShirtOpeningPath, Paper)
            inked(UnderCollarLeftPath, Paper)
            inked(UnderCollarRightPath, Paper)
        }
    }
}

/** The hood lying down: a puffy roll round the back of the neck, meeting below it. */
private val HoodPath: Path by lazy {
    path {
        moveTo(NECK_LEFT - 0.5f, 70f)
        cubicTo(39f, 69f, 34.5f, 74f, 37f, 79f)
        cubicTo(39.5f, 84f, 45f, 87f, NECK_CX, 87f)
        cubicTo(57f, 87f, 62.5f, 84f, 65f, 79f)
        cubicTo(67.5f, 74f, 63f, 69f, NECK_RIGHT + 0.5f, 70f)
        lineTo(NECK_RIGHT, NECKLINE_Y)
        quadraticTo(NECK_CX, NECKLINE_Y + NECKLINE_DIP, NECK_LEFT, NECKLINE_Y)
        close()
    }
}

private val HoodFoldsPath: Path by lazy {
    path {
        moveTo(40.5f, 75f)
        quadraticTo(42f, 81.5f, 47.5f, 83.5f)
        moveTo(61.5f, 75f)
        quadraticTo(60f, 81.5f, 54.5f, 83.5f)
    }
}

private val DrawstringsPath: Path by lazy {
    path {
        moveTo(48f, 87f)
        lineTo(47.5f, 95f)
        moveTo(54f, 87f)
        lineTo(54.5f, 95f)
    }
}

/** Skin showing in the shirt's V, below the neck's U. */
private val CollarOpeningPath: Path by lazy {
    path {
        moveTo(NECK_LEFT, NECKLINE_Y)
        lineTo(NECK_CX, 83f)
        lineTo(NECK_RIGHT, NECKLINE_Y)
        close()
    }
}

private val CollarLeftPath: Path by lazy {
    path {
        moveTo(NECK_LEFT, 70.5f)
        lineTo(NECK_CX, 83f)
        lineTo(44.5f, 85f)
        lineTo(40f, 73.5f)
        close()
    }
}

private val CollarRightPath: Path by lazy {
    path {
        moveTo(NECK_RIGHT, 70.5f)
        lineTo(NECK_CX, 83f)
        lineTo(57.5f, 85f)
        lineTo(62f, 73.5f)
        close()
    }
}

private val PlacketPath: Path by lazy {
    path {
        moveTo(NECK_CX, 84.5f)
        lineTo(NECK_CX, 106f)
    }
}

/** With the lower body: down the front, over the hem, onto the shirt tail. */
private val FullPlacketPath: Path by lazy {
    path {
        moveTo(NECK_CX, 84.5f)
        lineTo(NECK_CX, HEM_Y + 2.5f)
    }
}

/** A band between the neck's U and a wider U below it. */
private fun necklineBand(halfWidth: Float, outerDip: Float): Path = path {
    moveTo(NECK_LEFT - halfWidth, NECKLINE_Y + 0.5f)
    quadraticTo(NECK_CX, NECKLINE_Y + outerDip, NECK_RIGHT + halfWidth, NECKLINE_Y + 0.5f)
    lineTo(NECK_RIGHT, NECKLINE_Y)
    quadraticTo(NECK_CX, NECKLINE_Y + NECKLINE_DIP, NECK_LEFT, NECKLINE_Y)
    close()
}

/**
 * The band's lower edge, where it meets the shirt: an interior line, redrawn over the
 * band's Ink outline in the detail colour so it shows on dark outfits.
 */
private fun necklineBandSeam(halfWidth: Float, outerDip: Float): Path {
    val start = Offset(NECK_LEFT - halfWidth, NECKLINE_Y + 0.5f)
    val control = Offset(NECK_CX, NECKLINE_Y + outerDip)
    val end = Offset(NECK_RIGHT + halfWidth, NECKLINE_Y + 0.5f)
    // Stops just short of the corners, so its round caps stay off the torso's Ink outline.
    return path {
        val steps = 16
        for (i in 0..steps) {
            val p = quadAt(start, control, end, 0.06f + 0.88f * i / steps)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
    }
}

private const val TEE_HALF_WIDTH = 3.5f
private const val TEE_DIP = 18f
private const val SWEATER_HALF_WIDTH = 4.5f
private const val SWEATER_DIP = 22f

private val TeeBandPath: Path by lazy { necklineBand(halfWidth = TEE_HALF_WIDTH, outerDip = TEE_DIP) }
private val TeeBandSeamPath: Path by lazy { necklineBandSeam(halfWidth = TEE_HALF_WIDTH, outerDip = TEE_DIP) }
private val SweaterBandPath: Path by lazy { necklineBand(halfWidth = SWEATER_HALF_WIDTH, outerDip = SWEATER_DIP) }
private val SweaterBandSeamPath: Path by lazy { necklineBandSeam(halfWidth = SWEATER_HALF_WIDTH, outerDip = SWEATER_DIP) }

/** Short ribbing lines across the outer half of the sweater's neckline band. */
private val SweaterRibsPath: Path by lazy {
    val inner = Triple(
        Offset(NECK_RIGHT, NECKLINE_Y),
        Offset(NECK_CX, NECKLINE_Y + NECKLINE_DIP),
        Offset(NECK_LEFT, NECKLINE_Y)
    )
    val outer = Triple(
        Offset(NECK_RIGHT + SWEATER_HALF_WIDTH, NECKLINE_Y + 0.5f),
        Offset(NECK_CX, NECKLINE_Y + SWEATER_DIP),
        Offset(NECK_LEFT - SWEATER_HALF_WIDTH, NECKLINE_Y + 0.5f)
    )
    path {
        for (t in floatArrayOf(0.1f, 0.25f, 0.4f, 0.6f, 0.75f, 0.9f)) {
            val a = quadAt(inner.first, inner.second, inner.third, t)
            val b = quadAt(outer.first, outer.second, outer.third, t)
            val mid = a + (b - a) * 0.5f
            moveTo(mid.x, mid.y)
            lineTo(b.x, b.y)
        }
    }
}

/** The white shirt showing between the under-collar points. */
private val UnderShirtOpeningPath: Path by lazy {
    path {
        moveTo(NECK_LEFT, NECKLINE_Y)
        lineTo(NECK_CX, 79.5f)
        lineTo(NECK_RIGHT, NECKLINE_Y)
        close()
    }
}

private val UnderCollarLeftPath: Path by lazy {
    path {
        moveTo(NECK_LEFT, 71f)
        lineTo(NECK_CX, 78.5f)
        lineTo(47f, 80.5f)
        lineTo(44.5f, 74f)
        close()
    }
}

private val UnderCollarRightPath: Path by lazy {
    path {
        moveTo(NECK_RIGHT, 71f)
        lineTo(NECK_CX, 78.5f)
        lineTo(55f, 80.5f)
        lineTo(57.5f, 74f)
        close()
    }
}

// ---- Lower body: hands, legs, bottoms, shoes (only recorded when a view shows them) ----

/** Short wrist (its top hides under the cuff), with a rounded mitten below it. */
private fun wristPath(centerX: Float): Path = path {
    addRect(Rect(centerX - 2.8f, SLEEVE_END_Y - 2.4f, centerX + 2.8f, SLEEVE_END_Y + 3.4f))
}

private fun mittenPath(centerX: Float): Path = path {
    addRoundRect(
        RoundRect(
            rect = Rect(centerX - 4.6f, SLEEVE_END_Y + 1.8f, centerX + 4.6f, SLEEVE_END_Y + 12f),
            topLeft = CornerRadius(3.2f),
            topRight = CornerRadius(3.2f),
            bottomRight = CornerRadius(4.6f),
            bottomLeft = CornerRadius(4.6f)
        )
    )
}

// Centred under the cuffs (left cuff spans ~15.5..24.9), hanging just outside the hips.
private const val LEFT_HAND_CX = 20f
private val LeftWristPath: Path by lazy { wristPath(LEFT_HAND_CX) }
private val RightWristPath: Path by lazy { wristPath(mirrorX(LEFT_HAND_CX)) }
private val LeftMittenPath: Path by lazy { mittenPath(LEFT_HAND_CX) }
private val RightMittenPath: Path by lazy { mittenPath(mirrorX(LEFT_HAND_CX)) }

private fun ArtRecorder.drawHands(skin: Color) {
    inked(LeftWristPath, skin)
    inked(LeftMittenPath, skin)
    inked(RightWristPath, skin)
    inked(RightMittenPath, skin)
}

/** Bare legs under everything: they show below shorts and skirts, and as a sliver of ankle. */
private val SkinLegsPath: Path by lazy {
    path {
        moveTo(33.5f, HEM_Y - 2f)
        lineTo(34f, ANKLE_Y)
        lineTo(47f, ANKLE_Y)
        lineTo(47.5f, HEM_Y - 2f)
        close()
        moveTo(2f * BODY_CX - 33.5f, HEM_Y - 2f)
        lineTo(2f * BODY_CX - 34f, ANKLE_Y)
        lineTo(2f * BODY_CX - 47f, ANKLE_Y)
        lineTo(2f * BODY_CX - 47.5f, HEM_Y - 2f)
        close()
    }
}

private fun ArtRecorder.drawLegs(skin: Color) = inked(SkinLegsPath, skin)

/**
 * Two-legged bottoms, symmetric about BODY_CX: [outerTop] / [outerBottom] are the half
 * widths at the waist and the hem, [innerBottom] the half gap between the legs at the hem
 * (it opens from ~1.1 at the crotch).
 */
private class Legwear(val outerTop: Float, val outerBottom: Float, val innerBottom: Float, val hemY: Float) {
    val outline: Path by lazy {
        path {
            moveTo(BODY_CX - outerTop, WAIST_Y)
            lineTo(BODY_CX + outerTop, WAIST_Y)
            lineTo(BODY_CX + outerBottom, hemY)
            lineTo(BODY_CX + innerBottom, hemY)
            lineTo(BODY_CX + CROTCH_HALF_GAP, CROTCH_Y)
            quadraticTo(BODY_CX, CROTCH_Y - 1.5f, BODY_CX - CROTCH_HALF_GAP, CROTCH_Y)
            lineTo(BODY_CX - innerBottom, hemY)
            lineTo(BODY_CX - outerBottom, hemY)
            close()
        }
    }

    private fun outerHalf(y: Float) = outerTop + (outerBottom - outerTop) * (y - WAIST_Y) / (hemY - WAIST_Y)
    private fun innerHalf(y: Float) = CROTCH_HALF_GAP + (innerBottom - CROTCH_HALF_GAP) * (y - CROTCH_Y) / (hemY - CROTCH_Y)

    /** A line across both legs at [y], stopping short of the outline so its caps don't touch it. */
    fun across(y: Float): Path = path {
        val o = outerHalf(y) - 1f
        val i = innerHalf(y) + 1f
        moveTo(BODY_CX - o, y)
        lineTo(BODY_CX - i, y)
        moveTo(BODY_CX + i, y)
        lineTo(BODY_CX + o, y)
    }

    companion object {
        const val CROTCH_HALF_GAP = 1.1f
    }
}

private val Jeans = Legwear(outerTop = 21f, outerBottom = 20f, innerBottom = 2.5f, hemY = PANTS_HEM_Y)
// Slimmer than jeans and tapering to the ankle, so the two differ at thumbnail size.
private val Trousers = Legwear(outerTop = 20.5f, outerBottom = 17.5f, innerBottom = 3.2f, hemY = PANTS_HEM_Y)
private val Shorts = Legwear(outerTop = 21f, outerBottom = 22f, innerBottom = 1.8f, hemY = SHORTS_HEM_Y)
private val Cargo = Legwear(outerTop = 22f, outerBottom = 22.5f, innerBottom = 2.5f, hemY = PANTS_HEM_Y - 0.5f)

private const val JEANS_CUFF_Y = 152.5f
private val JeansCuffLine: Path by lazy { Jeans.across(JEANS_CUFF_Y) }
private val ShortsHemLine: Path by lazy { Shorts.across(SHORTS_HEM_Y - 3.5f) }
private val BelowCuffRect: Path by lazy { path { addRect(Rect(0f, JEANS_CUFF_Y, 101f, 175f)) } }

/** Jeans: light topstitching down the outside of each leg, the fly seam and pocket curves. */
private val JeansStitchingPath: Path by lazy {
    path {
        moveTo(32.4f, HEM_Y + 2f)
        lineTo(33.2f, JEANS_CUFF_Y - 1.6f)
        moveTo(mirrorX(32.4f), HEM_Y + 2f)
        lineTo(mirrorX(33.2f), JEANS_CUFF_Y - 1.6f)
        moveTo(BODY_CX, HEM_Y + 1f)
        lineTo(BODY_CX, CROTCH_Y - 2.3f)
        moveTo(31.5f, 124f)
        quadraticTo(37f, 124.5f, 38.5f, HEM_Y + 0.5f)
        moveTo(mirrorX(31.5f), 124f)
        quadraticTo(mirrorX(37f), 124.5f, mirrorX(38.5f), HEM_Y + 0.5f)
    }
}
private const val STITCH_LINE = 1.6f

/** Trousers: a pressed crease down the middle of each leg; plain hem, no cuffs. */
private val TrouserCreasesPath: Path by lazy {
    path {
        moveTo(40.2f, CROTCH_Y + 2f)
        lineTo(40.2f, PANTS_HEM_Y - 2f)
        moveTo(mirrorX(40.2f), CROTCH_Y + 2f)
        lineTo(mirrorX(40.2f), PANTS_HEM_Y - 2f)
    }
}

/** A-line skirt, flaring from the waist to a gently curved hem. */
private val SkirtPath: Path by lazy {
    path {
        moveTo(BODY_CX - 20.5f, WAIST_Y)
        lineTo(BODY_CX + 20.5f, WAIST_Y)
        lineTo(BODY_CX + 23.5f, SKIRT_HEM_Y)
        quadraticTo(BODY_CX, SKIRT_HEM_Y + 3.5f, BODY_CX - 23.5f, SKIRT_HEM_Y)
        close()
    }
}

private val SkirtPleatsPath: Path by lazy {
    path {
        moveTo(41f, HEM_Y + 6f)
        lineTo(39f, SKIRT_HEM_Y - 0.5f)
        moveTo(2f * BODY_CX - 41f, HEM_Y + 6f)
        lineTo(2f * BODY_CX - 39f, SKIRT_HEM_Y - 0.5f)
    }
}

/** Cargo: a patch pocket with a flap on the outside of each thigh. */
private val CargoPocketsPath: Path by lazy {
    path {
        for (left in floatArrayOf(30.5f, 2f * BODY_CX - 37f)) {
            addRoundRect(RoundRect(Rect(left, 129f, left + 6.5f, 140f), CornerRadius(1.2f)))
            moveTo(left, 132.5f)
            lineTo(left + 6.5f, 132.5f)
        }
    }
}

private fun ArtRecorder.drawBottoms(bottom: AvatarBottom, color: Color) {
    val detail = detailColorFor(color)
    when (bottom) {
        AvatarBottom.JEANS -> {
            // Rolled cuffs: a lighter band at the ankles; stitching in a lighter thread.
            fill(Jeans.outline, color)
            clipped(Jeans.outline) { fill(BelowCuffRect, lerp(color, Color.White, 0.3f)) }
            lines(Jeans.outline)
            lines(JeansCuffLine, detail)
            lines(JeansStitchingPath, lerp(color, Color.White, 0.5f), STITCH_LINE)
        }
        AvatarBottom.TROUSERS -> {
            inked(Trousers.outline, color)
            lines(TrouserCreasesPath, detail)
        }
        AvatarBottom.SHORTS -> {
            inked(Shorts.outline, color)
            lines(ShortsHemLine, detail)
        }
        AvatarBottom.SKIRT -> {
            inked(SkirtPath, color)
            lines(SkirtPleatsPath, detail)
        }
        AvatarBottom.CARGO -> {
            inked(Cargo.outline, color)
            lines(CargoPocketsPath, detail)
        }
    }
}

// Shoes, in local units: origin at the leg's centre and SHOE_TOP_Y; soles end at y 11.
// Seen front-on in 3/4 like the face: a rounded toe swelling slightly to the left.

/** The low part every shoe shares, from the top of its heel side round to the toe. */
private fun Path.shoeFoot(topY: Float, rightTop: Float) {
    lineTo(rightTop, topY)
    cubicTo(rightTop + 1.3f, topY + 2.2f, 7.6f, 5.5f, 7.4f, 8f)
    lineTo(7.4f, 9.6f)
    quadraticTo(7.4f, 11f, 6f, 11f)
    lineTo(-8.6f, 11f)
    quadraticTo(-10.2f, 11f, -10.1f, 9.4f)
}

private val SneakerPath: Path by lazy {
    path {
        moveTo(-5.8f, 0f)
        shoeFoot(topY = 0f, rightTop = 5.8f)
        cubicTo(-10f, 5.8f, -8.4f, 2.6f, -5.8f, 0f)
        close()
    }
}

/** Up to mid-shin, with an ankle collar band; no side patch (a circle there read as letters). */
private val HighTopPath: Path by lazy {
    path {
        moveTo(-6.1f, -9f)
        lineTo(6.2f, -9f)
        lineTo(6.6f, 1f)
        shoeFoot(topY = 1f, rightTop = 6.6f)
        cubicTo(-10f, 5.8f, -8.4f, 2.8f, -6.3f, 1f)
        close()
    }
}

/** Chunky boot upper; the sole and heel are separate blocks below it. */
private val BootUpperPath: Path by lazy {
    path {
        moveTo(-6.6f, -7f)
        lineTo(6.8f, -7f)
        lineTo(7.4f, 8.4f)
        lineTo(-9.8f, 8.4f)
        quadraticTo(-10.8f, 8.4f, -10.7f, 7f)
        cubicTo(-10.5f, 4f, -8.4f, 1.6f, -6.4f, 0.4f)
        close()
    }
}
private val BootSolePath: Path by lazy { path { addRoundRect(RoundRect(Rect(-11f, 8f, 7.8f, 10.8f), CornerRadius(1.2f))) } }
/** The heel: a block at the back that drops below the sole. */
private val BootHeelPath: Path by lazy { path { addRoundRect(RoundRect(Rect(2f, 9.4f, 7.8f, 11.8f), CornerRadius(0.8f))) } }

/** Low shoe: a little ankle shows above it. */
private val LoaferPath: Path by lazy {
    path {
        moveTo(-6f, 2.6f)
        shoeFoot(topY = 2.6f, rightTop = 6f)
        cubicTo(-10f, 6.6f, -8.4f, 4f, -6f, 2.6f)
        close()
    }
}

private val SoleBandRect: Path by lazy { path { addRect(Rect(-12f, 8.6f, 9f, 13f)) } }
private val SoleSeamPath: Path by lazy {
    path {
        moveTo(-9.6f, 8.6f)
        lineTo(6.6f, 8.6f)
    }
}

private val SneakerLacesPath: Path by lazy {
    path {
        moveTo(-3.4f, 1.6f)
        lineTo(-0.6f, 1.6f)
        moveTo(-4.2f, 3.4f)
        lineTo(-1.4f, 3.4f)
    }
}

private val HighTopDetailsPath: Path by lazy {
    path {
        moveTo(-5.2f, -6.2f)      // ankle collar band
        lineTo(5.4f, -6.2f)
        moveTo(-2.8f, -3.4f)      // laces up the front
        lineTo(0.2f, -3.4f)
        moveTo(-2.8f, -1.2f)
        lineTo(0.2f, -1.2f)
        moveTo(-3.4f, 1f)
        lineTo(-0.4f, 1f)
    }
}

private val BootCuffPath: Path by lazy {
    path {
        moveTo(-5.4f, -4.4f)
        lineTo(5.6f, -4.4f)
    }
}

private val LoaferDetailsPath: Path by lazy {
    path {
        moveTo(-6.4f, 5.2f)       // strap across the top of the foot
        lineTo(1.6f, 5.2f)
        moveTo(-9.4f, 9.4f)       // sole seam
        lineTo(6.4f, 9.4f)
    }
}

private fun ArtRecorder.drawShoes(shoes: AvatarShoes, color: Color) {
    val detail = detailColorFor(color)
    // Right foot first, so the left one is in front if a chunky pair touches.
    for (cx in floatArrayOf(RIGHT_LEG_CX, LEFT_LEG_CX)) {
        within(Affine.translate(cx, SHOE_TOP_Y)) {
            when (shoes) {
                AvatarShoes.SNEAKERS -> {
                    fill(SneakerPath, color)
                    clipped(SneakerPath) { fill(SoleBandRect, Paper) }
                    lines(SneakerPath)
                    lines(SoleSeamPath)
                    lines(SneakerLacesPath, detail, EYE_LINE)
                }
                AvatarShoes.HIGH_TOPS -> {
                    fill(HighTopPath, color)
                    clipped(HighTopPath) { fill(SoleBandRect, Paper) }
                    lines(HighTopPath)
                    lines(SoleSeamPath)
                    lines(HighTopDetailsPath, detail, EYE_LINE)
                }
                AvatarShoes.BOOTS -> {
                    val sole = lerp(color, Ink, 0.35f)
                    inked(BootHeelPath, sole)
                    inked(BootUpperPath, color)
                    inked(BootSolePath, sole)
                    lines(BootCuffPath, detail)
                }
                AvatarShoes.LOAFERS -> {
                    inked(LoaferPath, color)
                    lines(LoaferDetailsPath, detail, EYE_LINE)
                }
            }
        }
    }
}

// ---- Hair (always Ink) ---------------------------------------------------------------

/** Hair hanging behind the face: bob sides, ponytail tail, long hair. */
private fun ArtRecorder.drawBackHair(hair: AvatarHair) {
    when (hair) {
        AvatarHair.BOB -> inked(BobBackPath, Ink)
        AvatarHair.PONYTAIL -> inked(PonytailTailPath, Ink)
        AvatarHair.LONG_WAVY -> inked(LongBackPath, Ink)
        AvatarHair.CURLY, AvatarHair.SHORT, AvatarHair.BUN -> Unit
    }
}

/** Top and fringe, over the face. Fringes stop above y ~45.5 so glasses stay clear. */
private fun ArtRecorder.drawFrontHair(hair: AvatarHair) {
    when (hair) {
        AvatarHair.CURLY -> inked(CurlyPath, Ink)
        AvatarHair.BOB -> inked(BobFrontPath, Ink)
        AvatarHair.PONYTAIL -> {
            inked(PonytailFrontPath, Ink)
            inkedCircle(Offset(65.5f, 36f), 2.3f, HairTieCoral)
        }
        AvatarHair.LONG_WAVY -> inked(LongFrontPath, Ink)
        AvatarHair.SHORT -> inked(ShortPath, Ink)
        AvatarHair.BUN -> {
            inked(BunCapPath, Ink)
            inkedCircle(BUN_CENTER, BUN_R, Ink)
        }
    }
}

/** A pile of curls: small bumps all over the top, curls hanging onto the forehead. */
private val CurlyPath: Path by lazy {
    val top = arcPoints(HEAD_CX, 33.5f, 17.5f, 14f, 155f, 385f, 9)
    val aboveEar = Offset(65.5f, 46.5f)
    path {
        moveTo(top[0].x, top[0].y)
        bumpsThrough(top, 3.2f)
        bumpsThrough(listOf(top.last(), aboveEar), 2.4f)
        bumpsThrough(
            listOf(
                aboveEar, Offset(61.5f, 41.5f), Offset(56.5f, 40f), Offset(51f, 41f),
                Offset(45.5f, 40.5f), Offset(40f, 42f), Offset(35f, 40.5f)
            ),
            2.6f
        )
        close()
    }
}

/** Neat crop; the fringe sweeps from the parting down to the front of the face. */
private val ShortPath: Path by lazy {
    path {
        moveTo(35.5f, 43f)
        cubicTo(35f, 30.5f, 41f, 24.5f, 50f, 24.5f)
        cubicTo(59f, 24.5f, 66f, 30f, 66f, 40f)
        cubicTo(66f, 44f, 65.5f, 46.5f, 64.5f, 48f)
        lineTo(61.5f, 44.5f)
        cubicTo(60f, 39.5f, 57f, 37f, 52.5f, 37f)
        cubicTo(46f, 37f, 41f, 39.5f, 35.5f, 43f)
        close()
    }
}

/**
 * Chin-length sides with rounded, blunt bottoms, kept clear of the neck. The front bottom
 * edge runs flat into the chin instead of slanting up to it.
 */
private val BobBackPath: Path by lazy {
    path {
        moveTo(43f, 60f)
        lineTo(40.5f, 64.3f)
        lineTo(34.5f, 64.3f)
        quadraticTo(31f, 64.3f, 31f, 60.8f)
        lineTo(31f, 40f)
        cubicTo(31f, 27f, 39f, 21.5f, 50f, 21.5f)
        cubicTo(61f, 21.5f, 69f, 27f, 69f, 40f)
        lineTo(69f, 61f)
        quadraticTo(69f, 64.5f, 65.5f, 64.5f)
        lineTo(61.5f, 64.5f)
        quadraticTo(58.5f, 62.5f, 58.5f, 58f)
        lineTo(52f, 50f)
        close()
    }
}

/**
 * Rounded top with a blunt fringe swept to the front. The fringe ends running straight
 * down the face's front edge, so hair and face outline meet without a step.
 */
private val BobFrontPath: Path by lazy {
    path {
        moveTo(31f, 47f)
        lineTo(31f, 40f)
        cubicTo(31f, 27f, 39f, 21.5f, 50f, 21.5f)
        cubicTo(61f, 21.5f, 69f, 27f, 69f, 40f)
        lineTo(69f, 47f)
        quadraticTo(66.5f, 45f, 63f, 44.5f)
        cubicTo(59.5f, 37.5f, 53f, 36f, 47.5f, 38f)
        cubicTo(42f, 39.5f, 37f, 42f, FACE_LEFT + 0.2f, 47f)
        close()
    }
}

/** Pulled back from the forehead; the tie sits at the back of the head. */
private val PonytailFrontPath: Path by lazy {
    path {
        moveTo(36f, 42f)
        cubicTo(35.5f, 31f, 41f, 25f, 50f, 25f)
        cubicTo(59f, 25f, 65.5f, 30f, 65.5f, 40f)
        cubicTo(65.5f, 44f, 65f, 46.5f, 64f, 48f)
        quadraticTo(61.5f, 44f, 60f, 41f)
        cubicTo(56.5f, 35.5f, 47f, 34f, 41.5f, 36.5f)
        quadraticTo(37.5f, 38.5f, 36f, 42f)
        close()
    }
}

/** The tail, hanging down to the right shoulder. */
private val PonytailTailPath: Path by lazy {
    path {
        moveTo(61f, 33f)
        cubicTo(70f, 29f, 76.5f, 36f, 75f, 47f)
        cubicTo(74f, 56f, 76.5f, 66f, 71.5f, 75.5f)
        cubicTo(69.5f, 68f, 67.5f, 58f, 66.5f, 50f)
        lineTo(58f, 44f)
        close()
    }
}

/**
 * A short wavy lock curling under at the front, and one long wavy piece falling behind
 * the right shoulder with a curled end. Both kept clear of the neck.
 */
private val LongBackPath: Path by lazy {
    path {
        moveTo(41f, 60f)
        cubicTo(38f, 64f, 34f, 66f, 31.5f, 64f)
        cubicTo(28.5f, 61.5f, 31f, 57f, 29.5f, 52f)
        cubicTo(28f, 47f, 31f, 44f, 30f, 40f)
        cubicTo(30.5f, 27f, 39f, 21f, 50f, 21f)
        cubicTo(61f, 21f, 70f, 27f, 70f, 40f)
        cubicTo(70.5f, 47f, 68f, 52f, 70.5f, 58f)
        cubicTo(73f, 64f, 70f, 69f, 73f, 75f)
        cubicTo(75.5f, 80f, 73f, 86f, 76f, 90f)
        quadraticTo(72f, 93.5f, 67.5f, 90.5f)
        quadraticTo(66f, 88f, 68.5f, 86.5f)
        cubicTo(65f, 80f, 63.5f, 72f, 59f, 62f)
        lineTo(54f, 50f)
        close()
    }
}

/** Top with a wavy fringe; its last wave runs straight down the face's front edge. */
private val LongFrontPath: Path by lazy {
    path {
        moveTo(30f, 48f)
        lineTo(30f, 40f)
        cubicTo(30.5f, 27f, 39f, 21f, 50f, 21f)
        cubicTo(61f, 21f, 70f, 27f, 70f, 40f)
        lineTo(69.5f, 47f)
        quadraticTo(66.5f, 44.5f, 63f, 44.5f)
        bumpsThrough(
            listOf(Offset(63f, 44.5f), Offset(57f, 40.5f), Offset(51f, 42f), Offset(45f, 40f), Offset(39.5f, 43f)),
            2.2f
        )
        cubicTo(37.2f, 43.8f, FACE_LEFT + 0.3f, 45f, FACE_LEFT + 0.2f, 48f)
        close()
    }
}

private val BUN_CENTER = Offset(50.5f, 22f)
private const val BUN_R = 5.2f

/** Smoothed back from the forehead; the bun sits on top (drawn separately). */
private val BunCapPath: Path by lazy {
    path {
        moveTo(36f, 42.5f)
        cubicTo(35.5f, 31.5f, 41.5f, 26f, 50f, 26f)
        cubicTo(58.5f, 26f, 65.5f, 31f, 65.5f, 40f)
        cubicTo(65.5f, 44f, 65f, 46.5f, 64f, 48f)
        quadraticTo(61.5f, 44f, 60f, 41.5f)
        cubicTo(56f, 36f, 46f, 34.5f, 41.5f, 37f)
        quadraticTo(37.5f, 39f, 36f, 42.5f)
        close()
    }
}

// ---- Glasses (centred on the eyes, drawn over the fringe) ----------------------------

private const val ROUND_LENS_R = 3.4f
private const val SQUARE_HALF_W = 2.9f
private const val SQUARE_HALF_H = 2.3f
private const val SUN_HALF_W = 3.3f
private const val SUN_HALF_H = 2.8f

private val RoundLensesPath: Path by lazy {
    path {
        addOval(Rect(Offset(EYE_FRONT_X, EYE_Y), ROUND_LENS_R))
        addOval(Rect(Offset(EYE_BACK_X, EYE_Y), ROUND_LENS_R))
    }
}

private fun lensPair(halfW: Float, halfH: Float, top: Float, bottom: Float): Path = path {
    for (x in floatArrayOf(EYE_FRONT_X, EYE_BACK_X)) {
        addRoundRect(
            RoundRect(
                rect = Rect(x - halfW, EYE_Y - halfH, x + halfW, EYE_Y + halfH),
                topLeft = CornerRadius(top),
                topRight = CornerRadius(top),
                bottomRight = CornerRadius(bottom),
                bottomLeft = CornerRadius(bottom)
            )
        )
    }
}

// Flatter and sharper-cornered than the round lenses so the two read differently in thumbnails.
private val SquareLensesPath: Path by lazy { lensPair(SQUARE_HALF_W, SQUARE_HALF_H, top = 0.7f, bottom = 0.7f) }
// Wider than the gap allows on purpose: the two shades merge into one rounded band.
private val SunglassLensesPath: Path by lazy { lensPair(SUN_HALF_W, SUN_HALF_H, top = 0.8f, bottom = 2.4f) }

/** A Paper glint per shade, so they don't fuse into dark hair beside the face. */
private val SunglassGlintsPath: Path by lazy {
    path {
        for (x in floatArrayOf(EYE_FRONT_X, EYE_BACK_X)) {
            moveTo(x - 1.9f, EYE_Y - 0.3f)
            lineTo(x - 0.6f, EYE_Y - 1.6f)
        }
    }
}

/** Tiny bridge over the point where the lenses meet. */
private val GlassesBridgePath: Path by lazy {
    path {
        val mid = (EYE_FRONT_X + EYE_BACK_X) / 2f
        moveTo(mid - 1.2f, EYE_Y - 2.4f)
        quadraticTo(mid, EYE_Y - 3.6f, mid + 1.2f, EYE_Y - 2.4f)
    }
}

/**
 * One arm from the back lens to the ear: it ends on the line where the ear meets the face
 * (FACE_RIGHT), so it reaches the visible ear without crossing it.
 */
private fun templePath(lensHalfWidth: Float, endInset: Float): Path = path {
    moveTo(EYE_BACK_X + lensHalfWidth - 0.3f, EYE_Y - 0.6f)
    lineTo(FACE_RIGHT - endInset, EAR_CY - 2.3f)
}

private val RoundTemplePath: Path by lazy { templePath(ROUND_LENS_R, endInset = 0f) }
private val SquareTemplePath: Path by lazy { templePath(SQUARE_HALF_W, endInset = 0f) }
// The thicker arm's round cap reaches further, so it stops a little earlier.
private val SunTemplePath: Path by lazy { templePath(SUN_HALF_W, endInset = 0.6f) }

private fun ArtRecorder.drawGlasses(glasses: AvatarGlasses) {
    when (glasses) {
        AvatarGlasses.NONE -> Unit
        AvatarGlasses.ROUND -> {
            lines(RoundTemplePath, widthUnits = GLASSES_WIRE)
            lines(GlassesBridgePath, widthUnits = GLASSES_WIRE)
            lines(RoundLensesPath, widthUnits = GLASSES_WIRE)
        }
        AvatarGlasses.SQUARE -> {
            lines(SquareTemplePath, widthUnits = GLASSES_WIRE)
            lines(GlassesBridgePath, widthUnits = GLASSES_WIRE)
            lines(SquareLensesPath, widthUnits = GLASSES_WIRE)
        }
        AvatarGlasses.SUNGLASSES -> {
            lines(SunTemplePath)
            inked(SunglassLensesPath, Ink)
            lines(SunglassGlintsPath, Paper, widthUnits = GLINT_WIDTH)
        }
    }
}

// ---- Items (local coordinates: origin = the hair's crown, up is negative y) -----------

private fun ArtRecorder.drawItem(item: AvatarItem, crown: Offset) {
    within(Affine.translate(crown.x, crown.y)) {
        when (item) {
            AvatarItem.NONE -> Unit
            AvatarItem.CAT -> drawCat(calico = false)
            AvatarItem.CALICO_CAT -> drawCat(calico = true)
            AvatarItem.DUCK -> drawDuck()
            AvatarItem.FLOWER -> drawFlower()
            AvatarItem.ANTENNAE -> drawAntennae()
            AvatarItem.PARTY_HAT -> drawPartyHat()
        }
    }
}

/**
 * A chubby loaf lying on the hair: one rounded body about 28 wide and 13 tall. Its left
 * end is the (round) head, with no separate neck, and the belly arches over the hair so
 * the cat sits rather than floats.
 */
private val CatBodyPath: Path by lazy {
    path {
        moveTo(-12f, 1f)
        cubicTo(-15.5f, -1f, -15.5f, -10.5f, -10f, -12f)  // round face end, up to the top of the head
        cubicTo(-6.5f, -13f, -3.5f, -11.5f, -1.5f, -10f)  // over the head, easing into the back
        cubicTo(3f, -11f, 9f, -10.5f, 12f, -7f)           // back
        cubicTo(14f, -4.5f, 13.5f, -0.5f, 11f, 1.5f)      // round rump
        quadraticTo(0f, 0.5f, -12f, 1f)                   // belly sunk into the crown, no gap
        close()
    }
}

/** Thick hook curling up from the rump. Starts inside the body so the join is hidden. */
private val CatTailPath: Path by lazy {
    path {
        moveTo(10f, -4f)
        cubicTo(16.5f, -4.5f, 17.5f, -10f, 14f, -12f)
    }
}

/** Front paws: two short curved strokes inside the body, along its bottom-left edge. */
private val CatPawsPath: Path by lazy {
    path {
        moveTo(-11f, 0.4f)
        quadraticTo(-10f, -1.6f, -8.9f, 0.5f)
        moveTo(-8.2f, 0.6f)
        quadraticTo(-7.2f, -1.4f, -6.1f, 0.7f)
    }
}
private const val CAT_PAW_LINE = 1.8f

// Ears on top of the head end; the body covers their bases.
private val CatEarLeftPath: Path by lazy {
    path {
        moveTo(-13f, -9.5f)
        lineTo(-12.4f, -14.4f)
        lineTo(-8.6f, -11.6f)
        close()
    }
}

private val CatEarRightPath: Path by lazy {
    path {
        moveTo(-7.6f, -11.8f)
        lineTo(-4.6f, -14.2f)
        lineTo(-3.6f, -10f)
        close()
    }
}

/**
 * Tail, ears and body share one outline: every Ink stroke goes down first at twice the
 * usual width, then the fills cover their inner halves. Only the outside shows, at the
 * usual weight, so the small ears stay white instead of filling up with outline.
 */
private fun ArtRecorder.drawCat(calico: Boolean) {
    lines(CatTailPath, widthUnits = TAIL_WIDTH + 2f * STROKE)
    lines(CatEarLeftPath, widthUnits = 2f * STROKE)
    lines(CatEarRightPath, widthUnits = 2f * STROKE)
    lines(CatBodyPath, widthUnits = 2f * STROKE)

    lines(CatTailPath, if (calico) CatOrange else CatWhite, widthUnits = TAIL_WIDTH)
    fill(CatEarLeftPath, if (calico) CatOrange else CatWhite)
    fill(CatEarRightPath, CatWhite)
    fill(CatBodyPath, CatWhite)
    if (calico) {
        clipped(CatBodyPath) {
            dot(Offset(8.5f, -6.5f), 5.5f, CatOrange)
            dot(Offset(0.5f, -10.5f), 3.5f, CatGrey)
            dot(Offset(-11.5f, -12f), 3.2f, CatOrange)
        }
    }
    lines(CatPawsPath, widthUnits = CAT_PAW_LINE)
    // Well inside the head end, clear of its outline.
    dot(Offset(-9.5f, -6.8f), 0.95f, Ink)
    dot(Offset(-6.2f, -6.8f), 0.95f, Ink)
}

/** Rubber duck facing left, tail flicked up at the back. */
private val DuckBodyPath: Path by lazy {
    path {
        moveTo(-8.5f, -3f)
        cubicTo(-9f, 0.5f, -5f, 1.5f, 0f, 1.5f)
        cubicTo(6f, 1.5f, 10f, 0f, 10f, -4f)
        lineTo(12f, -9.5f)
        cubicTo(9f, -8.5f, 6f, -7f, 3f, -7f)
        cubicTo(-2f, -7f, -8f, -7f, -8.5f, -3f)
        close()
    }
}

private val DuckBeakPath: Path by lazy {
    path {
        moveTo(-7.4f, -11.2f)
        quadraticTo(-11.8f, -11.2f, -11.6f, -9.4f)
        quadraticTo(-9.8f, -8.2f, -7.2f, -8.8f)
        close()
    }
}

private val DuckWingPath: Path by lazy {
    path {
        moveTo(-0.5f, -3.8f)
        quadraticTo(3f, -0.8f, 6.5f, -4.2f)
    }
}

private fun ArtRecorder.drawDuck() {
    inked(DuckBodyPath, DuckYellow)
    lines(DuckWingPath)
    inkedCircle(Offset(-4f, -10f), 4.2f, DuckYellow)
    inked(DuckBeakPath, BeakOrange)
    dot(Offset(-5f, -11f), 0.85f, Ink)
}

// Stem runs from inside the hair up to the flower's centre (~9 units, ~8 of them visible).
private val FlowerCenter = Offset(0f, -9f)
private const val PETAL_R = 3.5f
private const val PETAL_DISTANCE = 2.9f

private val FlowerStemPath: Path by lazy {
    path {
        moveTo(0f, 1.5f)
        lineTo(0f, FlowerCenter.y)
    }
}

private val FlowerLeavesPath: Path by lazy {
    path {
        moveTo(-0.5f, -2f)
        quadraticTo(-2.5f, -10.5f, -11f, -7.5f)
        quadraticTo(-7f, 0f, -0.5f, -2f)
        close()
        moveTo(0.5f, -3f)
        quadraticTo(3.5f, -11.5f, 11.5f, -9f)
        quadraticTo(7.5f, -1f, 0.5f, -3f)
        close()
    }
}

private val FlowerPetalPaths: List<Path> by lazy {
    (0 until 5).map { i ->
        val a = Math.toRadians(-90.0 + 72.0 * i)
        path {
            addOval(
                Rect(
                    Offset(
                        FlowerCenter.x + PETAL_DISTANCE * cos(a).toFloat(),
                        FlowerCenter.y + PETAL_DISTANCE * sin(a).toFloat()
                    ),
                    PETAL_R
                )
            )
        }
    }
}

/**
 * The five petals merged into one shape, so it gets a single outline at the usual weight.
 * Null if path operations fail on this device; the petals are then drawn one by one.
 */
private val FlowerPetalsUnion: Path? by lazy {
    runCatching {
        FlowerPetalPaths.drop(1).fold(FlowerPetalPaths.first()) { acc, petal ->
            Path.combine(PathOperation.Union, acc, petal)
        }
    }.getOrNull()
}

private fun ArtRecorder.drawFlower() {
    lines(FlowerStemPath)
    inked(FlowerLeavesPath, LeafGreen)
    val union = FlowerPetalsUnion
    if (union != null) {
        inked(union, PetalRed)
    } else {
        FlowerPetalPaths.forEach { inked(it, PetalRed) }
    }
    dot(FlowerCenter, 2.3f, FlowerYellow)
}

private val AntennaStalksPath: Path by lazy {
    path {
        moveTo(-3.5f, 1.5f)
        quadraticTo(-4.5f, -5.5f, -8f, -9.5f)
        moveTo(3.5f, 1.5f)
        quadraticTo(4.5f, -5.5f, 8f, -10f)
    }
}

private fun ArtRecorder.drawAntennae() {
    lines(AntennaStalksPath)
    inkedCircle(Offset(-8.4f, -10f), 2.4f, AntennaPink)
    inkedCircle(Offset(8.4f, -10.5f), 2.4f, AntennaPink)
}

private val HatConePath: Path by lazy {
    path {
        moveTo(-6.5f, 0.5f)
        lineTo(0f, -12f)
        lineTo(6.5f, 0.5f)
        quadraticTo(0f, 2.5f, -6.5f, 0.5f)
        close()
    }
}

private fun ArtRecorder.drawPartyHat() {
    within(Affine.rotate(14f)) {
        fill(HatConePath, HatOrange)
        clipped(HatConePath) {
            dot(Offset(-2f, -3f), 1.2f, HatYellow)
            dot(Offset(2.2f, -6f), 1.2f, HatYellow)
            dot(Offset(2.8f, -1.2f), 1.2f, HatYellow)
            dot(Offset(-0.6f, -8.6f), 1.1f, HatYellow)
        }
        lines(HatConePath)
        inkedCircle(Offset(0f, -12.5f), 2.1f, HatYellow)
    }
}
