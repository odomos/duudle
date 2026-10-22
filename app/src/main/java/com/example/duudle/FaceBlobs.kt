package com.example.duudle

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.duudle.ui.theme.DuudleColors
import com.example.duudle.ui.theme.Pastel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Decorative pastel characters with line faces (Welcome, Home, empty states). They bob,
// blink, watch the finger and squish when tapped. Motion is mostly layer transforms, and
// after a quiet spell the whole scene comes to rest and stops drawing frames.

enum class BodyShape { Circle, RoundedSquare, Bolt, Triangle, Blob, Swirl }

/** [Wink] is googly eyes with the left one closed. */
enum class EyeStyle { Googly, Dot, Wink, Sleepy, Happy, Squint }

enum class MouthStyle { None, Smile, SmallSmile, Open, Grin }

/** [Excited] makes everyone grin and look up (Home, when a doodle is waiting). */
enum class FaceMood { Calm, Excited }

/**
 * One character. [x]/[y] place its centre as fractions of the scene's width/height
 * (the visible height, above any bottomInset);
 * [size] is its diameter as a fraction of the scene's unit length (the width, or less
 * when the scene is shorter than its designAspect allows). [faceX]/[faceY] shift the
 * face off-centre in body radii, so characters cropped by the screen edge keep their
 * face in view.
 */
data class FaceCharacter(
    val shape: BodyShape,
    val color: Pastel,
    val size: Float,
    val x: Float,
    val y: Float,
    val eyes: EyeStyle = EyeStyle.Googly,
    val mouth: MouthStyle = MouthStyle.Smile,
    val faceX: Float = 0f,
    val faceY: Float = 0f,
    val hair: Boolean = false,
    val hairFlipped: Boolean = false,
    val glasses: Boolean = false,
    /** Soft 3D radial gradient (Welcome) or a flat colour (Home). */
    val gradient: Boolean = true,
    /** Resting rotation in degrees. */
    val tilt: Float = 0f,
    /** 0..1, so characters don't bob in sync. */
    val phase: Float = 0f,
    /** Length of one idle float cycle, 2.2..3.6 s. */
    val floatSeconds: Float = 2.8f,
)

/** False when the user turned on "Remove animations" (animator duration scale 0). */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/** Where the finger is, shared so a whole screen (not just the scene) can steer the eyes. */
@Stable
class FaceGaze {
    internal var windowPosition by mutableStateOf<Offset?>(null)
    internal var source: LayoutCoordinates? = null
}

@Composable
fun rememberFaceGaze(): FaceGaze = remember { FaceGaze() }

/**
 * Watches the finger on this element in the Initial pass without consuming anything,
 * so buttons and text fields underneath still get their taps.
 */
fun Modifier.faceGazeSource(gaze: FaceGaze): Modifier = this
    .onGloballyPositioned { gaze.source = it }
    .pointerInput(gaze) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.pressed }
                val source = gaze.source
                gaze.windowPosition =
                    if (change != null && source != null && source.isAttached) source.localToWindow(change.position) else null
            }
        }
    }

/** The scene's animation time in seconds, so decorations next to a scene can move in step with it. */
@Stable
class FaceClock {
    internal val time = mutableFloatStateOf(0f)
    val seconds: Float get() = time.floatValue
}

@Composable
fun rememberFaceClock(): FaceClock = remember { FaceClock() }

// While nothing is being touched or tapped, tick at ~30 fps: after a frame, wait 26 ms so
// the next vsync is the one at ~33 ms on 60, 90 and 120 Hz screens alike.
private const val IDLE_FRAME_DELAY_MS = 26L

// After this long with nothing happening (no finger, no tap animation, no mood change) the
// characters ease into a still pose over REST_EASE_SECONDS and the scene stops asking for
// frames altogether, until a touch, a tap or a mood change wakes it.
private const val REST_AFTER_SECONDS = 15f
private const val REST_EASE_SECONDS = 0.6f

