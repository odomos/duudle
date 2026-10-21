package com.example.duudle

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LavenderBackground = Color(0xFFD9C9F0)
private val YellowAccent = Color(0xFFFFC72C)
private val PinkAlert = Color(0xFFFF6F91)
private val GreenOnline = Color(0xFF3DBE64)

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

    Box(modifier = modifier.fillMaxSize().background(LavenderBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(20.dp))
                HomeHeader(overlayGranted = overlayGranted, onOverlayPillClick = onOverlayDebugClick)

                Spacer(Modifier.height(24.dp))
                Mascot(
                    hasIncoming = newDoodleFromUsername != null,
                    senderName = newDoodleFromUsername,
                    onTap = { if (newDoodleFromUsername != null) onViewDoodle() else onDesignDoodleClick() }
                )

                if (newDoodleFromUsername != null) {
                    Spacer(Modifier.height(16.dp))
                    BounceSurface(
                        onClick = onViewDoodle,
                        containerColor = PinkAlert,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "📬 New doodle from $newDoodleFromUsername!",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                DesignDoodleCard(onClick = onDesignDoodleClick)

                Spacer(Modifier.height(16.dp))
                FriendsCard(onClick = onFriendsClick)

                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallPill(text = "ℹ️ About", onClick = { showAbout = true })
                    SmallPill(text = "📖 How to Use", onClick = { showHowTo = true })
                }

                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onOverlayDebugClick) {
                    Text("🔧 Overlay Debug Tools", color = Color.DarkGray, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
            }

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
                    Text("1. Tap \"Design Your Doodle\" and draw something.")
                    Spacer(Modifier.height(6.dp))
                    Text("2. Tap Send and pick a friend.")
                    Spacer(Modifier.height(6.dp))
                    Text("3. It pops up on their screen — they can Snooze it, Remove it, or draw a reply.")
                    Spacer(Modifier.height(6.dp))
                    Text("Make sure Overlay permission and unrestricted battery are both on (Overlay Debug Tools) so doodles show up even when Duudle is closed.")
                }
            },
            confirmButton = { TextButton(onClick = { showHowTo = false }) { Text("Got it") } }
        )
    }
}

@Composable
private fun HomeHeader(overlayGranted: Boolean, onOverlayPillClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) { Text("🎨", fontSize = 20.sp) }
            Spacer(Modifier.width(10.dp))
            Text("Duudle", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .clickable(enabled = !overlayGranted, onClick = onOverlayPillClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (overlayGranted) GreenOnline else PinkAlert)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (overlayGranted) "Overlay ON" else "Overlay OFF",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Mascot(hasIncoming: Boolean, senderName: String?, onTap: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "mascot-float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascot-offset"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .graphicsLayer { translationY = floatOffset * density }
                .size(130.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { onTap() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = if (hasIncoming) "🤩" else "🙂", fontSize = 52.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (hasIncoming) "$senderName sent you something!" else "All caught up — doodle someone 🎨",
            fontSize = 13.sp,
            color = Color.DarkGray
        )
    }
}

@Composable
private fun DesignDoodleCard(onClick: () -> Unit) {
    BounceSurface(onClick = onClick, containerColor = YellowAccent, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Design Your Doodle", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Draw something and send it instantly", fontSize = 13.sp, color = Color(0xFF5B4B00))
            }
            Spacer(Modifier.width(12.dp))
            Text("→", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun FriendsCard(onClick: () -> Unit) {
    BounceSurface(onClick = onClick, containerColor = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("👥 Friends", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Requests and your friend list", fontSize = 13.sp, color = Color.DarkGray)
            }
            Text("→", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun SmallPill(text: String, onClick: () -> Unit) {
    BounceSurface(onClick = onClick, containerColor = YellowAccent) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun BounceSurface(
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "press-scale")

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        content()
    }
}