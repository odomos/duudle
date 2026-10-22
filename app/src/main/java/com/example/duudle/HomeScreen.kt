package com.example.duudle

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.duudle.ui.theme.DuudleColors
import com.example.duudle.ui.theme.DuudleDisplayFont

private val PillShape = RoundedCornerShape(percent = 50)

// Height of DuudleBottomNav, which floats over the bottom of this screen.
private val NavOverlayHeight = 88.dp

// The part of the scene the floating nav bar covers (its 88dp minus the transparent
// strip above the bar, plus a little margin). Faces are laid out above it.
private val NavCoversScene = 78.dp

// Flat pastel shapes with line faces, cropped by the screen edges like the mood reference.
// y is a fraction of the scene height left visible above the nav bar.
private val HomeCast = listOf(
    FaceCharacter(
        BodyShape.Circle, DuudleColors.Mint, size = 0.39f, x = 0.33f, y = 0.24f,
        eyes = EyeStyle.Sleepy, mouth = MouthStyle.Smile, gradient = false, phase = 0.1f, floatSeconds = 3.4f
    ),
    FaceCharacter(
        BodyShape.Swirl, DuudleColors.Lilac, size = 0.36f, x = 0.8f, y = 0.19f,
        eyes = EyeStyle.Dot, mouth = MouthStyle.Open, faceY = -0.05f, gradient = false,
        tilt = -6f, phase = 0.45f, floatSeconds = 2.7f
    ),
    FaceCharacter(
        BodyShape.Circle, DuudleColors.Bubblegum, size = 0.37f, x = 0.06f, y = 0.55f,
        eyes = EyeStyle.Googly, mouth = MouthStyle.SmallSmile, faceX = 0.25f, gradient = false,
        phase = 0.7f, floatSeconds = 3.0f
    ),
    FaceCharacter(
        BodyShape.RoundedSquare, DuudleColors.Tangerine, size = 0.35f, x = 0.9f, y = 0.5f,
        eyes = EyeStyle.Googly, mouth = MouthStyle.SmallSmile, faceX = -0.2f, gradient = false,
        tilt = 12f, phase = 0.25f, floatSeconds = 2.4f
    ),
    FaceCharacter(
        BodyShape.Bolt, DuudleColors.Sunny, size = 0.5f, x = 0.5f, y = 0.66f,
        eyes = EyeStyle.Happy, mouth = MouthStyle.Smile, faceX = 0.05f, faceY = -0.42f, gradient = false,
        tilt = 8f, phase = 0.9f, floatSeconds = 3.6f
    ),
    FaceCharacter(
        BodyShape.Blob, DuudleColors.Sky, size = 0.46f, x = 0.16f, y = 0.9f,
        eyes = EyeStyle.Sleepy, mouth = MouthStyle.Open, faceY = -0.3f, gradient = false,
        phase = 0.55f, floatSeconds = 2.9f
    ),
    FaceCharacter(
        BodyShape.Triangle, DuudleColors.Coral, size = 0.46f, x = 0.84f, y = 0.86f,
        eyes = EyeStyle.Squint, mouth = MouthStyle.SmallSmile, faceY = 0.1f, gradient = false,
        tilt = -8f, phase = 0.3f, floatSeconds = 2.2f
    ),
)