/**
 * Draws [characters] and animates them. Decorative only: hidden from TalkBack, and taps
 * just make a character squish and grin. [gaze] lets the eyes follow a finger anywhere
 * on the screen (see [faceGazeSource]); without it they follow touches on the scene.
 * [designAspect] is the width/height the layout was designed at: if the scene gets
 * shorter (keyboard, small phone), the characters shrink instead of piling up.
 * [bottomInset] is how much of the scene's bottom is covered (e.g. by a floating nav):
 * positions and sizes are laid out in the visible part, the rest just shows cropped bodies.
 * [clock] exposes the scene's time; without it the scene keeps its own. The clock stands
 * still while the scene rests, so anything driven by it rests too.
 */
@Composable
fun FaceScene(
    characters: List<FaceCharacter>,
    modifier: Modifier = Modifier,
    mood: FaceMood = FaceMood.Calm,
    gaze: FaceGaze? = null,
    designAspect: Float = 1f,
    bottomInset: Dp = 0.dp,
    clock: FaceClock? = null,
) {
    val animate = rememberMotionEnabled()
    val ownGaze = remember { FaceGaze() }
    val activeGaze = gaze ?: ownGaze
    val ownClock = remember { FaceClock() }
    val motion = remember(clock) { SceneMotion(clock ?: ownClock) }
    val states = remember(characters) { List(characters.size) { CharacterState() } }
    val scope = rememberCoroutineScope()
    val excitement = animateFloatAsState(
        targetValue = if (mood == FaceMood.Excited) 1f else 0f,
        animationSpec = if (animate) tween(600) else snap(),
        label = "face-excitement"
    )
    val currentMood = rememberUpdatedState(mood)
    val density = LocalDensity.current
    val layout = remember(designAspect, bottomInset, density) {
        with(density) { SceneLayout(designAspect, bottomInset.toPx(), PLACEMENT_TOP_PAD.toPx()) }
    }

    if (animate) {
        // One clock for the whole scene, paused while the app is in the background.
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle, activeGaze, motion, states, characters, layout) {
            // Full frame rate only while a finger is down or eyes are still easing back,
            // a tap animation runs, or the mood is changing.
            fun busy(): Boolean {
                if (activeGaze.windowPosition != null || motion.presence > 0f) return true
                val ex = excitement.value
                if (ex != 0f && ex != 1f) return true
                for (i in states.indices) {
                    if (states[i].tapped || states[i].squish.isRunning) return true
                }
                return false
            }
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    // Awake. The clock carries on from wherever it stopped, and liveliness
                    // ramps back up from the rest pose, so waking never jumps.
                    var idleSeconds = 0f
                    var last = withFrameNanos { it }
                    while (true) {
                        val isBusy = busy()
                        val rested = withFrameNanos { now ->
                            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                            last = now
                            idleSeconds = if (isBusy) 0f else idleSeconds + dt
                            val settling = idleSeconds > REST_AFTER_SECONDS
                            motion.step(dt, activeGaze, settling)
                            val done = settling && motion.liveliness == 0f
                            motion.updateFaces(characters, states, layout, excitement.value, density, force = done)
                            done
                        }
                        if (rested) break
                        if (!isBusy) delay(IDLE_FRAME_DELAY_MS)
                    }
                    // Resting: no frames and no invalidations until a finger, a tap or a
                    // mood change (re-entering composition starts awake anyway).
                    val ticket = motion.wakeTicket.intValue
                    val restMood = currentMood.value
                    snapshotFlow {
                        activeGaze.windowPosition != null ||
                            motion.wakeTicket.intValue != ticket ||
                            currentMood.value != restMood
                    }.first { it }
                }
            }
        }
    }

    fun onTap(index: Int) {
        motion.wakeTicket.intValue++
        val state = states[index]
        state.tapJob?.cancel()
        state.tapJob = scope.launch {
            state.tapped = true
            if (animate) {
                launch {
                    state.squish.animateTo(0.85f, tween(90, easing = FastOutLinearInEasing))
                    // Low damping overshoots to ~1.05 before settling.
                    state.squish.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 380f))
                }
            }
            delay(1200)
            state.tapped = false
        }
    }

    Box(
        modifier
            .clipToBounds()
            // Own layer, so nothing in here re-records the screen around it.
            .graphicsLayer()
            .clearAndSetSemantics { }
            .onGloballyPositioned { motion.coords = it }
            .onSizeChanged {
                motion.sceneW = it.width.toFloat()
                motion.sceneH = it.height.toFloat()
            }
            .then(if (gaze == null) Modifier.faceGazeSource(ownGaze) else Modifier)
            .pointerInput(characters, states, layout) {
                detectTapGestures { pos ->
                    val t = motion.time.floatValue
                    val live = motion.liveliness
                    val ex = excitement.value
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    // Topmost (last drawn) character wins; same placement and float offset
                    // as the layers draw with.
                    for (i in characters.indices.reversed()) {
                        val c = characters[i]
                        val p = states[i].placementFor(c, w, h, layout)
                        val dx = if (animate) bobX(c, t, live) else 0f
                        val dy = if (animate) bobY(c, t, ex, live) else 0f
                        if (hypot(pos.x - (p.x + dx), pos.y - (p.y + dy)) <= p.r) {
                            onTap(i)
                            break
                        }
                    }
                }
            }
    ) {
        for (i in characters.indices) {
            CharacterLayer(characters[i], states[i], motion, layout, excitement, animate, activeGaze)
        }
    }
}

