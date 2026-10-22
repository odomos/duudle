package com.example.duudle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duudle.ui.theme.DuudleColors
import com.example.duudle.ui.theme.Pastel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Empty-state art for the Friends tab: two tilted phones with one of our characters in
// each, yellow "Add" tags with burst lines, sparkles and a soft pink blob behind.

private val ScreenCream = Color(0xFFFFF8E6)
private val TagShape = RoundedCornerShape(12.dp)
private val PhoneShape = RoundedCornerShape(22.dp)
private val PhoneScreenShape = RoundedCornerShape(12.dp)
private val SlotShape = RoundedCornerShape(percent = 50)

private val LeftPhoneCast = listOf(
    FaceCharacter(
        BodyShape.Circle, DuudleColors.Sunny, size = 1.05f, x = 0.5f, y = 0.72f,
        eyes = EyeStyle.Googly, mouth = MouthStyle.Smile, glasses = true, gradient = false,
        phase = 0.2f, floatSeconds = 3.0f
    )
)

private val RightPhoneCast = listOf(
    FaceCharacter(
        BodyShape.Circle, DuudleColors.Mint, size = 1.05f, x = 0.5f, y = 0.72f,
        eyes = EyeStyle.Wink, mouth = MouthStyle.SmallSmile, hair = true, gradient = false,
        phase = 0.7f, floatSeconds = 2.6f
    )
)

/** Decorative and hidden from TalkBack. [gaze] lets both characters watch the finger. */
@Composable
fun FriendsEmptyIllustration(gaze: FaceGaze, modifier: Modifier = Modifier) {
    // Only the right phone's scene ticks this clock (two scenes on one clock would run it
    // at double speed); the phones' sway and the sparkles' twinkle read it too, so all of
    // it stands still when animations are off.
    val clock = rememberFaceClock()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(width = 300.dp, height = 250.dp)) {
            SoftBlob(Modifier.matchParentSize())
            Phone(
                body = DuudleColors.Sky,
                cast = LeftPhoneCast,
                gaze = gaze,
                sceneClock = null,
                swayClock = clock,
                tilt = -12f,
                swayPhase = 0f,
                modifier = Modifier.offset(x = 40.dp, y = 20.dp)
            )
            Phone(
                body = DuudleColors.Coral,
                cast = RightPhoneCast,
                gaze = gaze,
                sceneClock = clock,
                swayClock = clock,
                tilt = 10f,
                swayPhase = 1.7f,
                modifier = Modifier.offset(x = 158.dp, y = 54.dp)
            )
            AddTag(raysBelow = true, tilt = -10f, modifier = Modifier.offset(x = 66.dp, y = 192.dp))
            AddTag(raysBelow = false, tilt = -8f, modifier = Modifier.offset(x = 150.dp, y = 20.dp))
            Sparkles(clock, Modifier.matchParentSize())
        }
    }
}

@Composable
private fun SoftBlob(modifier: Modifier = Modifier) {
    Spacer(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val blob = Path().apply {
                moveTo(0.5f * w, 0.06f * h)
                cubicTo(0.78f * w, 0f, 0.98f * w, 0.2f * h, 0.95f * w, 0.5f * h)
                cubicTo(0.93f * w, 0.82f * h, 0.72f * w, h, 0.48f * w, 0.97f * h)
                cubicTo(0.22f * w, 0.95f * h, 0.02f * w, 0.8f * h, 0.05f * w, 0.48f * h)
                cubicTo(0.08f * w, 0.18f * h, 0.25f * w, 0.1f * h, 0.5f * w, 0.06f * h)
                close()
            }
            onDrawBehind { drawPath(blob, DuudleColors.Bubblegum.light.copy(alpha = 0.6f)) }
        }
    )
}

/** A rounded phone outline with a cream screen showing one character, swaying gently. */
@Composable
private fun Phone(
    body: Pastel,
    cast: List<FaceCharacter>,
    gaze: FaceGaze,
    sceneClock: FaceClock?,
    swayClock: FaceClock,
    tilt: Float,
    swayPhase: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 104.dp, height = 176.dp)
            .graphicsLayer {
                rotationZ = tilt + sin(swayClock.seconds * 2f * PI.toFloat() / 4.2f + swayPhase) * 1.5f
            }
            .clip(PhoneShape)
            .background(body.flat)
            .border(3.dp, DuudleColors.Ink, PhoneShape)
    ) {
        // Speaker slot.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .size(width = 26.dp, height = 4.dp)
                .clip(SlotShape)
                .background(DuudleColors.Ink)
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 28.dp)
                .clip(PhoneScreenShape)
                .background(ScreenCream)
                .border(2.dp, DuudleColors.Ink, PhoneScreenShape)
        ) {
            FaceScene(
                characters = cast,
                modifier = Modifier.fillMaxSize(),
                gaze = gaze,
                designAspect = 0.66f,
                clock = sceneClock
            )
        }
        // Home button.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 9.dp)
                .size(width = 30.dp, height = 10.dp)
                .clip(SlotShape)
                .background(DuudleColors.Paper)
                .border(2.dp, DuudleColors.Ink, SlotShape)
        )
    }
}

/** Yellow "Add +" tag with short burst lines fanning out below or above it. */
@Composable
private fun AddTag(raysBelow: Boolean, tilt: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .graphicsLayer { rotationZ = tilt }
            .drawBehind {
                val stroke = 2.5.dp.toPx()
                val gap = 6.dp.toPx()
                val length = 9.dp.toPx()
                val cx = size.width / 2f
                val cy = size.height / 2f
                for (k in 0 until 5) {
                    val angle = ((if (raysBelow) 30f else 210f) + k * 30f) * PI.toFloat() / 180f
                    val ux = cos(angle)
                    val uy = sin(angle)
                    val start = Offset(cx + ux * (cx + gap), cy + uy * (cy + gap))
                    drawLine(
                        DuudleColors.Ink,
                        start,
                        Offset(start.x + ux * length, start.y + uy * length),
                        stroke,
                        StrokeCap.Round
                    )
                }
            }
            .clip(TagShape)
            .background(DuudleColors.Sunny.base)
            .border(2.5.dp, DuudleColors.Ink, TagShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Add", color = DuudleColors.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Spacer(Modifier.width(3.dp))
        Icon(Icons.Filled.Add, contentDescription = null, tint = DuudleColors.Ink, modifier = Modifier.size(15.dp))
    }
}

/** Four-point yellow sparkles that twinkle on the scene clock. */
@Composable
private fun Sparkles(clock: FaceClock, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            // Redrawn every tick: its own layer keeps that from re-recording anything else.
            .graphicsLayer()
            .drawWithCache {
                // (x dp, y dp, radius dp) inside the 300x250 art box.
                val specs = listOf(
                    Triple(36f, 24f, 13f),
                    Triple(266f, 224f, 11f),
                    Triple(264f, 44f, 7f),
                    Triple(22f, 156f, 6f)
                )
                val stars = specs.map { (_, _, radius) -> sparklePath(radius.dp.toPx()) }
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

/** A concave four-point star of the given radius, centred on the origin. */
private fun sparklePath(r: Float): Path = Path().apply {
    val k = 0.08f * r
    val m = 0.25f * r
    moveTo(0f, -r)
    cubicTo(k, -m, m, -k, r, 0f)
    cubicTo(m, k, k, m, 0f, r)
    cubicTo(-k, m, -m, k, -r, 0f)
    cubicTo(-m, -k, -k, -m, 0f, -r)
    close()
}
