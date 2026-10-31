package com.example.duudle

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.duudle.ui.theme.DuudleColors
import com.example.duudle.ui.theme.DuudleDisplayFont
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

private val PillShape = RoundedCornerShape(percent = 50)

private val WordmarkStyle = TextStyle(
    fontFamily = DuudleDisplayFont,
    fontWeight = FontWeight.Black,
    letterSpacing = (-0.05).em,
    color = DuudleColors.Ink
)

// Soft gradient characters, laid out like the sign-in reference: big ones cropped by
// the side edges, a small pink one up top and a tiny one with glasses at the bottom.
private val WelcomeCast = listOf(
    FaceCharacter(
        BodyShape.Circle, DuudleColors.Mint, size = 0.5f, x = 0.07f, y = 0.33f,
        eyes = EyeStyle.Googly, mouth = MouthStyle.SmallSmile, faceX = 0.32f,
        hair = true, tilt = 4f, phase = 0f, floatSeconds = 3.2f
    ),
    FaceCharacter(
        BodyShape.Circle, DuudleColors.Bubblegum, size = 0.17f, x = 0.45f, y = 0.1f,
        eyes = EyeStyle.Dot, mouth = MouthStyle.None, phase = 0.35f, floatSeconds = 2.4f
    ),
    FaceCharacter(
        BodyShape.Circle, DuudleColors.Coral, size = 0.38f, x = 0.9f, y = 0.28f,
        eyes = EyeStyle.Googly, mouth = MouthStyle.Grin, faceX = -0.25f,
        hair = true, tilt = 8f, phase = 0.6f, floatSeconds = 2.9f
    ),
    FaceCharacter(
        BodyShape.Circle, DuudleColors.Sky, size = 0.46f, x = 0.12f, y = 0.8f,
        eyes = EyeStyle.Googly, mouth = MouthStyle.Smile, faceX = 0.3f, faceY = -0.05f,
        hair = true, tilt = -12f, phase = 0.2f, floatSeconds = 3.6f
    ),
    FaceCharacter(
        BodyShape.Circle, DuudleColors.Sunny, size = 0.44f, x = 0.88f, y = 0.8f,
        eyes = EyeStyle.Wink, mouth = MouthStyle.Smile, faceX = -0.28f,
        hair = true, hairFlipped = true, tilt = 10f, phase = 0.75f, floatSeconds = 2.6f
    ),
    FaceCharacter(
        BodyShape.Circle, DuudleColors.Tangerine, size = 0.15f, x = 0.47f, y = 0.9f,
        eyes = EyeStyle.Googly, mouth = MouthStyle.Open, glasses = true, phase = 0.5f, floatSeconds = 2.2f
    ),
)

@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onGetStarted: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val gaze = rememberFaceGaze()
    val clock = rememberFaceClock()
    val navBarBottom = WindowInsets.navigationBars.getBottom(LocalDensity.current).toFloat()
    val trimmed = username.trim()
    val usernameOk = isValidUsername(trimmed)
    // The age/Terms box gates sign-up; MainActivity records the acceptance once it succeeds.
    val canSubmit = usernameOk && agreed
    val submit = { if (canSubmit) onGetStarted(trimmed) }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Scaffold padding stops us above the navigation bar; carry the cream panel
            // down behind it so the screen doesn't end in a white strip.
            .drawBehind {
                if (navBarBottom > 0f) {
                    drawRect(DuudleColors.Cream, topLeft = Offset(0f, size.height), size = Size(size.width, navBarBottom))
                }
            }
            .background(DuudleColors.Cream)
            // The Scaffold padding already covers the navigation bar, so only add the
            // part of the keyboard that sits above it.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
            .faceGazeSource(gaze)
    ) {
        // Top: wordmark and characters on a pale sky gradient. Shrinks when the keyboard opens.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(DuudleColors.Paper, DuudleColors.SkyTop)))
        ) {
            val sectionHeight = maxHeight
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(16.dp))
                Wordmark(
                    maxHeight = sectionHeight * 0.26f,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clearAndSetSemantics { }
                ) {
                    Squiggle(Modifier.matchParentSize())
                    FaceScene(
                        characters = WelcomeCast,
                        modifier = Modifier.fillMaxSize(),
                        gaze = gaze,
                        designAspect = 1.1f,
                        clock = clock
                    )
                    HelloBubble(clock, Modifier.align(BiasAlignment(horizontalBias = 0.12f, verticalBias = 0.1f)))
                }
            }
        }

        // Bottom panel: the existing username sign-in, as a white pill and a black pill.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 28.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Pick a username to get started",
                color = DuudleColors.Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            UsernamePill(
                value = username,
                // Rules cap names at 20; stop typing there rather than failing on submit.
                onValueChange = { username = it.take(USERNAME_MAX_LENGTH) },
                onDone = {
                    focusManager.clearFocus()
                    if (!isLoading && canSubmit) submit()
                }
            )
            Spacer(Modifier.height(8.dp))
            TermsCheckRow(checked = agreed, onCheckedChange = { agreed = it }, onReadTerms = { showTerms = true })
            Spacer(Modifier.height(8.dp))
            // Typing hint first (once something's typed), then any sign-up error.
            val shownError = if (trimmed.isNotEmpty() && !usernameOk) USERNAME_RULE_HINT else errorMessage
            if (shownError != null) {
                Text(
                    shownError,
                    color = DuudleColors.ErrorRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
            }
            BlackPillButton(
                text = if (isLoading) "Setting up..." else "Get Started",
                enabled = !isLoading && canSubmit,
                dimmed = !isLoading && !canSubmit,
                onClick = submit
            )
        }
        Spacer(Modifier.weight(0.1f))
    }
    if (showTerms) TermsDialog(onDismiss = { showTerms = false })
}