/**
 * One character, split so an animation frame never re-records its drawing:
 * - outer layer: idle float and tilt, as layer transforms read from the clock;
 * - inner layer: the tap squish, also a layer transform;
 * - body and hair, recorded once per size;
 * - the face, in its own layer, redrawn only when its gaze, blink or expression changes.
 * Every part covers the whole scene, so all of them share the scene's coordinates.
 */
@Composable
private fun CharacterLayer(
    c: FaceCharacter,
    state: CharacterState,
    motion: SceneMotion,
    layout: SceneLayout,
    excitement: State<Float>,
    animate: Boolean,
    gaze: FaceGaze,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@graphicsLayer
                val p = state.placementFor(c, w, h, layout)
                transformOrigin = TransformOrigin(p.x / w, p.y / h)
                if (animate) {
                    val t = motion.time.floatValue
                    val live = motion.liveliness
                    translationX = bobX(c, t, live)
                    translationY = bobY(c, t, excitement.value, live)
                    rotationZ = c.tilt + wobble(c, t, live)
                } else {
                    rotationZ = c.tilt
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val w = size.width
                    val h = size.height
                    if (w <= 0f || h <= 0f) return@graphicsLayer
                    val p = state.placementFor(c, w, h, layout)
                    val squish = state.squish.value
                    // Squash and stretch, anchored low so it reads as a little bounce.
                    scaleX = 1f + (1f - squish) * 0.6f
                    scaleY = squish
                    transformOrigin = TransformOrigin(p.x / w, (p.y + 0.5f * p.r) / h)
                }
                .drawWithCache {
                    val g = buildGeometry(c, state.placementFor(c, size.width, size.height, layout))
                    state.geometry = g
                    // Half a pixel of pupil travel: smaller gaze changes aren't worth a redraw.
                    state.lookQuantum = 0.5f / max(0.06f * g.r, 0.5f)
                    onDrawWithContent {
                        translate(g.x, g.y) {
                            drawBody(c, g)
                            g.hair?.let { drawPath(it, DuudleColors.Ink, style = g.hairStroke) }
                        }
                        drawContent()
                    }
                }
        ) {
            Spacer(
                Modifier
                    .fillMaxSize()
                    // Its own layer: a blink or a glance re-records only the face.
                    .graphicsLayer()
                    .drawBehind {
                        val g = state.geometry ?: return@drawBehind
                        drawCharacterFace(c, g, state, motion, excitement.value, animate, gaze)
                    }
            )
        }
    }
}

// ---- State ---------------------------------------------------------------------------

private class CharacterState {
    val squish = Animatable(1f)
    var tapped by mutableStateOf(false)
    var tapJob: Job? = null

    // Face inputs, written by the scene loop only when they visibly change.
    val lookX = mutableFloatStateOf(0f)
    val lookY = mutableFloatStateOf(0f)
    val openness = mutableFloatStateOf(1f)
    var lookQuantum = 0.02f
    val look = FloatArray(2)
    var geometry: CharacterGeometry? = null

    private var cachedPlacement: Placement? = null
    private var cachedW = -1f
    private var cachedH = -1f
    private var cachedLayout: SceneLayout? = null

