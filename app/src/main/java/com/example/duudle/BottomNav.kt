package com.example.duudle

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duudle.ui.theme.DuudleColors

enum class DuudleNavTab { HOME, FRIENDS, VAULT, ME }

private val BarShape = RoundedCornerShape(percent = 50)

@Composable
fun DuudleBottomNav(
    activeTab: DuudleNavTab,
    onHomeClick: () -> Unit,
    onFriendsClick: () -> Unit,
    onDrawClick: () -> Unit,
    onVaultClick: () -> Unit,
    onMeClick: () -> Unit
) {
    // Transparent container: the ink bar floats over the screen's own background.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(64.dp)
                .shadow(elevation = 10.dp, shape = BarShape)
                .background(DuudleColors.Ink)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTab(Icons.Filled.Home, "Home", activeTab == DuudleNavTab.HOME, onHomeClick, Modifier.weight(1f))
            NavTab(Icons.Filled.Face, "Friends", activeTab == DuudleNavTab.FRIENDS, onFriendsClick, Modifier.weight(1f))
            Spacer(Modifier.width(72.dp))
            NavTab(Icons.Filled.Star, "Vault", activeTab == DuudleNavTab.VAULT, onVaultClick, Modifier.weight(1f))
            NavTab(Icons.Filled.Person, "Me", activeTab == DuudleNavTab.ME, onMeClick, Modifier.weight(1f))
        }

        DrawButton(onClick = onDrawClick, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun NavTab(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (active) DuudleColors.Ink else DuudleColors.Paper.copy(alpha = 0.6f)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                selected = active
                role = Role.Tab
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (active) DuudleColors.Sunny.base else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The label is read out, so the icon itself stays silent.
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
            Text(
                label,
                fontSize = 11.sp,
                color = contentColor,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun DrawButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, label = "draw-press")

    // An ink ring around the yellow button ties it to the bar it pokes out of.
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(64.dp)
            .clip(CircleShape)
            .background(DuudleColors.Ink)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(4.dp)
            .clip(CircleShape)
            .background(DuudleColors.Sunny.base),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Edit,
            contentDescription = "Draw a doodle",
            tint = DuudleColors.Ink,
            modifier = Modifier.size(26.dp)
        )
    }
}