/** "I'm 13 or older and agree to the Terms…": the row toggles; the link opens the Terms. */
@Composable
private fun TermsCheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, onReadTerms: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = DuudleColors.Ink, uncheckedColor = DuudleColors.Muted)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "I'm $MINIMUM_AGE or older and agree to the Terms and Community Guidelines",
                fontSize = 13.sp,
                color = DuudleColors.Ink
            )
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)) {
                    append("Read the Terms and Community Guidelines")
                }
            },
            fontSize = 13.sp,
            color = DuudleColors.Ink,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onReadTerms)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = 8.dp)
        )
    }
}

/** "Duudle", sized to fill the width like the reference wordmark (capped by [maxHeight]). */
@Composable
private fun Wordmark(maxHeight: Dp, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth
        val fitSp = remember(widthPx, density) {
            val probe = measurer.measure("Duudle", WordmarkStyle.copy(fontSize = 100.sp), softWrap = false, maxLines = 1)
            if (probe.size.width > 0) 100f * widthPx * 0.97f / probe.size.width else 72f
        }
        val capSp = with(density) { maxHeight.toSp().value }
        val fontSize = min(fitSp, capSp).coerceAtLeast(36f).sp
        Text(
            "Duudle",
            style = WordmarkStyle.copy(fontSize = fontSize, lineHeight = 1.05.em),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.semantics { heading() }
        )
    }
}

/** Thin loopy line drawn behind the characters, from the green one over to the red one. */
@Composable
private fun Squiggle(modifier: Modifier = Modifier) {
    Spacer(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val line = Path().apply {
                moveTo(0.28f * w, 0.34f * h)
                cubicTo(0.34f * w, 0.24f * h, 0.44f * w, 0.18f * h, 0.52f * w, 0.2f * h)
                cubicTo(0.6f * w, 0.22f * h, 0.6f * w, 0.33f * h, 0.54f * w, 0.32f * h)
                cubicTo(0.48f * w, 0.31f * h, 0.5f * w, 0.2f * h, 0.6f * w, 0.17f * h)
                cubicTo(0.66f * w, 0.15f * h, 0.7f * w, 0.15f * h, 0.78f * w, 0.18f * h)
            }
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind { drawPath(line, DuudleColors.Ink, style = stroke) }
        }
    )
}

/**
 * Black "Hello~" speech bubble, tilted, with a tail and two little excitement dashes.
 * Bobs on the scene's [clock], which stands still when animations are off.
 */
@Composable
private fun HelloBubble(clock: FaceClock, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = -8f
                translationY = sin(clock.seconds * 2f * PI.toFloat() / 3.4f) * 3.dp.toPx()
            }
            .drawWithCache {
                val tail = Path().apply {
                    moveTo(size.width * 0.62f, size.height - 1f)
                    lineTo(size.width * 0.8f, size.height - 1f)
                    lineTo(size.width * 0.78f, size.height + 9.dp.toPx())
                    close()
                }
                val dash = 3.dp.toPx()
                onDrawBehind {
                    drawPath(tail, DuudleColors.Ink)
                    drawLine(
                        DuudleColors.Ink,
                        Offset(size.width * 0.12f, -18.dp.toPx()),
                        Offset(size.width * 0.18f, -7.dp.toPx()),
                        dash,
                        StrokeCap.Round
                    )
                    drawLine(
                        DuudleColors.Ink,
                        Offset(size.width * 0.3f, -21.dp.toPx()),
                        Offset(size.width * 0.31f, -9.dp.toPx()),
                        dash,
                        StrokeCap.Round
                    )
                }
            }
            .background(DuudleColors.Ink, PillShape)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text("Hello~", color = DuudleColors.Paper, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun UsernamePill(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = DuudleColors.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(DuudleColors.Ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(PillShape)
                    .background(DuudleColors.Paper)
                    .border(1.dp, DuudleColors.Hairline, PillShape)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty()) {
                    Text(
                        "e.g. arnav123",
                        color = DuudleColors.Muted.copy(alpha = 0.7f),
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun BlackPillButton(text: String, enabled: Boolean, dimmed: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "get-started-press")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(PillShape)
            .background(if (dimmed) DuudleColors.Ink.copy(alpha = 0.3f) else DuudleColors.Ink)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = DuudleColors.Paper, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}