    /** [placement] for this scene size, cached so per-frame callers don't allocate. */
    fun placementFor(c: FaceCharacter, w: Float, h: Float, layout: SceneLayout): Placement {
        val cached = cachedPlacement
        if (cached != null && w == cachedW && h == cachedH && layout === cachedLayout) return cached
        val p = placement(c, w, h, layout.designAspect, layout.bottomInsetPx, layout.topPadPx)
        cachedPlacement = p
        cachedW = w
        cachedH = h
        cachedLayout = layout
        return p
    }
}

private class SceneLayout(val designAspect: Float, val bottomInsetPx: Float, val topPadPx: Float)

/**
 * Scene clock, a smoothed finger position and how lively the scene is (1 = awake, 0 = at
 * rest). [time] is snapshot state that the layer transforms read; [wakeTicket] lets a tap
 * wake a resting scene. The rest is plain fields read alongside [time].
 */
private class SceneMotion(clock: FaceClock) {
    val time = clock.time
    val wakeTicket = mutableIntStateOf(0)
    var liveliness = 1f
    var pointerX = 0f
    var pointerY = 0f
    var presence = 0f
    var coords: LayoutCoordinates? = null
    var sceneW = 0f
    var sceneH = 0f

    fun localPointer(gaze: FaceGaze): Offset? {
        val window = gaze.windowPosition ?: return null
        val c = coords?.takeIf { it.isAttached } ?: return null
        return c.windowToLocal(window)
    }

    fun step(dt: Float, gaze: FaceGaze, settling: Boolean) {
        val target = localPointer(gaze)
        if (target != null) {
            if (presence < 0.05f) {
                pointerX = target.x
                pointerY = target.y
            } else {
                val k = 1f - exp(-14f * dt)
                pointerX += (target.x - pointerX) * k
                pointerY += (target.y - pointerY) * k
            }
            presence = min(1f, presence + dt / 0.18f)
        } else {
            presence = max(0f, presence - dt / 0.45f)
        }
        liveliness = if (settling) {
            max(0f, liveliness - dt / REST_EASE_SECONDS)
        } else {
            min(1f, liveliness + dt / REST_EASE_SECONDS)
        }
        time.floatValue += dt
    }

    /** Works out each face's gaze and blink, and stores them only if they visibly changed. */
    fun updateFaces(
        characters: List<FaceCharacter>,
        states: List<CharacterState>,
        layout: SceneLayout,
        excitement: Float,
        density: Density,
        force: Boolean,
    ) {
        val w = sceneW
        val h = sceneH
        if (w <= 0f || h <= 0f) return
        val t = time.floatValue
        for (i in characters.indices) {
            val c = characters[i]
            val st = states[i]
            val p = st.placementFor(c, w, h, layout)
            val dx = density.bobX(c, t, liveliness)
            val dy = density.bobY(c, t, excitement, liveliness)
            val angle = c.tilt + wobble(c, t, liveliness)
            computeLook(c, p.x + dx, p.y + dy, p.r, angle, t, excitement, liveliness, pointerX, pointerY, presence, st.look)
            val lx = st.look[0]
            val ly = st.look[1]
            if (force || abs(lx - st.lookX.floatValue) >= st.lookQuantum || abs(ly - st.lookY.floatValue) >= st.lookQuantum) {
                st.lookX.floatValue = lx
                st.lookY.floatValue = ly
            }
            val open = mix(1f, eyeOpenness(t, i), liveliness)
            if (open != st.openness.floatValue) st.openness.floatValue = open
        }
    }
}

// ---- Geometry (built once per scene size) --------------------------------------------

private class Placement(val x: Float, val y: Float, val r: Float)

// Room kept above the highest point of a character: covers the excited bob (8dp) and a bit.
private val PLACEMENT_TOP_PAD = 10.dp

/**
 * Lays a character out in the visible part of the scene (height minus [bottomInset]),
 * then nudges it down if needed so its top (hair included) never hits the scene's top
 * edge, which is clipped. Sides and bottom are allowed to crop.
 */
private fun placement(
    c: FaceCharacter,
    w: Float,
    h: Float,
    designAspect: Float,
    bottomInset: Float,
    topPad: Float,
): Placement {
    val visibleH = max(h - bottomInset, 1f)
    val unit = min(w, visibleH * designAspect)
    val r = max(c.size * unit / 2f, 1f)
    val reach = if (c.hair) 1.3f else 1.1f
    return Placement(c.x * w, max(c.y * visibleH, reach * r + topPad), r)
}

