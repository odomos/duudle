package com.example.duudle

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class DuudleNavTab { HOME, FRIENDS, VAULT, ME }

private val NavYellow = Color(0xFFFFC72C)
private val NavActivePink = Color(0xFFFF6F91)

@Composable
fun DuudleBottomNav(
    activeTab: DuudleNavTab,
    onHomeClick: () -> Unit,
    onFriendsClick: () -> Unit,
    onDrawClick: () -> Unit,
    onVaultClick: () -> Unit,
    onMeClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                NavIcon(emoji = "🏠", label = "Home", active = activeTab == DuudleNavTab.HOME, onClick = onHomeClick)
                NavIcon(emoji = "👥", label = "Friends", active = activeTab == DuudleNavTab.FRIENDS, onClick = onFriendsClick)
            }
            Spacer(Modifier.width(56.dp))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                NavIcon(emoji = "🗂️", label = "Vault", active = activeTab == DuudleNavTab.VAULT, onClick = onVaultClick)
                BouncingNavIcon(emoji = "🙂", label = "Me", active = activeTab == DuudleNavTab.ME, onClick = onMeClick)
            }
        }

        DrawFab(onClick = onDrawClick, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun NavIcon(emoji: String, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                selected = active
                role = Role.Tab
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Text(emoji, fontSize = 22.sp)
        Text(
            label,
            fontSize = 11.sp,
            color = if (active) NavActivePink else Color.Gray,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun BouncingNavIcon(emoji: String, label: String, active: Boolean = false, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "nav-icon-float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "nav-icon-offset"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                selected = active
                role = Role.Tab
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Text(emoji, fontSize = 22.sp, modifier = Modifier.graphicsLayer { translationY = if (active) 0f else floatOffset * density })
        Text(
            label,
            fontSize = 11.sp,
            color = if (active) NavActivePink else Color.Gray,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun DrawFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, label = "fab-scale")

    Box(
        modifier = modifier
            .offset(y = (-14).dp)
            .scale(scale)
            .size(56.dp)
            .clip(CircleShape)
            .background(NavYellow)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("✏️", fontSize = 24.sp)
    }
}