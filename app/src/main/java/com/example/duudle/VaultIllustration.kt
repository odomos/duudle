package com.example.duudle

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.duudle.ui.theme.DuudleColors
import kotlin.math.PI
import kotlin.math.sin

// Empty-Vault art: two characters peeking over the front of an open cardboard box, with
// a soft sky blob behind and a couple of sparkles. All coordinates are dp inside a
// 240x200 box. Layers, back to front: blob + box back, characters, box front, sparkles.

private val ArtWidth = 240.dp
private val ArtHeight = 200.dp

// Region the characters live in; the box front (drawn on top) hides their lower halves.
private const val SCENE_X = 52f
private const val SCENE_Y = 10f
private const val SCENE_W = 136f
private const val SCENE_H = 140f

private val VaultEmptyCast = listOf(
    FaceCharacter(
        BodyShape.Circle, DuudleColors.Bubblegum, size = 0.5f, x = 0.3f, y = 0.66f,
        eyes = EyeStyle.Googly, mouth = MouthStyle.SmallSmile, faceY = -0.1f, gradient = false,
        tilt = -6f, phase = 0.15f, floatSeconds = 2.8f
    ),
    FaceCharacter(
        BodyShape.Blob, DuudleColors.Sky, size = 0.46f, x = 0.72f, y = 0.7f,
        eyes = EyeStyle.Dot, mouth = MouthStyle.Open, faceY = -0.12f, gradient = false,
        tilt = 5f, phase = 0.6f, floatSeconds = 3.3f
    ),
)

/** Decorative and hidden from TalkBack. [gaze] lets the characters watch the finger. */
@Composable
fun VaultEmptyIllustration(gaze: FaceGaze, modifier: Modifier = Modifier) {
    val clock = rememberFaceClock()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ArtHeight)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(width = ArtWidth, height = ArtHeight)) {
            BoxBack(Modifier.matchParentSize())
            FaceScene(
                characters = VaultEmptyCast,
                modifier = Modifier
                    .offset(x = SCENE_X.dp, y = SCENE_Y.dp)
                    .size(width = SCENE_W.dp, height = SCENE_H.dp),
                gaze = gaze,
                designAspect = SCENE_W / SCENE_H,
                clock = clock
            )
            BoxFront(Modifier.matchParentSize())
            VaultSparkles(clock, Modifier.matchParentSize())
        }
    }
}

/** Soft blob, the box's back wall and its two open flaps. Static. */
@Composable
private fun BoxBack(modifier: Modifier = Modifier) {
    Spacer(
        modifier.drawWithCache {
            val u = 1.dp.toPx()
            val w = size.width
            val h = size.height
            val blob = Path().apply {
                moveTo(0.5f * w, 0.04f * h)
                cubicTo(0.8f * w, 0f, 1f * w, 0.25f * h, 0.96f * w, 0.55f * h)
                cubicTo(0.92f * w, 0.85f * h, 0.7f * w, h, 0.47f * w, 0.98f * h)
                cubicTo(0.2f * w, 0.96f * h, 0f, 0.78f * h, 0.04f * w, 0.48f * h)
                cubicTo(0.08f * w, 0.18f * h, 0.24f * w, 0.08f * h, 0.5f * w, 0.04f * h)
                close()
            }
            val leftFlap = Path().apply {
                moveTo(52 * u, 98 * u)
                lineTo(22 * u, 74 * u)
                lineTo(40 * u, 64 * u)
                lineTo(72 * u, 98 * u)
                close()
            }
            val rightFlap = Path().apply {
                moveTo(188 * u, 98 * u)
                lineTo(218 * u, 74 * u)
                lineTo(200 * u, 64 * u)
                lineTo(168 * u, 98 * u)
                close()
            }
            val outline = Stroke(width = 3 * u, join = StrokeJoin.Round)
            val flap = DuudleColors.Tangerine.flat
            onDrawBehind {
                drawPath(blob, DuudleColors.Sky.light.copy(alpha = 0.6f))
                for (p in listOf(leftFlap, rightFlap)) {
                    drawPath(p, flap)
                    drawPath(p, DuudleColors.Ink, style = outline)
                }
                // Back wall, darker so the box reads as open.
                drawRect(DuudleColors.Tangerine.base, topLeft = Offset(52 * u, 96 * u), size = Size(136 * u, 30 * u))
                drawRect(DuudleColors.Ink, topLeft = Offset(52 * u, 96 * u), size = Size(136 * u, 30 * u), style = outline)
            }
        }
    )
}

/** The box front with a little doodle label on it. Drawn over the characters. Static. */
@Composable
private fun BoxFront(modifier: Modifier = Modifier) {
    Spacer(
        modifier.drawWithCache {
            val u = 1.dp.toPx()
            val outline = Stroke(width = 3 * u, join = StrokeJoin.Round)
            val scribble = Path().apply {
                moveTo(106 * u, 156 * u)
                cubicTo(112 * u, 144 * u, 118 * u, 166 * u, 124 * u, 152 * u)
                cubicTo(128 * u, 144 * u, 132 * u, 160 * u, 136 * u, 150 * u)
            }
            val thin = Stroke(width = 2.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                val frontTopLeft = Offset(44 * u, 120 * u)
                val frontSize = Size(152 * u, 66 * u)
                val corner = CornerRadius(8 * u)
                drawRoundRect(DuudleColors.Tangerine.flat, frontTopLeft, frontSize, corner)
                drawRoundRect(DuudleColors.Ink, frontTopLeft, frontSize, corner, style = outline)
                // Label with a doodle on it.
                val labelTopLeft = Offset(98 * u, 140 * u)
                val labelSize = Size(46 * u, 30 * u)
                drawRoundRect(DuudleColors.Paper, labelTopLeft, labelSize, CornerRadius(5 * u))
                drawRoundRect(DuudleColors.Ink, labelTopLeft, labelSize, CornerRadius(5 * u), style = thin)
                drawPath(scribble, DuudleColors.Coral.base, style = thin)
            }
        }
    )
}

/** Yellow four-point sparkles twinkling on the scene clock, in their own layer. */
@Composable
private fun VaultSparkles(clock: FaceClock, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            // Redrawn every tick: its own layer keeps that from re-recording anything else.
            .graphicsLayer()
            .drawWithCache {
                val specs = listOf(Triple(26f, 40f, 10f), Triple(214f, 150f, 8f), Triple(206f, 30f, 6f))
                val stars = specs.map { (_, _, radius) -> vaultSparklePath(radius.dp.toPx()) }
                val centres = specs.map { (x, y, _) -> Offset(x.dp.toPx(), y.dp.toPx()) }
                val outline = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round)
                onDrawBehind {
                    val t = clock.seconds
                    for (i in stars.indices) {
                        val twinkle = 0.9f + 0.12f * sin(t * 2f * PI.toFloat() / 1.8f + i * 1.3f)
                        withTransform({
                            translate(centres[i].x, centres[i].y)
                            scale(twinkle, twinkle, pivot = Offset.Zero)
                        }) {
                            drawPath(stars[i], DuudleColors.Sunny.base)
                            drawPath(stars[i], DuudleColors.Ink, style = outline)
                        }
                    }
                }
            }
    )
}

private fun vaultSparklePath(r: Float): Path = Path().apply {
    val k = 0.08f * r
    val m = 0.25f * r
    moveTo(0f, -r)
    cubicTo(k, -m, m, -k, r, 0f)
    cubicTo(m, k, k, m, 0f, r)
    cubicTo(-k, m, -m, k, -r, 0f)
    cubicTo(-m, -k, -k, -m, 0f, -r)
    close()
}