private class CharacterGeometry(
    val x: Float,
    val y: Float,
    val r: Float,
    val brush: Brush,
    val body: Path?,
    val bodyStroke: Stroke?,
    val hair: Path?,
    val hairStroke: Stroke,
    val faceStroke: Stroke,
)

private fun Density.buildGeometry(c: FaceCharacter, p: Placement): CharacterGeometry {
    val r = p.r
    val brush: Brush = if (c.gradient) {
        // Highlight up-left, saturated down-right: the soft 3D look of the sign-in art.
        Brush.radialGradient(
            colors = listOf(c.color.light, c.color.base),
            center = Offset(-0.35f * r, -0.42f * r),
            radius = 1.5f * r
        )
    } else {
        SolidColor(c.color.flat)
    }
    val body = when (c.shape) {
        BodyShape.Bolt -> polygonPath(r, BOLT_POINTS)
        BodyShape.Triangle -> polygonPath(r, TRIANGLE_POINTS)
        BodyShape.Blob -> blobPath(r)
        BodyShape.Swirl -> swirlPath(r)
        BodyShape.Circle, BodyShape.RoundedSquare -> null
    }
    // A fat round-joined outline in the body colour softens the polygon corners.
    val bodyStroke = when (c.shape) {
        BodyShape.Bolt -> Stroke(width = 0.16f * r, join = StrokeJoin.Round)
        BodyShape.Triangle -> Stroke(width = 0.24f * r, join = StrokeJoin.Round)
        BodyShape.Swirl -> Stroke(width = 0.24f * r, cap = StrokeCap.Round, join = StrokeJoin.Round)
        else -> null
    }
    return CharacterGeometry(
        x = p.x,
        y = p.y,
        r = r,
        brush = brush,
        body = body,
        bodyStroke = bodyStroke,
        hair = if (c.hair) hairPath(r, c.hairFlipped) else null,
        hairStroke = Stroke(
            width = (0.1f * r).coerceIn(3.dp.toPx(), 7.dp.toPx()),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        faceStroke = Stroke(
            width = (0.075f * r).coerceIn(2.5.dp.toPx(), 3.5.dp.toPx()),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
    )
}

// Unit-radius outlines (x, y pairs), centred on the body.
private val BOLT_POINTS = floatArrayOf(
    -0.1f, -1f, 0.62f, -1f, 0.22f, -0.18f, 0.68f, -0.18f, -0.3f, 1f, -0.02f, 0.12f, -0.55f, 0.12f
)
private val TRIANGLE_POINTS = floatArrayOf(0f, -0.9f, 0.95f, 0.78f, -0.95f, 0.78f)
private val BLOB_RADII = floatArrayOf(1f, 0.93f, 0.98f, 0.9f, 1.02f, 0.92f, 0.99f, 0.9f)

private fun polygonPath(r: Float, points: FloatArray): Path = Path().apply {
    moveTo(points[0] * r, points[1] * r)
    for (i in 2 until points.size step 2) lineTo(points[i] * r, points[i + 1] * r)
    close()
}

/** Smooth closed curve through slightly uneven radii (Catmull-Rom as cubics). */
private fun blobPath(r: Float): Path {
    val n = BLOB_RADII.size
    val pts = Array(n) { i ->
        val a = 2f * PI.toFloat() * i / n - PI.toFloat() / 2f
        Offset(cos(a) * BLOB_RADII[i] * r, sin(a) * BLOB_RADII[i] * r)
    }
    return Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 0 until n) {
            val p0 = pts[(i - 1 + n) % n]
            val p1 = pts[i]
            val p2 = pts[(i + 1) % n]
            val p3 = pts[(i + 2) % n]
            cubicTo(
                p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                p2.x, p2.y
            )
        }
        close()
    }
}

/** A spiral curling out from the middle, stroked thick for the lilac swirl. */
private fun swirlPath(r: Float): Path = Path().apply {
    val steps = 72
    val turns = 1.55f
    for (i in 0..steps) {
        val f = i / steps.toFloat()
        val a = -0.6f + f * turns * 2f * PI.toFloat()
        val radius = r * (0.18f + 0.62f * f)
        if (i == 0) moveTo(cos(a) * radius, sin(a) * radius) else lineTo(cos(a) * radius, sin(a) * radius)
    }
}