@Composable
fun HomeScreen(

    modifier: Modifier = Modifier,
    onDesignDoodleClick: () -> Unit = {},
    onFriendsClick: () -> Unit = {},
    onOverlayDebugClick: () -> Unit = {},
    onVaultClick: () -> Unit = {},
    onMeClick: () -> Unit = {},
    newDoodleFromUsername: String? = null,
    onViewDoodle: () -> Unit = {}
) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    var showHowTo by remember { mutableStateOf(false) }
    val overlayGranted = remember { Settings.canDrawOverlays(context) }
    val gaze = rememberFaceGaze()
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DuudleColors.Paper)
            .faceGazeSource(gaze)
    ) {
        // Short screens (or big font sizes) scroll, with a fixed-height scene; otherwise
        // the scene takes whatever is left and runs underneath the floating nav.
        val compact = maxHeight < 640.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (compact) Modifier.verticalScroll(scrollState) else Modifier)
        ) {
            Spacer(Modifier.height(12.dp))
            HomeHeader(overlayGranted = overlayGranted, onOverlayPillClick = onOverlayDebugClick)

            Spacer(Modifier.height(20.dp))
            Text(
                text = if (newDoodleFromUsername != null) {
                    "@$newDoodleFromUsername sent you a doodle!"
                } else {
                    "Who’s getting a doodle today?"
                },
                fontFamily = DuudleDisplayFont,
                fontWeight = FontWeight.Black,
                fontSize = 38.sp,
                lineHeight = 40.sp,
                // Normal tracking and a typographic apostrophe so "Who’s" doesn't read as "Whos".
                letterSpacing = 0.em,
                textAlign = TextAlign.Center,
                color = DuudleColors.Ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .semantics { heading() }
            )

            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (newDoodleFromUsername != null) {
                    ArrowPill(text = "Open it", filled = true, onClick = onViewDoodle)
                    TextButton(
                        onClick = onDesignDoodleClick,
                        colors = ButtonDefaults.textButtonColors(contentColor = DuudleColors.Muted)
                    ) {
                        Text("or draw one", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    ArrowPill(text = "Design your doodle", filled = false, onClick = onDesignDoodleClick)
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuietPill(text = "About", onClick = { showAbout = true })
                    QuietPill(text = "How to use", onClick = { showHowTo = true })
                }
            }

            FaceScene(
                characters = HomeCast,
                modifier = if (compact) {
                    Modifier.fillMaxWidth().height(260.dp)
                } else {
                    Modifier.fillMaxWidth().weight(1f)
                },
                mood = if (newDoodleFromUsername != null) FaceMood.Excited else FaceMood.Calm,
                gaze = gaze,
                designAspect = 0.93f,
                bottomInset = if (compact) 0.dp else NavCoversScene
            )
            if (compact) Spacer(Modifier.height(NavOverlayHeight))
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            DuudleBottomNav(
                activeTab = DuudleNavTab.HOME,
                onHomeClick = { /* already here */ },
                onFriendsClick = onFriendsClick,
                onDrawClick = onDesignDoodleClick,
                onVaultClick = onVaultClick,
                onMeClick = onMeClick
            )
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About Duudle") },
            text = {
                Text("Duudle lets you send a hand-drawn doodle straight to a friend's screen — it pops up right over whatever they're doing, no need to open a chat first. Draw it, send it, and it finds them.")
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Got it") } }
        )
    }

    if (showHowTo) {
        AlertDialog(
            onDismissRequest = { showHowTo = false },
            title = { Text("How to Use") },
            text = {
                Column {
                    Text("1. Tap \"Design your doodle\" and draw something.")
                    Spacer(Modifier.height(6.dp))
                    Text("2. Tap Send and pick a friend.")
                    Spacer(Modifier.height(6.dp))
                    Text("3. It pops up on their screen — they can Snooze it, Remove it, or draw a reply.")
                    Spacer(Modifier.height(6.dp))
                    Text("Make sure Overlay permission and unrestricted battery are both on (Profile → Make sure doodles pop up) so doodles show up even when Duudle is closed.")
                }
            },
            confirmButton = { TextButton(onClick = { showHowTo = false }) { Text("Got it") } }
        )
    }
}

@Composable
private fun HomeHeader(overlayGranted: Boolean, onOverlayPillClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Duudle",
            fontFamily = DuudleDisplayFont,
            fontWeight = FontWeight.Black,
            fontSize = 28.sp,
            letterSpacing = (-0.04).em,
            color = DuudleColors.Ink,
            modifier = Modifier.semantics { heading() }
        )

        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(PillShape)
                .background(DuudleColors.PillGrey)
                .clickable(enabled = !overlayGranted, onClick = onOverlayPillClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (overlayGranted) DuudleColors.Mint.base else DuudleColors.Coral.base)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (overlayGranted) "Overlay on" else "Overlay off",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DuudleColors.Ink
            )
        }
    }
}

/** Reference-A call to action: a pill with a round arrow button at its end. */
@Composable
private fun ArrowPill(text: String, filled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "cta-press")

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(PillShape)
            .background(if (filled) DuudleColors.Ink else DuudleColors.PillGrey)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 24.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            color = if (filled) DuudleColors.Paper else DuudleColors.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (filled) DuudleColors.Paper else DuudleColors.Ink),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (filled) DuudleColors.Ink else DuudleColors.Paper,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun QuietPill(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = DuudleColors.Ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(PillShape)
            .border(1.dp, DuudleColors.Hairline, PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}