/** A swoop of hair over the head ending in a little loop, mirrored when [flipped]. */
private fun hairPath(r: Float, flipped: Boolean): Path {
    val s = if (flipped) -r else r
    val loopCentre = Offset(0.72f * s, -1f * r)
    val loopRadius = 0.18f * r
    return Path().apply {
        moveTo(-0.98f * s, -0.3f * r)
        cubicTo(-0.8f * s, -1.05f * r, 0.2f * s, -1.18f * r, 0.6f * s, -0.86f * r)
        arcTo(
            rect = Rect(loopCentre, loopRadius),
            startAngleDegrees = if (flipped) 49f else 131f,
            sweepAngleDegrees = if (flipped) 320f else -320f,
            forceMoveTo = false
        )
        cubicTo(0.56f * s, -0.82f * r, 0.78f * s, -0.68f * r, 1.05f * s, -0.66f * r)
    }
}

// ---- Motion helpers ------------------------------------------------------------------

private fun floatAngle(c: FaceCharacter, t: Float): Float = 2f * PI.toFloat() * (t / c.floatSeconds + c.phase)

// [live] (liveliness) scales every idle motion: 1 = awake, 0 = the still rest pose.
private fun Density.bobY(c: FaceCharacter, t: Float, excitement: Float, live: Float): Float =
    sin(floatAngle(c, t)) * (4f + 4f * excitement).dp.toPx() * live

private fun Density.bobX(c: FaceCharacter, t: Float, live: Float): Float =
    cos(floatAngle(c, t) * 0.5f) * 1.5f.dp.toPx() * live

private fun wobble(c: FaceCharacter, t: Float, live: Float): Float = sin(floatAngle(c, t) * 0.75f + 1.3f) * 3f * live

private const val BLINK_SECONDS = 0.16f

/** 1 = open, 0 = shut. Each character blinks once per 3.2-4.8 s cycle at a random moment. */
private fun eyeOpenness(t: Float, seed: Int): Float {
    val cycle = 3.2f + ((seed * 0.618f) % 1f) * 1.6f
    val k = floor(t / cycle)
    val start = cycle * (0.3f + 0.4f * hash01(seed, k))
    val d = t - k * cycle - start
    if (d < 0f || d > BLINK_SECONDS) return 1f
    return abs(d / BLINK_SECONDS * 2f - 1f)
}

private fun hash01(seed: Int, k: Float): Float {
    val v = sin(seed * 12.9898f + k * 78.233f) * 43758.547f
    return v - floor(v)
}

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// ---- Drawing -------------------------------------------------------------------------

private val Tongue = Color(0xFFFF8E8E)

/**
 * Where the pupils point, in the character's own (rotated) frame, length 0..1, written to
 * [out]. Idle: a slow wander scaled by [live] (centred at rest); excited: up; a finger
 * ([presence] > 0) pulls them towards ([pointerX], [pointerY]). ([faceCx], [faceCy]) is the
 * body centre as currently drawn.
 */
private fun computeLook(
    c: FaceCharacter,
    faceCx: Float,
    faceCy: Float,
    r: Float,
    angle: Float,
    t: Float,
    excitement: Float,
    live: Float,
    pointerX: Float,
    pointerY: Float,
    presence: Float,
    out: FloatArray,
) {
    var lookX = sin(t * 0.55f + c.phase * 6.28f) * 0.4f * live
    var lookY = cos(t * 0.4f + c.phase * 9.1f) * 0.25f * live
    lookX = mix(lookX, 0f, excitement)
    lookY = mix(lookY, -0.9f, excitement)
    if (presence > 0f) {
        val vx = pointerX - (faceCx + c.faceX * r)
        val vy = pointerY - (faceCy + c.faceY * r)
        val dist = hypot(vx, vy)
        if (dist > 0.5f) {
            val reach = min(dist / (0.8f * r), 1f)
            val rad = -angle * PI.toFloat() / 180f
            val px = (vx * cos(rad) - vy * sin(rad)) / dist * reach
            val py = (vx * sin(rad) + vy * cos(rad)) / dist * reach
            lookX = mix(lookX, px, presence)
            lookY = mix(lookY, py, presence)
        }
    }
    out[0] = lookX
    out[1] = lookY
}

/** The face layer: expression from tap/mood, gaze and blink from the scene loop. */
private fun DrawScope.drawCharacterFace(
    c: FaceCharacter,
    g: CharacterGeometry,
    state: CharacterState,
    motion: SceneMotion,
    excitement: Float,
    animate: Boolean,
    gaze: FaceGaze,
) {
    val lookX: Float
    val lookY: Float
    val openness: Float
    if (animate) {
        lookX = state.lookX.floatValue
        lookY = state.lookY.floatValue
        openness = state.openness.floatValue
    } else {
        // No animation: no loop either, so the eyes go straight to the finger from here.
        val pointer = motion.localPointer(gaze)
        computeLook(
            c, g.x, g.y, g.r, c.tilt, 0f, excitement, 0f,
            pointer?.x ?: 0f, pointer?.y ?: 0f, if (pointer != null) 1f else 0f, state.look
        )
        lookX = state.look[0]
        lookY = state.look[1]
        openness = 1f
    }

    val excited = excitement > 0.5f
    val joyful = state.tapped || excited
    val baseEyes = if (c.eyes == EyeStyle.Wink) EyeStyle.Googly else c.eyes
    val closedEyes = baseEyes == EyeStyle.Sleepy || baseEyes == EyeStyle.Happy || baseEyes == EyeStyle.Squint
    val eyes = if (joyful && closedEyes) EyeStyle.Happy else baseEyes
    val wink = !closedEyes && (state.tapped || (c.eyes == EyeStyle.Wink && !excited))
    val mouth = if (joyful) MouthStyle.Grin else c.mouth

    translate(g.x, g.y) {
        drawFace(c, g, eyes, wink, mouth, lookX, lookY, openness)
    }
}

private fun DrawScope.drawBody(c: FaceCharacter, g: CharacterGeometry) {
    val r = g.r
    when (c.shape) {
        BodyShape.Circle -> drawCircle(g.brush, radius = r, center = Offset.Zero)
        BodyShape.RoundedSquare -> drawRoundRect(
            brush = g.brush,
            topLeft = Offset(-0.86f * r, -0.86f * r),
            size = Size(1.72f * r, 1.72f * r),
            cornerRadius = CornerRadius(0.4f * r)
        )
        BodyShape.Bolt, BodyShape.Triangle, BodyShape.Blob -> {
            val body = g.body ?: return
            drawPath(body, g.brush)
            g.bodyStroke?.let { drawPath(body, g.brush, style = it) }
        }
        BodyShape.Swirl -> {
            val body = g.body ?: return
            drawCircle(c.color.light, radius = 0.94f * r, center = Offset.Zero)
            g.bodyStroke?.let { drawPath(body, g.brush, style = it) }
        }
    }
}

private fun DrawScope.drawFace(
    c: FaceCharacter,
    g: CharacterGeometry,
    eyes: EyeStyle,
    wink: Boolean,
    mouth: MouthStyle,
    lookX: Float,
    lookY: Float,
    openness: Float,
) {
    val r = g.r
    val fx = c.faceX * r
    val fy = c.faceY * r
    val eyeY = fy - 0.1f * r
    val spread = 0.3f * r
    val stroke = g.faceStroke

    drawEye(if (wink) EyeStyle.Happy else eyes, Offset(fx - spread, eyeY), r, stroke, lookX, lookY, openness, rightEye = false)
    drawEye(eyes, Offset(fx + spread, eyeY), r, stroke, lookX, lookY, openness, rightEye = true)

    if (c.glasses) {
        val lens = 0.27f * r
        drawCircle(DuudleColors.Ink, radius = lens, center = Offset(fx - spread, eyeY), style = stroke)
        drawCircle(DuudleColors.Ink, radius = lens, center = Offset(fx + spread, eyeY), style = stroke)
        drawLine(
            DuudleColors.Ink,
            Offset(fx - spread + lens, eyeY),
            Offset(fx + spread - lens, eyeY),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }

    drawMouth(mouth, Offset(fx, fy + 0.25f * r), r, stroke)
}

private fun DrawScope.drawEye(
    style: EyeStyle,
    center: Offset,
    r: Float,
    stroke: Stroke,
    lookX: Float,
    lookY: Float,
    openness: Float,
    rightEye: Boolean,
) {
    val ink = DuudleColors.Ink
    when (style) {
        EyeStyle.Googly, EyeStyle.Wink -> {
            val sclera = 0.2f * r
            val pupil = 0.105f * r
            val reach = sclera - pupil - 0.02f * r
            if (openness < 0.2f) {
                drawLine(ink, Offset(center.x - 0.8f * sclera, center.y), Offset(center.x + 0.8f * sclera, center.y), stroke.width, StrokeCap.Round)
                return
            }
            scale(1f, openness, pivot = center) {
                drawCircle(ink.copy(alpha = 0.08f), radius = sclera, center = Offset(center.x, center.y + 0.035f * r))
                drawCircle(Color.White, radius = sclera, center = center)
                drawCircle(ink, radius = pupil, center = Offset(center.x + lookX * reach, center.y + lookY * reach))
            }
        }
        EyeStyle.Dot -> {
            if (openness < 0.2f) {
                drawLine(ink, Offset(center.x - 0.07f * r, center.y), Offset(center.x + 0.07f * r, center.y), stroke.width, StrokeCap.Round)
                return
            }
            val reach = 0.06f * r
            scale(1f, openness, pivot = center) {
                drawOval(
                    ink,
                    topLeft = Offset(center.x - 0.065f * r + lookX * reach, center.y - 0.09f * r + lookY * reach),
                    size = Size(0.13f * r, 0.18f * r)
                )
            }
        }
        // Closed and curving down: dozing.
        EyeStyle.Sleepy -> drawArc(
            ink, 20f, 140f, false,
            topLeft = Offset(center.x - 0.14f * r, center.y - 0.1f * r),
            size = Size(0.28f * r, 0.2f * r),
            style = stroke
        )
        // Closed and curving up: delighted.
        EyeStyle.Happy -> drawArc(
            ink, 200f, 140f, false,
            topLeft = Offset(center.x - 0.14f * r, center.y - 0.06f * r),
            size = Size(0.28f * r, 0.2f * r),
            style = stroke
        )
        // ">" on the left, "<" on the right.
        EyeStyle.Squint -> {
            val d = if (rightEye) -1f else 1f
            val tip = Offset(center.x + 0.08f * r * d, center.y)
            drawLine(ink, Offset(center.x - 0.1f * r * d, center.y - 0.08f * r), tip, stroke.width, StrokeCap.Round)
            drawLine(ink, tip, Offset(center.x - 0.1f * r * d, center.y + 0.08f * r), stroke.width, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawMouth(style: MouthStyle, center: Offset, r: Float, stroke: Stroke) {
    val ink = DuudleColors.Ink
    when (style) {
        MouthStyle.None -> Unit
        MouthStyle.Smile -> drawArc(
            ink, 20f, 140f, false,
            topLeft = Offset(center.x - 0.22f * r, center.y - 0.2f * r),
            size = Size(0.44f * r, 0.3f * r),
            style = stroke
        )
        MouthStyle.SmallSmile -> drawArc(
            ink, 25f, 130f, false,
            topLeft = Offset(center.x - 0.12f * r, center.y - 0.12f * r),
            size = Size(0.24f * r, 0.18f * r),
            style = stroke
        )
        MouthStyle.Open -> drawOval(
            ink,
            topLeft = Offset(center.x - 0.065f * r, center.y - 0.05f * r),
            size = Size(0.13f * r, 0.17f * r),
            style = stroke
        )
        // A filled "D" with a little tongue.
        MouthStyle.Grin -> {
            drawArc(
                ink, 0f, 180f, true,
                topLeft = Offset(center.x - 0.26f * r, center.y - 0.28f * r),
                size = Size(0.52f * r, 0.44f * r)
            )
            drawArc(
                Tongue, 0f, 180f, true,
                topLeft = Offset(center.x - 0.13f * r, center.y - 0.02f * r),
                size = Size(0.26f * r, 0.16f * r)
            )
        }
    }
}
